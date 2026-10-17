package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-62 and AC-UI-63 (REQ-UI-58, REQ-UI-59, T3.51): a slider of at most 15 values, at least 4 GUI px apart, has a dot at
 * each value, none where the knob's disc touches it; a release clicks only when the drag or the press changed the value.
 */
class SliderDotsTest {
	/** The settings' track for a slider: 80 GUI px wide at most (ConfigLayout), the knob's centre running over 70. */
	private static final int TRACK = 80;

	@Test
	void theNoticeDurationSliderHasADotAtEachOfItsFifteenValues() {
		SliderModel notice = SliderModel.ofInt(1, 15, 1);
		float[] dots = Slider.dotCentres(notice, 100, TRACK);
		assertEquals(15, dots.length);
		for (int i = 0; i < dots.length; i++) {
			assertEquals(100 + Slider.KNOB_SIZE / 2F + i * 5F, dots[i], 1e-4, "value " + (i + 1) + " where the knob's centre would be");
		}
	}

	@Test
	void longerOrDenserSlidersHaveNone() {
		assertEquals(21, SliderModel.ofInt(0, 100, 5).valueCount(), "fill opacity: 21 values");
		assertEquals(0, Slider.dotCentres(SliderModel.ofInt(0, 100, 5), 100, TRACK).length, "more than 15 values: none");
		assertEquals(0, Slider.dotCentres(SliderModel.ofInt(1, 15, 1), 100, 50).length, "40 GUI px for 14 gaps, under 4 apart: none");
		assertEquals(0, Slider.dotCentres(SliderModel.ofInt(3, 3, 1), 100, TRACK).length, "one value: none");
		assertEquals(4, SliderModel.ofInt(0, 10, 4).valueCount(), "0, 4, 8 and the maximum 10");
		// 15 values, 5 GUI px apart on average, but the last step (52 to 53) only 1.3: none.
		assertEquals(15, SliderModel.ofInt(0, 53, 4).valueCount());
		assertEquals(0, Slider.dotCentres(SliderModel.ofInt(0, 53, 4), 100, TRACK).length, "a short last step: none");
	}

	/**
	 * With the knob at 10 and 12 GUI px, at the minimum, middle and maximum, the dots it touches (5 GUI px apart, 2 across)
	 * are left out: at 10 GUI px it reaches 6 GUI px from its centre, so its own value's dot and the next one's; at 12 it reaches
	 * 7, the same; a dot 10 GUI px away is drawn.
	 */
	@Test
	void theKnobNeverSitsOnADot() {
		SliderModel notice = SliderModel.ofInt(1, 15, 1);
		float[] dots = Slider.dotCentres(notice, 0, TRACK);
		long[][] expected = {{1, 2}, {7, 8, 9}, {14, 15}};
		long[] at = {1, 8, 15};
		for (int knob : new int[] {Slider.KNOB_SIZE, Slider.KNOB_GROWN}) {
			for (int k = 0; k < at.length; k++) {
				float centre = (float) (Slider.KNOB_SIZE / 2.0 + notice.fraction(at[k]) * (TRACK - Slider.KNOB_SIZE));
				java.util.List<Long> hidden = new java.util.ArrayList<>();
				for (int i = 0; i < dots.length; i++) {
					if (Slider.underKnob(dots[i], centre, knob)) {
						hidden.add(i + 1L);
					}
				}
				assertEquals(java.util.Arrays.stream(expected[k]).boxed().toList(), hidden, "knob " + knob + " at " + at[k]);
			}
		}
	}

	/** REQ-UI-59: no change, no click; a drag or a press on the track that changes the value, one click; keys none. */
	@Test
	void aReleaseClicksOnlyWhenTheValueChanged() {
		long[] value = {50};
		int[] clicks = {0};
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> value[0] = v, () -> { }, Long::toString, () -> clicks[0]++);
		// Track from x 0 to 110: the knob's centre runs from 5 to 105, one value per GUI px.
		slider.setBounds(0, 0, 110, 12);
		assertTrue(slider.press(55, 6, 0), "on the knob");
		slider.release(55, 6);
		assertEquals(0, clicks[0], "a press and release on the knob that changes nothing: no click");
		slider.press(55, 6, 0);
		slider.drag(70, 6);
		slider.release(70, 6);
		assertEquals(1, clicks[0], "a drag that changed it: one click");
		slider.press(20, 6, 0);
		slider.release(20, 6);
		assertEquals(2, clicks[0], "a press on the track that moved it: one click");
		slider.navKey(NavKey.RIGHT, false);
		slider.navKey(NavKey.LEFT, false);
		assertEquals(2, clicks[0], "the keys: none");
		// A stored value off the range (120, shown as 100): a drag that ends where it shows changes nothing shown, so no click.
		value[0] = 120;
		slider.press(105, 6, 0);
		slider.drag(95, 6);
		slider.drag(105, 6);
		slider.release(105, 6);
		assertEquals(2, clicks[0], "back where it showed: no click");
	}
}
