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
		this.lock = new UpdateLock();
		this.hooks = hooks;
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
		if (!lock.tryAcquire()) {
			return CheckOutcome.Busy.INSTANCE;
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
			outcome = CandidateFinder.find(state.releases, runningVersion, runningMinecraft, channel.get(), line -> {
				if (loggedOnce.add(line)) {
					info.accept(line);
				}
			});
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
			outcome = CandidateFinder.find(state.releases, runningVersion, runningMinecraft, channel.get(), line -> {
				if (loggedOnce.add(line)) {
					info.accept(line);
				}
			});
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
