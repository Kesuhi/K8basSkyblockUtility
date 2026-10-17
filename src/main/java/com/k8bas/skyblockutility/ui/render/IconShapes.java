package com.k8bas.skyblockutility.ui.render;

import java.util.ArrayList;
import java.util.List;

/**
 * The UI's icons as our own geometry (REQ-UI-32, REQ-UI-24, T3.20): strokes with round ends along line segments and arcs in
 * a unit box, rasterised in code at icon size × GUI scale with 4×4 supersampling, so an icon is as sharp at GUI scale 2 as
 * a font is not; no image or font file. The coordinates are sixteenths of the box, so a mirrored point meets a mirrored
 * stroke in exactly the same arithmetic and the chevrons and the × come out exactly symmetric. Pure, so it is tested
 * without a game.
 */
public final class IconShapes {
	/** The icon set: the chevrons, the × of the search box, the check mark, the search glass and the reset arrow. */
	public enum Icon {
		/** Points down: a closed dropdown. */
		CHEVRON_DOWN(new double[] {8, 11, 3, 6}, new double[] {8, 11, 13, 6}),
		/** Points up: an open dropdown. */
		CHEVRON_UP(new double[] {8, 5, 3, 10}, new double[] {8, 5, 13, 10}),
		/** Points right: a closed folder or rule header. */
		CHEVRON_RIGHT(new double[] {11, 8, 6, 3}, new double[] {11, 8, 6, 13}),
		/** The ×, four rays from the centre. */
		CROSS(new double[] {8, 8, 4, 4}, new double[] {8, 8, 12, 4}, new double[] {8, 8, 4, 12}, new double[] {8, 8, 12, 12}),
		/** The check mark of the selected entry. */
		CHECK(new double[] {3, 8.5, 6.5, 12}, new double[] {6.5, 12, 13, 4.5}),
		/** The search glass: a ring and its handle. */
		SEARCH(new double[] {10, 10, 13.5, 13.5}, new double[] {7, 7, 4.5, 0, 360}),
		/** The reset arrow: a circle open at the top, with an arrow head at its end on the right, pointing back into the gap. */
		RESET(new double[] {8, 8.5, 5, -40, 220}, new double[] {11.83, 5.29, 11.83, 8.0}, new double[] {11.83, 5.29, 14.5, 5.76});

		/** A segment is {x0, y0, x1, y1}; an arc is {cx, cy, r, from°, to°}, clockwise on screen. All in sixteenths of the box. */
		private final double[][] strokes;

		Icon(double[]... strokes) {
			this.strokes = strokes;
		}
	}

	private static final int SAMPLES = 4;

	private IconShapes() {
	}

	/**
	 * Half the stroke's width in screen pixels for an icon px pixels wide: at least 1 px, so a pixel whose centre the centre
	 * line passes within half a pixel is at least 0.9 covered at every angle (AC-UI-33).
	 */
	public static double halfStroke(int px) {
		return Math.max(1.0, 0.075 * px);
	}

	/** The alpha (0-255) of each pixel of the icon at px × px screen pixels, row-major: the share of its 4×4 samples on the stroke. */
	public static int[] bake(Icon icon, int px) {
		double half = halfStroke(px);
		int[] alpha = new int[px * px];
		for (int y = 0; y < px; y++) {
			for (int x = 0; x < px; x++) {
				int inside = 0;
				for (int j = 0; j < SAMPLES; j++) {
					for (int i = 0; i < SAMPLES; i++) {
						if (distance(icon, px, x + (i + 0.5) / SAMPLES, y + (j + 0.5) / SAMPLES) <= half) {
							inside++;
						}
					}
				}
				alpha[y * px + x] = Math.round(255F * inside / (SAMPLES * SAMPLES));
			}
		}
		return alpha;
	}

	/**
	 * The icon as fill spans (REQ-UI-47, where no texture can be had): its pixels at least half covered, one quad {x0, y0, x1,
	 * y1} per run of them in a row, x1 and y1 exclusive, none overlapping.
	 */
	public static int[] spans(Icon icon, int px) {
		int[] alpha = bake(icon, px);
		List<int[]> runs = new ArrayList<>();
		for (int y = 0; y < px; y++) {
			int start = -1;
			for (int x = 0; x <= px; x++) {
				boolean on = x < px && alpha[y * px + x] >= 128;
				if (on && start < 0) {
					start = x;
				} else if (!on && start >= 0) {
					runs.add(new int[] {start, y, x, y + 1});
					start = -1;
				}
			}
		}
		int[] quads = new int[runs.size() * 4];
		for (int i = 0; i < runs.size(); i++) {
			System.arraycopy(runs.get(i), 0, quads, i * 4, 4);
		}
		return quads;
	}

	/** The distance in screen pixels from (x, y) to the icon's centre line, the icon px pixels wide. */
	public static double distance(Icon icon, int px, double x, double y) {
		double unit = px / 16.0;
		double best = Double.MAX_VALUE;
		for (double[] stroke : icon.strokes) {
			double d = stroke.length == 4
					? segment(x, y, stroke[0] * unit, stroke[1] * unit, stroke[2] * unit, stroke[3] * unit)
					: arc(x, y, stroke[0] * unit, stroke[1] * unit, stroke[2] * unit, stroke[3], stroke[4]);
			best = Math.min(best, d);
		}
		return best;
	}

	/** From (x, y) to the segment from (ax, ay) to (bx, by). */
	private static double segment(double x, double y, double ax, double ay, double bx, double by) {
		double ex = bx - ax;
		double ey = by - ay;
		double t = Math.max(0, Math.min(1, ((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey)));
		double dx = x - ax - t * ex;
		double dy = y - ay - t * ey;
		return Math.sqrt(dx * dx + dy * dy);
	}

	/** From (x, y) to the arc of radius r about (cx, cy) from one angle to the other, in degrees clockwise on screen. */
	private static double arc(double x, double y, double cx, double cy, double r, double from, double to) {
		double angle = Math.toDegrees(Math.atan2(y - cy, x - cx));
		while (angle < from) {
			angle += 360;
		}
		if (angle <= to) {
			return Math.abs(Math.hypot(x - cx, y - cy) - r);
		}
		double fx = cx + r * Math.cos(Math.toRadians(from));
		double fy = cy + r * Math.sin(Math.toRadians(from));
		double tx = cx + r * Math.cos(Math.toRadians(to));
		double ty = cy + r * Math.sin(Math.toRadians(to));
		return Math.min(Math.hypot(x - fx, y - fy), Math.hypot(x - tx, y - ty));
	}
}
