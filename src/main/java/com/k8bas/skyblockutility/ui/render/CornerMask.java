package com.k8bas.skyblockutility.ui.render;

/**
 * The coverage of the anti-aliased corners (REQ-UI-18, T2.9d, R32): a 2R×2R image of a disc of radius R, one
 * alpha per screen pixel, fully covered up to half a pixel inside the rim and fading over one pixel, the ramp
 * the colour wheel uses. A pixel at least half covered is exactly a pixel the fill-based corners draw
 * ({@link Corners}), so turning the setting on only softens the edge. The disc masks of discs and pills (REQ-UI-30,
 * T3.18) are the exact area of each pixel inside the circle instead, centred on a mask of any diameter. Written from
 * SPEC REQ-UI-18 and REQ-UI-30; no AlpakaAddons code (REQ-UI-24).
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

	/**
	 * The alpha (0-255) of pixel (x, y) of a disc mask of diameter d: the area of the pixel inside the circle of radius d/2
	 * centred on the mask. The pixel is folded into one octant first, so the mask is exactly symmetric.
	 */
	public static int discAlpha(int d, int x, int y) {
		int fx = Math.min(x, d - 1 - x);
		int fy = Math.min(y, d - 1 - y);
		double r = d / 2.0;
		double x0 = Math.min(fx, fy) - r;
		double y0 = Math.max(fx, fy) - r;
		return (int) Math.round(255 * (below(r, x0, x0 + 1, y0 + 1) - below(r, x0, x0 + 1, y0)));
	}

	/** The mask of {@link #discAlpha}, row-major. */
	public static int[] bakeDisc(int d) {
		int[] alpha = new int[d * d];
		for (int y = 0; y < d; y++) {
			for (int x = 0; x < d; x++) {
				alpha[y * d + x] = discAlpha(d, x, y);
			}
		}
		return alpha;
	}

	/**
	 * The area of the circle of radius r about the origin between x0 and x1 and between 0 and y, negative for a negative y:
	 * the integral over x of min(|y|, h) with h = sqrt(r² − x²) the circle's upper half. That is h where the circle stays
	 * below |y|, and |y| where it rises above, between ±sqrt(r² − y²).
	 */
	private static double below(double r, double x0, double x1, double y) {
		double t = Math.abs(y);
		double area = halfChord(r, x1) - halfChord(r, x0);
		if (t < r) {
			double w = Math.sqrt(r * r - t * t);
			double p = Math.max(x0, -w);
			double q = Math.min(x1, w);
			if (q > p) {
				area -= halfChord(r, q) - halfChord(r, p) - t * (q - p);
			}
		}
		return Math.copySign(area, y);
	}

	/** The integral of sqrt(r² − u²) from 0 to x, with x clamped to [−r, r]: the area under the circle's upper half. */
	private static double halfChord(double r, double x) {
		double u = Math.max(-r, Math.min(r, x));
		return (u * Math.sqrt(r * r - u * u) + r * r * Math.asin(u / r)) / 2;
	}

	/** How much of pixel (x, y) a disc of the given radius, centred at (r, r), covers: 0 to 1. */
	private static float coverage(int r, int x, int y, int radius) {
		double distance = Math.hypot(x + 0.5 - r, y + 0.5 - r);
		return (float) Math.max(0, Math.min(1, radius + 0.5 - distance));
	}
}
