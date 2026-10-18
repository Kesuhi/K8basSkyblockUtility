package com.k8bas.skyblockutility.update;

import java.util.function.BooleanSupplier;

/**
 * When the automatic update check runs (R50, REQ-UPD-04): once after the client has started and once at the first server
 * join of the session, never periodically while playing. Both still go through the persisted limits, so the join sends
 * nothing after an answer at startup, and is the retry when startup had no network. An attempt counts only when a check
 * really started: a join while the startup check still waits for the network tries again at the next join.
 */
final class SessionChecks {
	private final BooleanSupplier check;
	private boolean started;
	private boolean joined;

	/** @param check starts one automatic check off the render thread; true if it started */
	SessionChecks(BooleanSupplier check) {
		this.check = check;
	}

	/** The client has started. */
	synchronized void started() {
		if (!started) {
			started = check.getAsBoolean();
		}
	}

	/** A server (or singleplayer world) was joined. */
	synchronized void joined() {
		if (!joined) {
			joined = check.getAsBoolean();
		}
	}
}
