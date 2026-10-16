package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.CornerMask;

import java.awt.image.BufferedImage;

/**
 * AC-UI-28 (REQ-UI-27, REQ-UI-28), for gametests only: a slider's knob in a screenshot, measured as it is drawn (on the
 * physical-pixel grid, T3.25; grown when hot, T3.36). Knobs and pills are smooth discs whatever "Smooth corners" says
 * (REQ-UI-29), so their pixels are judged by mask coverage. Used by WidgetsGameTest and the UI review's screenshot matrix.
 */
public final class KnobProbe {
	private KnobProbe() {
	}

	/**
	 * A slider as the probe reads it, in GUI px unless said: its x, its track's top, its width, its knob's layout x, its knob's
	 * layout top, 1 if dimmed, and its knob as drawn last in screen px: left, top and size.
	 */
	public static int[] of(Slider slider) {
		int[] drawn = slider.drawnKnob();
		return new int[] {slider.x(), slider.y() + (slider.height() - Slider.TRACK_HEIGHT) / 2, slider.width(), slider.knobX(),
				slider.y() + (slider.height() - Slider.KNOB_SIZE) / 2, slider.isDimmed() ? 1 : 0, drawn[0], drawn[1], drawn[2]};
	}

	/**
	 * The knob's rules: drawn 10 or 12 GUI px across; its interior (every pixel it fully covers) has the colour of its centre, so
	 * no fill shows; a dimmed knob's box has none of the fill's colour; with {@code notchCheck} and the knob away from the track's
	 * ends, the track's top and bottom rows show no backdrop beside it; its outermost pixels left and right match on every row
	 * above and below the track. Returns "" when they hold, else the first that does not.
	 */
	public static String knob(BufferedImage shot, int scale, int[] slider, boolean notchCheck, String what) {
		int size = slider[8];
		int r = size / 2;
		int left = slider[6];
		int top = slider[7];
		if (size != Slider.KNOB_SIZE * scale && size != Slider.KNOB_GROWN * scale) {
			return what + ": the knob is drawn " + size + " px, not 10 or 12 GUI px across";
		}
		boolean dimmed = slider[5] == 1;
		int centre = shot.getRGB(left + r, top + r);
		// A knob is never in the accent: a fill drawn over all of it would still look uniform.
		if (near(centre, com.k8bas.skyblockutility.ui.render.Theme.current().accent())) {
			return what + ": the knob's centre has the accent colour: " + Integer.toHexString(centre);
		}
		int interior = 0;
		int wrong = 0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (CornerMask.discAlpha(size, x, y) == 255) {
					interior++;
					if (!near(shot.getRGB(left + x, top + y), centre)) {
						wrong++;
					}
				}
			}
		}
		if (interior == 0 || wrong > 0) {
			return what + ": " + wrong + " of " + interior + " knob pixels differ from its centre";
		}
		int trackX = slider[0] * scale;
		int trackTop = slider[1] * scale;
		int trackW = slider[2] * scale;
		// The fill's colour where it shows: 2 GUI px into the track, when the knob leaves at least 3 GUI px of it.
		if (dimmed && left > trackX + 3 * scale) {
			int fill = shot.getRGB(trackX + 2 * scale, trackTop + Slider.TRACK_HEIGHT * scale / 2);
			int fillPixels = 0;
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					if (near(shot.getRGB(left + x, top + y), fill)) {
						fillPixels++;
					}
				}
			}
			if (fillPixels > 0) {
				return what + ": " + fillPixels + " pixels of the dimmed fill inside the knob's box";
			}
		}
		if (notchCheck && !dimmed && left - scale >= trackX && left + size + scale <= trackX + trackW) {
			int backdrop = shot.getRGB(left + r, top - 2 * scale);
			for (int y : new int[] {trackTop, trackTop + Slider.TRACK_HEIGHT * scale - 1}) {
				for (int x = left - scale; x < left + size + scale; x++) {
					if (near(shot.getRGB(x, y), backdrop)) {
						return what + ": the backdrop shows on the track's row " + (y - top) + " at x " + (x - left);
					}
				}
			}
		}
		return rimSymmetry(shot, size, left, top, trackTop, scale, what);
	}

	/**
	 * Above and below the track the knob's outermost pixels left and right match: the knob is a smooth disc whatever the setting
	 * (REQ-UI-29), so its rim is anti-aliased in both passes and must blend over the same backdrop.
	 */
	private static String rimSymmetry(BufferedImage shot, int size, int left, int top, int trackTop, int scale, String what) {
		int trackRows = Slider.TRACK_HEIGHT * scale;
		for (int y = 0; y < size; y++) {
			int row = top + y;
			if (row >= trackTop && row < trackTop + trackRows) {
				continue;
			}
			int a = -1;
			int b = -1;
			for (int x = 0; x < size; x++) {
				if (CornerMask.discAlpha(size, x, y) > 0) {
					if (a < 0) {
						a = x;
					}
					b = x;
				}
			}
			if (a >= 0) {
				int la = shot.getRGB(left + a, row);
				int rb = shot.getRGB(left + b, row);
				if (!near(la, rb)) {
					return what + ": the rim differs left and right on knob row " + y + ": " + Integer.toHexString(la) + " vs " + Integer.toHexString(rb);
				}
			}
		}
		return "";
	}

	/** Next to a knob at either end, every pixel outside both the full track pill and the knob's disc is backdrop; "" when so. */
	public static String trackEnds(BufferedImage shot, int scale, int[] slider, String what) {
		int size = slider[8];
		int left = slider[6];
		int top = slider[7];
		int trackX = slider[0] * scale;
		int trackTop = slider[1] * scale;
		int trackW = slider[2] * scale;
		int trackH = Slider.TRACK_HEIGHT * scale;
		int backdrop = shot.getRGB(left + size / 2, top - 2 * scale);
		// Discs and pills are smooth whatever the setting (REQ-UI-29): a pixel any of their masks touches may be tinted.
		// The pill's caps are the outer halves of the disc mask of its height (REQ-UI-30).
		int cap = trackH / 2;
		for (int y = 0; y < trackH; y++) {
			for (int x = left - scale; x < left + size + scale; x++) {
				int px = x - trackX;
				boolean inPill = px >= 0 && px < trackW && (px >= cap && px < trackW - cap
						|| CornerMask.discAlpha(trackH, px < cap ? px : px - (trackW - trackH), y) > 0);
				int ky = trackTop + y - top;
				boolean inDisc = x >= left && x < left + size && ky >= 0 && ky < size && CornerMask.discAlpha(size, x - left, ky) > 0;
				if (!inPill && !inDisc && !near(shot.getRGB(x, trackTop + y), backdrop)) {
					return what + ": fill or track outside the pill and the knob at track row " + y + ", x " + (x - left) + " from the knob";
				}
			}
		}
		return "";
	}

	/** Within 8 per colour channel. */
	public static boolean near(int a, int b) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > 8) {
				return false;
			}
		}
		return true;
	}
}
