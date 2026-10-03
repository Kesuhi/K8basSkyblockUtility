package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** AC-UI-06 [A] and REQ-UI-06 (T2.3a): the slider's value model. */
class SliderModelTest {
	/** AC-UI-06: 3 wheel notches up at step 0.1 raise the value by 0.3. */
	@Test
	void threeNotchesAtATenthAddThreeTenths() {
		SliderModel model = SliderModel.ofDecimal(0.5, 3.0, 0.1, 2);
		long value = model.units(1.0);
		value = model.stepFrom(value, 1, false);
		value = model.stepFrom(value, 1, false);
		value = model.stepFrom(value, 1, false);
		assertEquals(1.3, model.toDouble(value));
		assertEquals(1.0 + 0.3, model.toDouble(value), 1e-9);
		assertEquals(1.29, model.toDouble(model.stepFrom(value, -1, true)), "Shift is the fine step, one hundredth");
	}

	@Test
	void stepsStayInRangeAndOnTheGrid() {
		SliderModel model = SliderModel.ofInt(0, 128, 10);
		assertEquals(128, model.stepFrom(125, 1, false), "clamped at the maximum, which is always reachable");
		assertEquals(0, model.stepFrom(5, -1, false));
		assertEquals(20, model.stepFrom(13, 1, false), "back on the grid of 10 from the minimum");
		assertEquals(14, model.stepFrom(13, 1, true), "Shift: one unit");
		SliderModel single = SliderModel.ofInt(0, 128, 1);
		assertEquals(65, single.stepFrom(64, 1, true), "with a step of 1 Shift changes nothing");
	}

	/** EC-UI-05: a stored value outside the range is shown clamped but not written back until changed. */
	@Test
	void anOutOfRangeValueIsOnlyShownClamped() {
		SliderModel model = SliderModel.ofInt(0, 150, 1);
		assertEquals(150, model.shown(200));
		assertEquals(149, model.stepFrom(200, -1, false), "a step starts from what is shown");
	}

	@Test
	void theTrackMapsToTheGrid() {
		SliderModel model = SliderModel.ofInt(0, 128, 10);
		assertEquals(0, model.fromTrack(0.0, false));
		assertEquals(128, model.fromTrack(1.0, false), "the end of the track is the maximum, also off the grid");
		assertEquals(60, model.fromTrack(0.5, false));
		assertEquals(64, model.fromTrack(0.5, true));
		assertEquals(0, model.fromTrack(-0.3, false), "a drag beyond the ends is clamped");
		assertEquals(128, model.fromTrack(1.7, false));
		assertEquals(0.5, model.fraction(64), 1e-9);
	}

	@Test
	void decimalsRoundTripExactly() {
		SliderModel model = SliderModel.ofDecimal(0.5, 3.0, 0.1, 2);
		assertEquals(130, model.units(1.3));
		assertEquals(1.3, model.toDouble(130));
		assertEquals("1.30", model.format(130));
		assertEquals("64", SliderModel.ofInt(0, 128, 1).format(64));
	}
}
