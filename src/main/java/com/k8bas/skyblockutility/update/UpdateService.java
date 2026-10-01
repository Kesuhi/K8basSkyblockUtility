package com.k8bas.skyblockutility.update;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
	private final AtomicBoolean inFlight = new AtomicBoolean();
	private final Set<String> loggedOnce = ConcurrentHashMap.newKeySet();
	private final UpdateState state;
	private volatile Consumer<CandidateFinder.Outcome> onOutcome = outcome -> { };

	UpdateService(GitHubReleaseSource source, UpdateStateStore store, LongSupplier clock, BooleanSupplier toggle,
			String runningVersion, String runningMinecraft, Supplier<UpdateChannel> channel, Consumer<String> info, Consumer<String> warn) {
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
		if (!toggle.getAsBoolean() || !inFlight.compareAndSet(false, true)) {
			return;
		}
		Thread.ofVirtual().name("k8bas-update-check").start(() -> {
			try {
				checkAutomatic();
			} finally {
				inFlight.set(false);
			}
		});
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
