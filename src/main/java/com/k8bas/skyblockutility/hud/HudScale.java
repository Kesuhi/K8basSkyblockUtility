package com.k8bas.skyblockutility.hud;

/** An element's scale (REQ-HUD-05): 0.5-3.0, changed in steps of 0.1, stored rounded to 0.01. */
public final class HudScale {
	public static final double MIN = 0.5;
	public static final double MAX = 3.0;
	public static final double STEP = 0.1;

	private HudScale() {
	}

	/** Held to the range and rounded to hundredths; not a number reads as 1. */
	public static double clamp(double scale) {
		if (!Double.isFinite(scale)) {
			return 1;
		}
		return Math.round(Math.max(MIN, Math.min(MAX, scale)) * 100) / 100.0;
	}

	public static boolean inRange(double scale) {
		return Double.isFinite(scale) && scale >= MIN && scale <= MAX;
	}

	/** {@code notches} steps up (or down, when negative) from {@code scale}, held to the range. */
	public static double step(double scale, int notches) {
		return clamp(scale + notches * STEP);
	}
}
