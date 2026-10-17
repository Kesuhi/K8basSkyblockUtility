package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Corners;
import com.k8bas.skyblockutility.ui.render.MaskKey;
import com.k8bas.skyblockutility.ui.render.ShapePlan;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-31 (REQ-UI-28, REQ-UI-30, T3.18): for knob sizes 10, 11 and 12 GUI px at GUI scales 1-4, the slider's fill ends
 * square, and the rest of its track starts, at knob centre ∓ sqrt(r² − (h/2)²) snapped to the nearest physical px (∓ r
 * for a dimmed knob); their outer ends are caps of the disc mask, and a fill shorter than its cap shows only the part of
 * the cap before its square end.
 */
class SliderTrackTest {
	private static final int TRACK_X = 20;
	private static final int TRACK_W = 120;

	/**
	 * REQ-UI-47: the disc mask chosen for the knob is its own at rest and the grown knob's from the first frame it grows until
	 * it has shrunk back, so the in-between sizes need no masks of their own (at GUI scale 9 they would be 19 of the 64).
	 * ConfigMotionGameTest counts the masks a drawn knob adds.
	 */
	@Test
	void sizesInBetweenBorrowTheGrownMask() {
		for (int scale = 1; scale <= 10; scale++) {
			java.util.Set<Integer> masks = new java.util.TreeSet<>();
			for (int step = 0; step <= 1000; step++) {
				masks.add(Slider.knobMaskPx(step / 1000F, scale));
			}
			assertEquals(java.util.Set.of(Slider.KNOB_SIZE * scale, Slider.KNOB_GROWN * scale), masks,
					"scale " + scale + ": the masks of the resting and the grown knob only");
			assertEquals(Slider.KNOB_SIZE * scale, Slider.knobMaskPx(0F, scale), "at rest, its own");
			assertEquals(Slider.KNOB_GROWN * scale, Slider.knobMaskPx(1F, scale), "grown, its own");
			assertEquals(Slider.KNOB_GROWN * scale, Slider.knobMaskPx(Float.MIN_VALUE, scale), "the grown knob's from the first frame");
		}
	}

	@Test
	void fillAndTrackEndAtTheKnob() {
		int checked = 0;
		int shortCaps = 0;
		for (int knob = 10; knob <= 12; knob++) {
			for (int scale = 1; scale <= 4; scale++) {
				for (boolean dimmed : new boolean[] {false, true}) {
					for (float knobX = TRACK_X; knobX <= TRACK_X + TRACK_W - knob; knobX += 0.25F) {
						String what = "knob " + knob + ", scale " + scale + (dimmed ? " dimmed" : "") + ", at " + knobX;
						float r = knob / 2F;
						float h = Slider.TRACK_HEIGHT / 2F;
						double reach = dimmed ? r : Math.sqrt(r * r - h * h);
						float[] gap = Slider.trackGap(knobX, knob, Slider.TRACK_HEIGHT, dimmed);
						assertEquals(knobX + r - reach, gap[0], 1e-4, what + ": the fill's end");
						assertEquals(knobX + r + reach, gap[1], 1e-4, what + ": the track's start");
						int d = Slider.TRACK_HEIGHT * scale;
						int[] fill = ShapePlan.barSpan(TRACK_X, gap[0], scale);
						if (fill != null) {
							assertEquals(TRACK_X * scale, fill[0], what);
							assertEquals(Math.round((knobX + r - reach) * scale), fill[0] + fill[1], what + ": snapped to the nearest pixel");
							ShapePlan.Element cap = check(fill, Corners.LEFT, scale, what + ", the fill");
							assertEquals(0, cap.quads()[0], what + ": the fill starts with its cap");
							assertEquals(0F, cap.uvs()[0], what + ": the disc's left edge");
							if (fill[1] < d / 2) {
								assertArrayEquals(new float[] {0, 0, (float) fill[1] / d, 1}, cap.uvs(), what + ": only the cap's part before the square end");
								shortCaps++;
							}
						}
						int[] rest = ShapePlan.barSpan(gap[1], TRACK_X + TRACK_W, scale);
						if (rest != null) {
							assertEquals(Math.round((knobX + r + reach) * scale), rest[0], what + ": snapped to the nearest pixel");
							assertEquals((TRACK_X + TRACK_W) * scale, rest[0] + rest[1], what);
							ShapePlan.Element cap = check(rest, Corners.RIGHT, scale, what + ", the track");
							assertEquals(rest[1], cap.quads()[2], what + ": the track ends with its cap");
							assertEquals(1F, cap.uvs()[2], what + ": the disc's right edge");
						}
						checked++;
					}
				}
			}
		}
		assertTrue(checked > 5000 && shortCaps > 0, "the sweep ran: " + checked + ", " + shortCaps + " short fills");
	}

	/** The bar's plan: the disc mask of the track's height; beside the cap the square end is a plain fill of the full height. */
	private static ShapePlan.Element check(int[] span, int corners, int scale, String what) {
		int d = Slider.TRACK_HEIGHT * scale;
		ShapePlan.Result plan = ShapePlan.ofPixels(new Matrix3x2f(), (float) span[0] / scale, 30, span[1], d,
				ShapePlan.barRadius(Slider.TRACK_HEIGHT, scale), 0, corners, scale, true, null);
		assertEquals(MaskKey.disc(d), plan.maskKey(), what);
		ShapePlan.Element cap = plan.elements().get(0);
		assertTrue(cap.textured(), what + ": the cap is a mask quad");
		if (span[1] > d / 2) {
			int[] square = plan.elements().get(1).quads();
			assertArrayEquals(corners == Corners.LEFT ? new int[] {d / 2, 0, span[1], d} : new int[] {0, 0, span[1] - d / 2, d}, square,
					what + ": the square end is a plain fill of the full height");
		}
		return cap;
	}
}
