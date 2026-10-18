package com.k8bas.skyblockutility.update;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Runs the automatic update check (REQ-UPD-04): off the client thread, only while the toggle is ON,
 * at most one at a time, and only when CheckPolicy allows a request. Even without a request, the
 * cached release list is evaluated, so a known newer version is still reported.
 */
public final class UpdateService {
	private final GitHubReleaseSource source;
	private final UpdateStateStore store;
	private final LongSupplier clock;
	private final BooleanSupplier toggle;
	private final String runningVersion;
	private final String runningMinecraft;
	private final Supplier<UpdateChannel> channel;
	private final Consumer<String> info;
	private final Consumer<String> warn;
	private final UpdateLock lock;
	private final UpdateHooks hooks;
	private final JarStager stager;
	private final StagedInstall installer;
	private final Set<String> loggedOnce = ConcurrentHashMap.newKeySet();
	private final UpdateState state;
	private volatile Consumer<CandidateFinder.Outcome> onOutcome = outcome -> { };

	UpdateService(GitHubReleaseSource source, UpdateStateStore store, LongSupplier clock, BooleanSupplier toggle,
			String runningVersion, String runningMinecraft, Supplier<UpdateChannel> channel, Consumer<String> info, Consumer<String> warn) {
		this(source, store, clock, toggle, runningVersion, runningMinecraft, channel, info, warn, UpdateHooks.DEFAULT);
	}

	/** @param hooks the hash and install checks (T4.2, T4.3) */
	UpdateService(GitHubReleaseSource source, UpdateStateStore store, LongSupplier clock, BooleanSupplier toggle,
			String runningVersion, String runningMinecraft, Supplier<UpdateChannel> channel, Consumer<String> info, Consumer<String> warn,
			UpdateHooks hooks) {
		this(source, store, clock, toggle, runningVersion, runningMinecraft, channel, info, warn, hooks, null, null);
	}

	/**
	 * @param stager    downloads and checks a confirmed jar (T4.2)
	 * @param installer arms it for the helper at exit (T4.3); with either null nothing is downloaded
	 */
	UpdateService(GitHubReleaseSource source, UpdateStateStore store, LongSupplier clock, BooleanSupplier toggle,
			String runningVersion, String runningMinecraft, Supplier<UpdateChannel> channel, Consumer<String> info, Consumer<String> warn,
			UpdateHooks hooks, JarStager stager, StagedInstall installer) {
		this.lock = new UpdateLock();
		this.hooks = hooks;
		this.stager = stager;
		this.installer = installer;
		this.source = source;
		this.store = store;
		this.clock = clock;
		this.toggle = toggle;
		this.runningVersion = runningVersion;
		this.runningMinecraft = runningMinecraft;
		this.channel = channel;
		this.info = info;
		this.warn = warn;
		this.state = store.load();
	}

	/** Called with every evaluation's outcome, on the check's thread. */
	public void setOnOutcome(Consumer<CandidateFinder.Outcome> listener) {
		onOutcome = listener;
	}

	/** Starts an automatic check on a background thread and returns at once. Does nothing while
	 *  the toggle is OFF or a check is already running. */
	public void triggerAutomatic() {
		if (!toggle.getAsBoolean() || !lock.tryAcquire()) {
			return;
		}
		try {
			Thread.ofVirtual().name("k8bas-update-check").start(() -> {
				try {
					checkAutomatic();
				} finally {
					lock.release();
				}
			});
		} catch (RuntimeException | Error notStarted) {
			lock.release();
			throw notStarted;
		}
	}

	/**
	 * A manual check (*Check now*, `/ksu update check`) on the calling thread, which the caller keeps off the client thread:
	 * at most one per 60 s and none inside a rate-limit wait, but not held back by the 6 h interval or another back-off, and
	 * not counted against the automatic cap (REQ-UPD-04, REQ-UPD-09). It works with the toggle OFF.
	 */
	public CheckOutcome checkManual() {
		UpdateLock.Holder holder = lock.acquireOrHolder(UpdateLock.Holder.CHECK);
		if (holder != null) {
			return holder == UpdateLock.Holder.DOWNLOAD ? CheckOutcome.Downloading.INSTANCE : CheckOutcome.Busy.INSTANCE;
		}
		try {
			String etag;
			synchronized (state) {
				long now = clock.getAsLong();
				long cooldown = CheckPolicy.manualAllowedAt(state, now);
				if (cooldown != 0) {
					return new CheckOutcome.Cooldown(cooldown, now);
				}
				if (CheckPolicy.manualRateLimited(state, now)) {
					return new CheckOutcome.RateLimited(state.nextAllowedMs);
				}
				state.lastManualMs = now;
				store.save(state);
				etag = state.etag;
			}
			store.flush();
			GitHubReleaseSource.Result result = source.fetch(etag);
			long nextAllowed;
			synchronized (state) {
				CheckPolicy.apply(state, result, clock.getAsLong());
				store.save(state);
				nextAllowed = state.nextAllowedMs;
			}
			if (result.problem() != null) {
				warn.accept("Update check failed: " + result.problem());
			}
			return switch (result.kind()) {
				case OK, NOT_MODIFIED -> manualOutcome();
				case HTTP_ERROR -> switch (result.status()) {
					case 403, 429 -> new CheckOutcome.RateLimited(nextAllowed);
					case 404 -> CheckOutcome.SourceUnavailable.INSTANCE;
					default -> CheckOutcome.Unreachable.INSTANCE;
				};
				case NO_RESPONSE, MALFORMED, BAD_REDIRECT -> CheckOutcome.Unreachable.INSTANCE;
			};
		} finally {
			lock.release();
		}
	}

