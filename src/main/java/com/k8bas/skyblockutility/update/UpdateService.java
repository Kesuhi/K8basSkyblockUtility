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
	private volatile Consumer<CheckOutcome> onOutcome = outcome -> { };
	private volatile CheckOutcome lastOffer;
	/**
	 * Under the state lock: each offer and each skip takes the next number; an offer that read the state before a newer
	 * one (or a skip) never replaces it, though its hash check may end last.
	 */
	private long offerGeneration;
	private long lastOfferGeneration;
	/**
	 * Copies of the two state fields the render thread reads (the card, the notices), set at every save: they never wait
	 * for the state lock, which the launch check holds while it reads the mods folder.
	 */
	private volatile long lastSuccessCopy;
	private volatile PendingInstall pendingCopy;

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
		this.lastSuccessCopy = state.lastSuccessMs;
		this.pendingCopy = state.pendingInstall;
	}

	/** Called with every evaluation's outcome, on the check's thread. */
	public void setOnOutcome(Consumer<CheckOutcome> listener) {
		onOutcome = listener;
	}

	/**
	 * Starts an automatic check on a background thread and returns at once; true if it started. Does nothing while the
	 * toggle is OFF or a check, download or the launch's reconciliation runs.
	 */
	public boolean triggerAutomatic() {
		if (!toggle.getAsBoolean() || !lock.tryAcquire()) {
			return false;
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
		return true;
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
				save();
				etag = state.etag;
			}
			store.flush();
			GitHubReleaseSource.Result result = source.fetch(etag);
			long nextAllowed;
			synchronized (state) {
				CheckPolicy.apply(state, result, clock.getAsLong());
				save();
				nextAllowed = state.nextAllowedMs;
			}
			if (result.problem() != null) {
				warn.accept("Update check failed: " + result.problem());
			}
			return switch (result.kind()) {
				case OK, NOT_MODIFIED -> offer();
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

	/**
	 * What the cached release list offers now, with the hooks' verdicts, or why nothing is offered: the model the manual
	 * check, the notices, the confirmation screen and the card all read (REQ-UPD-08). No API request; the hash check may
	 * read the release's sidecar (R49), so callers stay off the render thread.
	 */
	public CheckOutcome offer() {
		CandidateFinder.Outcome outcome;
		SemVer skipped;
		java.util.List<Release> releases;
		long generation;
		synchronized (state) {
			outcome = CandidateFinder.find(state.releases, runningVersion, runningMinecraft, channel.get(), this::logOnce);
			skipped = SemVer.Companion.fromTagOrNull(state.skippedVersion);
			releases = java.util.List.copyOf(state.releases);
			generation = ++offerGeneration;
		}
		String running = runningVersion.contains("+") ? runningVersion.substring(0, runningVersion.indexOf('+')) : runningVersion;
		CheckOutcome result = switch (outcome) {
			case CandidateFinder.Candidate candidate -> new CheckOutcome.Found(candidate, hooks.getHash().check(candidate.release(), candidate.jar()),
					hooks.getEligibility().check(), skipped != null && skipped.compareTo(candidate.version()) == 0,
					UpdateChannel.isPreRelease(candidate.release(), candidate.version()), runningMinecraft);
			case CandidateFinder.NoJar noJar -> new CheckOutcome.NoBuild(noJar.newest(), runningMinecraft, releasePage(releases, noJar.newest()));
			case CandidateFinder.RunningNotSemVer notSemVer -> new CheckOutcome.NotARelease(running, notSemVer.newest());
			case CandidateFinder.UpToDate upToDate -> new CheckOutcome.UpToDate(running);
			default -> throw new IllegalStateException("unknown outcome " + outcome);
		};
		synchronized (state) {
			if (generation > lastOfferGeneration) {
				lastOfferGeneration = generation;
				lastOffer = result;
			}
			// Overtaken by a newer offer or a skip, it answers with that one, so no caller shows or toasts a stale view.
			return lastOffer != null ? lastOffer : result;
		}
	}

	/** The last {@link #offer()}, or null before the first: what the settings card reads every frame, at no cost. */
	public CheckOutcome lastOffer() {
		return lastOffer;
	}

	/** The running version as the Loader reports it. */
	public String runningVersion() {
		return runningVersion;
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
			if (notOffered != null) {
				return new DownloadOutcome.NotOffered(notOffered);
			}
			// A file changed since the screen showed its checksum is refused first, whatever else this game allows.
			String sha256 = ((UpdateHooks.Hash.Verifiable) hash).sha256();
			if (consent.sha256() != null && !consent.sha256().equalsIgnoreCase(sha256)) {
				return new DownloadOutcome.Changed(candidate.version());
			}
			if (stager == null || installer == null) {
				return new DownloadOutcome.NotOffered(UpdateHooks.NOTIFY_ONLY.reason());
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
					armed.old().toString(), 0);
			save();
		}
		store.flush();
		return staged;
	}

	/**
	 * Settles the staged update once per launch, locally and without network (REQ-UPD-18): finishes an applied one,
	 * re-arms one the last exit did not apply, disarms one that failed twice, clears orphans and names other copies of the
	 * mod once. Holds the lock, so no check or download runs beside it; it waits for the lock rather than skip a launch.
	 */
	public LaunchReconciler.Report reconcileAtLaunch() {
		if (installer == null || !awaitLock(UpdateLock.Holder.RECONCILE, RECONCILE_WAIT_MS)) {
			return new LaunchReconciler.Report(null, java.util.List.of());
		}
		try {
			LaunchReconciler reconciler = new LaunchReconciler(runningVersion, installer.runningJar(), installer.root(), installer,
					hooks.getEligibility(), info, warn);
			LaunchReconciler.Report report;
			synchronized (state) {
				report = reconciler.reconcile(state);
				save();
			}
			store.flush();
			reconciler.finish();
			if (report.getToast() != null) {
				info.accept(report.getToast());
			}
			return report;
		} finally {
			lock.release();
		}
	}

	/** What the confirmation screen shows for {@code found}: the running version and the channel as this service has them. */
	public Confirmation confirmation(CheckOutcome.Found found) {
		return Confirmation.of(found, runningVersion, channel.get());
	}

	/** The longest the launch's reconciliation waits for a check or download to end. */
	static final long RECONCILE_WAIT_MS = 30_000;

	private boolean awaitLock(UpdateLock.Holder holder, long waitMs) {
		long until = System.nanoTime() + waitMs * 1_000_000;
		while (lock.acquireOrHolder(holder) != null) {
			if (System.nanoTime() >= until) {
				warn.accept("Update: the launch check gave up waiting for a running check or download");
				return false;
			}
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return true;
	}

	/**
	 * Skips {@code version} (REQ-UPD-12): no toast and no chat line for it; a higher version notifies again, and the manual
	 * check and the card still show it, marked. False if it is not a version.
	 */
	public boolean skip(String version) {
		SemVer parsed = SemVer.Companion.fromTagOrNull(version);
		if (parsed == null) {
			return false;
		}
		synchronized (state) {
			state.skippedVersion = parsed.toString();
			save();
			// The card reads the cached offer; it shows the mark at once, not after the next check (AC-UPD-13). An offer still
			// running read the state before the skip, so it may not replace this one.
			if (lastOffer instanceof CheckOutcome.Found found && found.candidate().version().compareTo(parsed) == 0 && !found.skipped()) {
				lastOffer = new CheckOutcome.Found(found.candidate(), found.hash(), found.eligibility(), true, found.beta(), found.minecraft());
			}
			lastOfferGeneration = ++offerGeneration;
		}
		store.flush();
		info.accept("Update v" + parsed + " skipped");
		return true;
	}

	/** When the last check got an answer (epoch ms, 0 for never), as last saved; any thread, never waits (the settings card). */
	public long lastSuccessMs() {
		return lastSuccessCopy;
	}

	/** The pending install (T4.3), or null, as last saved; any thread, never waits (the settings card, the notices). */
	public PendingInstall pendingInstall() {
		return pendingCopy;
	}

	/** What {@link #cancelPending()} did. */
	public enum Cancel {
		CANCELLED,
		NOTHING_PENDING,
		/** A download holds the lock; nothing changed. */
		DOWNLOADING,
		/** A check or the launch check holds the lock; nothing changed. */
		BUSY
	}

	/**
	 * Cancels a pending update (REQ-UPD-12): disarmed, so no helper starts at exit, the staged files deleted and the record
	 * cleared; the running jar is untouched, and it is not a skip. It takes the lock, so it never runs beside a download.
	 */
	public Cancel cancelPending() {
		UpdateLock.Holder holder = lock.acquireOrHolder(UpdateLock.Holder.DOWNLOAD);
		if (holder != null) {
			return holder == UpdateLock.Holder.DOWNLOAD ? Cancel.DOWNLOADING : Cancel.BUSY;
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
				save();
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
		boolean due;
		synchronized (state) {
			long now = clock.getAsLong();
			due = CheckPolicy.automaticDue(state, now);
			if (due) {
				// Counted before sending, so a crash mid-request still counts against the daily cap.
				CheckPolicy.recordAutomatic(state, now);
				save();
			}
			etag = state.etag;
		}
		// Evaluated outside the lock: the hash check may read a sidecar, which must not hold the state up.
		if (!due) {
			evaluate();
			return false;
		}
		store.flush();
		GitHubReleaseSource.Result result = source.fetch(etag);
		synchronized (state) {
			CheckPolicy.apply(state, result, clock.getAsLong());
			save();
		}
		if (result.problem() != null) {
			// One line per attempt, without a stack trace (AC-UPD-10).
			warn.accept("Update check failed: " + result.problem());
		}
		evaluate();
		return true;
	}

	/** The automatic check's result for the notices: the same model a manual check gives (T4.4). */
	private void evaluate() {
		onOutcome.accept(offer());
	}

	/** Writes pending state now, e.g. on client shutdown. */
	public void flush() {
		store.flush();
	}

	/** Saves the state and refreshes the copies the render thread reads; under the state lock. */
	private void save() {
		store.save(state);
		lastSuccessCopy = state.lastSuccessMs;
		pendingCopy = state.pendingInstall;
	}

	/** For gametests: a pending install as a staged download leaves it, through the same save. */
	void pendingForTests(PendingInstall pending) {
		synchronized (state) {
			state.pendingInstall = pending;
			save();
		}
	}

	/** For tests. */
	UpdateState state() {
		return state;
	}
}
