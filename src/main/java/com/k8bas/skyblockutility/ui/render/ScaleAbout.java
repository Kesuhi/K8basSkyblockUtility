package com.k8bas.skyblockutility.ui.render;

/**
 * A scale about a centre point, one axis at a time (REQ-UI-18, T2.9): where a laid-out point is drawn
 * while the settings panel opens, and the inverse, where a mouse position lies in the layout. Doubles,
 * so a round trip is exact at a control's edges. A scale of 1, 0 or less, or NaN is no scale at all.
 */
public final class ScaleAbout {
	private ScaleAbout() {
	}

	/** Where a layout coordinate is drawn. */
	public static double toScreen(double local, double centre, float scale) {
		return identity(scale) ? local : centre + (local - centre) * scale;
	}

	/** Which layout coordinate a drawn (mouse) coordinate falls on. */
	public static double toLocal(double screen, double centre, float scale) {
		return identity(scale) ? screen : centre + (screen - centre) / scale;
	}

	private static boolean identity(float scale) {
		return scale == 1F || !(scale > 0F);
	}
}
