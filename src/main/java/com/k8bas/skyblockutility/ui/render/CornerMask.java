package com.k8bas.skyblockutility.ui.render;

/**
 * The coverage of the anti-aliased corners (REQ-UI-18, T2.9d, R32): a 2R×2R image of a disc of radius R, one
 * alpha per screen pixel, fully covered up to half a pixel inside the rim and fading over one pixel, the ramp
 * the colour wheel uses. A pixel at least half covered is exactly a pixel the fill-based corners draw
 * ({@link Corners}), so turning the setting on only softens the edge. Written from SPEC REQ-UI-18; no
 * AlpakaAddons code (REQ-UI-24).
 */
public final class CornerMask {
	/** The largest radius, in screen pixels, a mask is made for (a 512×512 texture); larger shapes use the fills. */
	public static final int MAX_RADIUS = 256;

	private CornerMask() {
	}

	/** The alpha (0-255) of pixel (x, y) of a filled corner mask of radius r. */
	public static int alpha(int r, int x, int y) {
		return Math.round(255 * coverage(r, x, y, r));
	}

	/**
	 * The alpha of pixel (x, y) of an outline's corner mask: the disc of radius r minus the concentric one of
	 * radius r - t (the inner shape is inset by t with radius r - t), rounded once.
	 */
	public static int ringAlpha(int r, int t, int x, int y) {
		float inner = r - t > 0 ? coverage(r, x, y, r - t) : 0;
		return Math.round(255 * Math.max(0, coverage(r, x, y, r) - inner));
	}

	/** The mask of {@link #alpha}, row-major. */
	public static int[] bakeFill(int r) {
		int size = 2 * r;
		int[] alpha = new int[size * size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				alpha[y * size + x] = alpha(r, x, y);
			}
		}
		return alpha;
	}

	/** The mask of {@link #ringAlpha}, row-major. */
	public static int[] bakeRing(int r, int t) {
		int size = 2 * r;
		int[] alpha = new int[size * size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				alpha[y * size + x] = ringAlpha(r, t, x, y);
			}
		}
		return alpha;
	}

	/** How much of pixel (x, y) a disc of the given radius, centred at (r, r), covers: 0 to 1. */
	private static float coverage(int r, int x, int y, int radius) {
		double distance = Math.hypot(x + 0.5 - r, y + 0.5 - r);
		return (float) Math.max(0, Math.min(1, radius + 0.5 - distance));
	}
}