	/** The cached list read for a manual check: the candidate with the hooks' verdicts, or why there is none. */
	private CheckOutcome manualOutcome() {
		CandidateFinder.Outcome outcome;
		SemVer skipped;
		java.util.List<Release> releases;
		synchronized (state) {
			outcome = CandidateFinder.find(state.releases, runningVersion, runningMinecraft, channel.get(), this::logOnce);
			skipped = SemVer.Companion.fromTagOrNull(state.skippedVersion);
			releases = java.util.List.copyOf(state.releases);
		}
		String running = runningVersion.contains("+") ? runningVersion.substring(0, runningVersion.indexOf('+')) : runningVersion;
		return switch (outcome) {
			case CandidateFinder.Candidate candidate -> new CheckOutcome.Found(candidate, hooks.getHash().check(candidate.release(), candidate.jar()),
					hooks.getEligibility().check(), skipped != null && skipped.compareTo(candidate.version()) == 0,
					UpdateChannel.isPreRelease(candidate.release(), candidate.version()), runningMinecraft);
			case CandidateFinder.NoJar noJar -> new CheckOutcome.NoBuild(noJar.newest(), runningMinecraft, releasePage(releases, noJar.newest()));
			case CandidateFinder.RunningNotSemVer notSemVer -> new CheckOutcome.NotARelease(running, notSemVer.newest());
			case CandidateFinder.UpToDate upToDate -> new CheckOutcome.UpToDate(running);
			default -> throw new IllegalStateException("unknown outcome " + outcome);
		};
	}

	/** The page of the release tagged {@code version}, or null. */
	private static String releasePage(java.util.List<Release> releases, SemVer version) {
		for (Release release : releases) {
			SemVer tagged = SemVer.Companion.fromTagOrNull(release.tag());
			if (tagged != null && tagged.compareTo(version) == 0) {
				return release.htmlUrl();
			}
		}
		return null;
	}

	/**
	 * Downloads and checks the candidate the player confirmed (REQ-UPD-11, REQ-UPD-13 to REQ-UPD-15) on the calling thread,
	 * which the caller keeps off the client thread. Not one byte of the jar is fetched unless {@code consent} names the
	 * candidate on offer now (a skipped one included) and an install may be offered for it; the hash check may read the
	 * release's sidecar first (REQ-UPD-14). It holds the lock a check holds, so it never runs beside one (REQ-UPD-04).
	 */
	public DownloadOutcome download(Consent consent) {
		UpdateLock.Holder holder = lock.acquireOrHolder(UpdateLock.Holder.DOWNLOAD);
		if (holder != null) {
			return holder == UpdateLock.Holder.DOWNLOAD ? DownloadOutcome.Downloading.INSTANCE : DownloadOutcome.Busy.INSTANCE;
		}
		try {
			CandidateFinder.Outcome outcome;
			synchronized (state) {
				outcome = CandidateFinder.find(state.releases, runningVersion, runningMinecraft, channel.get(), this::logOnce);
			}
			String requested = consent.version() != null && consent.version().startsWith("v") ? consent.version().substring(1) : String.valueOf(consent.version());
			SemVer wanted = SemVer.Companion.fromTagOrNull(consent.version());
			if (!(outcome instanceof CandidateFinder.Candidate candidate) || wanted == null || wanted.compareTo(candidate.version()) != 0) {
				return new DownloadOutcome.Stale(requested, outcome instanceof CandidateFinder.Candidate current ? current.version() : null);
			}
			UpdateHooks.Hash hash = hooks.getHash().check(candidate.release(), candidate.jar());
			String notOffered = UpdateHooks.notOfferedReason(hash, hooks.getEligibility().check());
			if (notOffered != null || stager == null || installer == null) {
				return new DownloadOutcome.NotOffered(notOffered != null ? notOffered : UpdateHooks.NOTIFY_ONLY.reason());
			}
			String sha256 = ((UpdateHooks.Hash.Verifiable) hash).sha256();
			if (consent.sha256() != null && !consent.sha256().equalsIgnoreCase(sha256)) {
				return new DownloadOutcome.Changed(candidate.version());
			}
			DownloadOutcome result = stager.stage(candidate.version().toString(), candidate.jar(), sha256);
			if (result instanceof DownloadOutcome.Staged staged) {
				result = arm(staged, candidate.jar().name());
			}
			switch (result) {
				case DownloadOutcome.Staged staged -> info.accept("Update v" + staged.version() + " checked and staged; it installs when the game exits: "
						+ staged.file().getFileName());
				case DownloadOutcome.Failed failed -> warn.accept("Update download of v" + candidate.version() + " failed: " + failed.reason());
				case DownloadOutcome.Incompatible incompatible -> warn.accept("Update v" + candidate.version() + " is an incompatible build: " + incompatible.reason());
				case DownloadOutcome.ChecksumMismatch mismatch -> warn.accept("Update v" + candidate.version() + " does not match its SHA-256; deleted");
				default -> {
				}
			}
			return result;
		} finally {
			lock.release();
		}
	}

