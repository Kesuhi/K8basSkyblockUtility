package com.k8bas.skyblockutility.ui.widget;

import java.awt.image.BufferedImage;

/**
 * AC-UI-37 [C] (REQ-UI-36, T3.27), for gametests only: a scrollbar in a screenshot, measured on the row through its thumb's
 * middle against the rule written out here: the thumb's right edge 2 GUI px in from the region's, 3 GUI px wide idle and 5
 * hovered or dragged, its ends round, and a faint track beside it only while it is wide.
 */
public final class ScrollbarProbe {
	private ScrollbarProbe() {
	}

	/**
	 * @param regionRight the region's right edge, GUI px
	 * @param regionTop the region's top, GUI px
	 * @param regionBottom the region's bottom, GUI px
	 * @param thumbTop the thumb's top, GUI px (absolute)
	 * @param thumbLength its length, GUI px
	 * @return "" when it matches, else what does not
	 */
	public static String check(BufferedImage image, String what, int scale, int regionRight, int regionTop, int regionBottom, int thumbTop,
			int thumbLength, boolean hot) {
		int right = (regionRight - 2) * scale;
		int expected = (hot ? 5 : 3) * scale;
		int row = (thumbTop + thumbLength / 2) * scale;
		int colour = image.getRGB(right - 1, row);
		int width = 0;
		while (right - 1 - width >= 0 && near(image.getRGB(right - 1 - width, row), colour)) {
			width++;
		}
		StringBuilder problems = new StringBuilder();
		if (width != expected) {
			problems.append("the thumb is ").append(width).append(" px wide, not ").append(expected).append("; ");
		}
		if (near(image.getRGB(right, row), colour)) {
			problems.append("it reaches past 2 GUI px from the region's edge; ");
		}
		// A pill: its top corner pixel is soft, not the thumb's full colour.
		int top = (thumbTop + 1) * scale;
		if (near(image.getRGB(right - expected, top), colour)) {
			problems.append("its top end is square; ");
		}
		// The track: above or below the thumb, on its right and left columns, against the region's own background beside it.
		int outside = thumbTop - regionTop > 6 ? (regionTop + 3) * scale : (thumbTop + thumbLength + 3) * scale;
		if (outside >= (regionBottom - 2) * scale) {
			problems.append("no room beside the thumb to look for the track; ");
		} else {
			boolean track = differs(image.getRGB(right - 1, outside), image.getRGB(right, outside))
					&& differs(image.getRGB(right - expected, outside), image.getRGB(right, outside));
			if (track != hot) {
				problems.append(hot ? "no track shows; " : "a track shows while idle; ");
			}
		}
		return problems.isEmpty() ? "" : what + " at scale " + scale + ": " + problems;
	}

	/** Faint, but there: some channel 4 or more apart. */
	private static boolean differs(int a, int b) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) >= 4) {
				return true;
			}
		}
		return false;
	}

	private static boolean near(int a, int b) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > 8) {
				return false;
			}
		}
		return true;
	}
}
