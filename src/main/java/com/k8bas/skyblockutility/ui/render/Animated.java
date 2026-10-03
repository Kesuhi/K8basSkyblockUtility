package com.k8bas.skyblockutility.ui.render;

import java.util.function.LongSupplier;

/**
 * A value that eases to its target over a fixed time (REQ-UI-17: ~150 ms), e.g. a toggle's knob or a
 * hover tint. The clock is passed in, so tests drive it; the UI uses {@link UiClock#MILLIS}.
 */
public final class Animated {
	public static final long DEFAULT_DURATION_MS = 150;

	private final long durationMs;
	private final LongSupplier clockMs;
	private float start;
	private float target;
	private long startedAt;

	public Animated(float initial, long durationMs, LongSupplier clockMs) {
		this.durationMs = Math.max(0, durationMs);
		this.clockMs = clockMs;
		this.start = initial;
		this.target = initial;
		this.startedAt = clockMs.getAsLong();
	}

	/** Eases from the current value to {@code newTarget}; the same target again does not restart. */
	public void animateTo(float newTarget) {
		if (newTarget == target) {
			return;
		}
		start = value();
		target = newTarget;
		startedAt = clockMs.getAsLong();
	}

	/** Jumps to a value with no motion. */
	public void snapTo(float value) {
		start = value;
		target = value;
		startedAt = clockMs.getAsLong();
	}

	public float value() {
		if (durationMs == 0) {
			return target;
		}
		float t = (clockMs.getAsLong() - startedAt) / (float) durationMs;
		return start + (target - start) * Easing.outCubic(t);
	}

	public float target() {
		return target;
	}

	public boolean settled() {
		return durationMs == 0 || start == target || clockMs.getAsLong() - startedAt >= durationMs;
	}
}