	/** Arms a checked jar for the helper at exit and records it as the pending install, or deletes it if it may not go in. */
	private DownloadOutcome arm(DownloadOutcome.Staged staged, String assetName) {
		StagedInstall.Armed armed = installer.arm(staged.file(), assetName, staged.sha256());
		if (armed.refusal() != null) {
			// The same version confirmed again overwrote the armed file; that one stays armed.
			if (!staged.file().equals(installer.armedFile())) {
				try {
					java.nio.file.Files.deleteIfExists(staged.file());
				} catch (java.io.IOException ignored) {
					// the next launch clears it (T4.3b)
				}
			}
			return new DownloadOutcome.Failed(armed.refusal());
		}
		synchronized (state) {
			state.pendingInstall = new PendingInstall(staged.version(), assetName, staged.sha256(), clock.getAsLong(), runningVersion,
					armed.old().toString());
			store.save(state);
		}
		store.flush();
		return staged;
	}

	/** What {@link #cancelPending()} did. */
	public enum Cancel {
		CANCELLED,
		NOTHING_PENDING,
		/** A download holds the lock; nothing changed. */
		BUSY
	}

	/**
	 * Cancels a pending update (REQ-UPD-12): disarmed, so no helper starts at exit, the staged files deleted and the record
	 * cleared; the running jar is untouched, and it is not a skip. It takes the lock, so it never runs beside a download.
	 */
	public Cancel cancelPending() {
		if (lock.acquireOrHolder(UpdateLock.Holder.DOWNLOAD) != null) {
			return Cancel.BUSY;
		}
		try {
			boolean pending = installer != null && installer.armedFile() != null;
			// Disarmed first, so a hook running meanwhile cannot install what is being cancelled.
			if (installer != null) {
				installer.disarm();
			}
			synchronized (state) {
				pending |= state.pendingInstall != null;
				state.pendingInstall = null;
				store.save(state);
			}
			store.flush();
			if (installer != null) {
				installer.deleteStaged();
			}
			if (pending) {
				info.accept("Pending update cancelled");
			}
			return pending ? Cancel.CANCELLED : Cancel.NOTHING_PENDING;
		} finally {
			lock.release();
		}
	}

	private void logOnce(String line) {
		if (loggedOnce.add(line)) {
			info.accept(line);
		}
	}

	/** The lock a check or a download holds (REQ-UPD-04: one at a time); T4.2's download takes it too. */
	UpdateLock lock() {
		return lock;
	}

	/** One automatic check on the calling thread; true if a request was sent. */
	boolean checkAutomatic() {
		if (!toggle.getAsBoolean()) {
			return false;
		}
		String etag;
		synchronized (state) {
			long now = clock.getAsLong();
			if (!CheckPolicy.automaticDue(state, now)) {
				evaluate();
				return false;
			}
			// Counted before sending, so a crash mid-request still counts against the daily cap.
			CheckPolicy.recordAutomatic(state, now);
			store.save(state);
			etag = state.etag;
		}
		store.flush();
		GitHubReleaseSource.Result result = source.fetch(etag);
		synchronized (state) {
			CheckPolicy.apply(state, result, clock.getAsLong());
			store.save(state);
		}
		if (result.problem() != null) {
			// One line per attempt, without a stack trace (AC-UPD-10).
			warn.accept("Update check failed: " + result.problem());
		}
		evaluate();
		return true;
	}

	private void evaluate() {
		CandidateFinder.Outcome outcome;
		synchronized (state) {
			outcome = CandidateFinder.find(state.releases, runningVersion, runningMinecraft, channel.get(), this::logOnce);
		}
		onOutcome.accept(outcome);
	}

	/** Writes pending state now, e.g. on client shutdown. */
	public void flush() {
		store.flush();
	}

	/** For tests. */
	UpdateState state() {
		return state;
	}
}
