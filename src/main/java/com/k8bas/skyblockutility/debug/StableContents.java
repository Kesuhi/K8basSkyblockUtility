package com.k8bas.skyblockutility.debug;

/**
 * Decides when an open menu's contents are stable enough to capture: its content has arrived (the
 * server's state id is set, or a slot holds an item) and nothing changed for {@link #QUIET_TICKS}
 * client ticks. Each distinct stable state is reported once, so a page change or content update
 * gives a new capture and an unchanged menu gives none. One instance per open screen.
 */
public final class StableContents {
	static final int QUIET_TICKS = 2;

	private boolean seen;
	private int stateId;
	private long fingerprint;
	private int quietTicks;
	private boolean emittedCurrent;

	/** @param fingerprint a hash of the top-container slots; {@code emptyFingerprint} when all are empty
	 *  @return true exactly on the tick a new stable state should be captured */
	public boolean onTick(int stateId, long fingerprint, long emptyFingerprint) {
		boolean changed = !seen || stateId != this.stateId || fingerprint != this.fingerprint;
		seen = true;
		if (changed) {
			this.stateId = stateId;
			this.fingerprint = fingerprint;
			quietTicks = 0;
			emittedCurrent = false;
			return false;
		}
		boolean contentArrived = stateId != 0 || fingerprint != emptyFingerprint;
		if (!contentArrived || emittedCurrent) {
			return false;
		}
		if (++quietTicks >= QUIET_TICKS) {
			emittedCurrent = true;
			return true;
		}
		return false;
	}

	public void reset() {
		seen = false;
		quietTicks = 0;
		emittedCurrent = false;
	}
}
