package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.CornerMask;
import com.k8bas.skyblockutility.ui.render.Corners;
import com.k8bas.skyblockutility.ui.render.Shapes;

import java.awt.image.BufferedImage;

/**
 * AC-UI-29 (REQ-UI-27), for gametests only: in the corner squares of a hairline outline in a screenshot, every pixel the
 * outline's own coverage fully covers shows the outline colour, so no fill is drawn over it. With "Smooth corners" the
 * coverage is the ring mask's; without, the fill-based staircase's. Used by WidgetsGameTest and the UI review's matrix.
 */
public final class OutlineProbe {
	private OutlineProbe() {
	}

	/**
	 * The corners of the w×h outline with radius r at (x, y), all in screen px; returns {pixels checked, pixels without
	 * {@code colour}}.
	 */
	public static int[] corners(BufferedImage shot, int scale, boolean smooth, int x, int y, int w, int h, int r, int colour) {
		return corners(shot, smooth, x, y, w, h, r, Shapes.hairlinePx(scale), colour);
	}

	/** As {@link #corners(BufferedImage, int, boolean, int, int, int, int, int, int)} for an outline {@code t} screen px thick (a focus ring). */
	public static int[] corners(BufferedImage shot, boolean smooth, int x, int y, int w, int h, int r, int t, int colour) {
		int checked = 0;
		int wrong = 0;
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				boolean corner = (px < r || px >= w - r) && (py < r || py >= h - r);
				if (corner && covers(smooth, w, h, r, t, px, py)) {
					checked++;
					if (!KnobProbe.near(shot.getRGB(x + px, y + py), colour)) {
						wrong++;
					}
				}
			}
		}
		return new int[] {checked, wrong};
	}

	/** Whether the outline, t screen px thick, fully covers screen pixel (x, y) of the w×h box with radius r. */
	public static boolean covers(boolean smooth, int w, int h, int r, int t, int x, int y) {
		if (smooth) {
			int mx = x < r ? x : x - (w - 2 * r);
			int my = y < r ? y : y - (h - 2 * r);
			return CornerMask.ringAlpha(r, t, mx, my) == 255;
		}
		for (Corners.Span span : Corners.ring(w, h, r, t)) {
			if (x >= span.x0() && x < span.x1() && y >= span.y0() && y < span.y1()) {
				return true;
			}
		}
		return false;
	}
}
