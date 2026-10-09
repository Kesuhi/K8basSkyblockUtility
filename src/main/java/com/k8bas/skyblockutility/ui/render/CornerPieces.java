package com.k8bas.skyblockutility.ui.render;

/**
 * A smooth rounded shape (REQ-UI-18, T2.9d, R32) as pieces in screen pixels: four R×R quads, each showing its
 * quadrant of a {@link CornerMask}, and plain fills for the straight parts. The pieces never overlap, so a
 * translucent colour blends evenly. Each piece is {@link #STRIDE} ints in the caller's buffer: kind, x0, y0,
 * x1, y1 (x1 and y1 exclusive), and the mask's u, v. Nothing is allocated, since it runs for every shape drawn.
 */
public final class CornerPieces {
	public static final int STRIDE = 7;
	public static final int KIND_FILL = 0;
	public static final int KIND_MASK = 1;
	/** Four corners plus at most four straight parts. */
	public static final int MAX_PIECES = 8;

	private CornerPieces() {
	}

	/** The radius a w×h shape can have: at most half its shorter side, as {@link Corners} draws it. */
	public static int clampRadius(int w, int h, int r) {
		return Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
	}

	/**
	 * A filled w×h shape with corners of radius r (already clamped) into {@code out}.
	 *
	 * @return the number of pieces, or -1 if a mask cannot draw it (the fill-based corners do)
	 */
	public static int fill(int w, int h, int r, int[] out) {
		if (r < 1 || r > CornerMask.MAX_RADIUS || 2 * r > w || 2 * r > h) {
			return -1;
		}
		int n = corners(w, h, r, out);
		n = straight(out, n, r, 0, w - r, r);
		n = straight(out, n, r, h - r, w - r, h);
		return straight(out, n, 0, r, w, h - r);
	}

	/**
	 * The outline, t thick, of a w×h shape with corners of radius r (already clamped) into {@code out}.
	 *
	 * @return the number of pieces, or -1 if a mask cannot draw it (thicker than its radius, or no hole left)
	 */
	public static int ring(int w, int h, int r, int t, int[] out) {
		if (t < 1 || t > r || r > CornerMask.MAX_RADIUS || 2 * r > w || 2 * r > h || 2 * t >= w || 2 * t >= h) {
			return -1;
		}
		int n = corners(w, h, r, out);
		n = straight(out, n, r, 0, w - r, t);
		n = straight(out, n, r, h - t, w - r, h);
		n = straight(out, n, 0, r, t, h - r);
		return straight(out, n, w - t, r, w, h - r);
	}

	/** The mask's cache key: a fill has thickness 0. */
	public static int key(int r, int t) {
		return r << 9 | t;
	}

	private static int corners(int w, int h, int r, int[] out) {
		int n = mask(out, 0, 0, 0, r, 0, 0);
		n = mask(out, n, w - r, 0, r, r, 0);
		n = mask(out, n, 0, h - r, r, 0, r);
		return mask(out, n, w - r, h - r, r, r, r);
	}

	private static int mask(int[] out, int n, int x, int y, int r, int u, int v) {
		int o = n * STRIDE;
		out[o] = KIND_MASK;
		out[o + 1] = x;
		out[o + 2] = y;
		out[o + 3] = x + r;
		out[o + 4] = y + r;
		out[o + 5] = u;
		out[o + 6] = v;
		return n + 1;
	}

	/** A fill piece, skipped when it has no area. */
	private static int straight(int[] out, int n, int x0, int y0, int x1, int y1) {
		if (x1 <= x0 || y1 <= y0) {
			return n;
		}
		int o = n * STRIDE;
		out[o] = KIND_FILL;
		out[o + 1] = x0;
		out[o + 2] = y0;
		out[o + 3] = x1;
		out[o + 4] = y1;
		out[o + 5] = 0;
		out[o + 6] = 0;
		return n + 1;
	}
}
