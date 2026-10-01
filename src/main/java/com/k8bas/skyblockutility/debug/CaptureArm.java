package com.k8bas.skyblockutility.debug;

import java.util.function.LongSupplier;

/** The armed state of a capture, which disarms itself after {@link #MAX_ARMED_MS} (REQ-GS-12). */
public final class CaptureArm {
	static final long MAX_ARMED_MS = 60L * 60 * 1000;

	private final LongSupplier clockMs;
	private long armedAt = -1;

	public CaptureArm(LongSupplier clockMs) {
		this.clockMs = clockMs;
	}

	public void arm() {
		armedAt = clockMs.getAsLong();
	}

	public void disarm() {
		armedAt = -1;
	}

	public boolean isArmed() {
		return armedAt >= 0 && clockMs.getAsLong() - armedAt < MAX_ARMED_MS;
	}

	/** True once, on the first call after the time limit ran out; the arm is then cleared. */
	public boolean expiredNow() {
		if (armedAt >= 0 && !isArmed()) {
			armedAt = -1;
			return true;
		}
		return false;
	}
}
