package com.k8bas.skyblockutility.ui.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rounded rectangles as rows of plain fills (REQ-UI-17, D-8: corners from fills, no texture). A pixel
 * of a corner is left out when its centre lies outside the corner's circle. Rows with the same span
 * are merged, and spans never overlap, so a translucent colour blends evenly. Written from SPEC
 * REQ-UI-17; no AlpakaAddons code (REQ-UI-24).
 */
public final class Corners {
	/** A fill from (x0, y0) inclusive to (x1, y1) exclusive. */
	public record Span(int x0, int y0, int x1, int y1) {
	}

	/** Which corners are round, for {@link #fill(int, int, int, int)}: any of these or-ed together. */
	public static final int TOP_LEFT = 1;
	public static final int TOP_RIGHT = 2;
	public static final int BOTTOM_LEFT = 4;
	public static final int BOTTOM_RIGHT = 8;
	public static final int LEFT = TOP_LEFT | BOTTOM_LEFT;
	public static final int RIGHT = TOP_RIGHT | BOTTOM_RIGHT;
	public static final int ALL = LEFT | RIGHT;

	private Corners() {
	}

	/** How many pixels each of a corner's {@code r} rows leaves out, from the outer row in. */
	public static int[] insets(int r) {
		int[] insets = new int[Math.max(0, r)];
		double limit = (double) r * r;
		for (int i = 0; i < insets.length; i++) {
			double dy = r - i - 0.5;
			int cut = 0;
			while (cut < r) {
				double dx = r - cut - 0.5;
				if (dx * dx + dy * dy <= limit) {
					break;
				}
				cut++;
			}
			insets[i] = cut;
		}
		return insets;
	}

	/** A w×h rect with corners of radius r (clamped to half the shorter side); empty if w or h is not positive. */
	public static List<Span> fill(int w, int h, int r) {
		return fill(w, h, r, ALL);
	}

	/**
	 * As {@link #fill(int, int, int)}, with only the given {@code corners} round and the others square. A one-cap pill
	 * ({@link #LEFT} or {@link #RIGHT}) keeps its radius up to half its height however short it is, and a pill shorter
	 * than its cap shows only the part of the cap before its square end (REQ-UI-30).
	 */
	public static List<Span> fill(int w, int h, int r, int corners) {
		if (w <= 0 || h <= 0) {
			return List.of();
		}
		int[] insets = insets(oneCap(corners) ? Math.max(0, Math.min(r, h / 2)) : clampRadius(w, h, r));
		List<int[][]> rows = new ArrayList<>(h);
		for (int y = 0; y < h; y++) {
			int inset = inset(insets, y, h);
			boolean top = y < insets.length;
			int left = (corners & (top ? TOP_LEFT : BOTTOM_LEFT)) != 0 ? inset : 0;
			int right = (corners & (top ? TOP_RIGHT : BOTTOM_RIGHT)) != 0 ? inset : 0;
			rows.add(new int[][] {{left, w - right}});
		}
		return merge(rows);
	}

	/** The outline of thickness t: the rounded rect minus the one inside it (inset t, radius r - t). */
	public static List<Span> ring(int w, int h, int r, int t) {
		if (w <= 0 || h <= 0 || t <= 0) {
			return List.of();
		}
		int radius = clampRadius(w, h, r);
		int[] outer = insets(radius);
		int innerW = w - 2 * t;
		int innerH = h - 2 * t;
		int[] inner = innerW > 0 && innerH > 0 ? insets(clampRadius(innerW, innerH, Math.max(0, radius - t))) : null;
		List<int[][]> rows = new ArrayList<>(h);
		for (int y = 0; y < h; y++) {
			int a = inset(outer, y, h);
			int b = w - a;
			int innerY = y - t;
			if (inner == null || innerY < 0 || innerY >= innerH) {
				rows.add(new int[][] {{a, b}});
			} else {
				int innerInset = inset(inner, innerY, innerH);
				rows.add(new int[][] {{a, t + innerInset}, {w - t - innerInset, b}});
			}
		}
		return merge(rows);
	}

	/** Round on one side only. */
	public static boolean oneCap(int corners) {
		return corners == LEFT || corners == RIGHT;
	}

	private static int clampRadius(int w, int h, int r) {
		return Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
	}

	private static int inset(int[] insets, int y, int h) {
		if (y < insets.length) {
			return insets[y];
		}
		if (y >= h - insets.length) {
			return insets[h - 1 - y];
		}
		return 0;
	}

	/** One span per run of rows with the same horizontal pieces; empty pieces are dropped. */
	private static List<Span> merge(List<int[][]> rows) {
		List<Span> spans = new ArrayList<>();
		int start = 0;
		for (int y = 1; y <= rows.size(); y++) {
			if (y == rows.size() || !Arrays.deepEquals(rows.get(y), rows.get(start))) {
				for (int[] piece : rows.get(start)) {
					if (piece[1] > piece[0]) {
						spans.add(new Span(piece[0], start, piece[1], y));
					}
				}
				start = y;
			}
		}
		return spans;
	}
}
