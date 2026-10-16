package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Animated;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-35 (REQ-UI-34, T3.25): the toggle knob's slide and the slider's knob are drawn on the physical-pixel grid, at
 * multiples of 1/guiScale GUI px, not whole GUI px; at GUI scale 2 some positions lie on a half GUI px. The layout they
 * are hit-tested in stays whole GUI px.
 */
class SubPixelMotionTest {
	private static final int SCALE = 2;

	@Test
	void theToggleKnobSlidesOnThePhysicalGrid() {
		long[] now = {0};
		Animated knob = new Animated(0F, Animated.DEFAULT_DURATION_MS, () -> now[0]);
		knob.animateTo(1F);
		int halves = 0;
		for (; now[0] <= Animated.DEFAULT_DURATION_MS + 20; now[0] += 10) {
			float x = ToggleSwitch.knobX(100, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT, knob.value(), SCALE);
			assertOnGrid(x, "at " + now[0] + " ms");
			float exact = 100 + 2 + (ToggleSwitch.WIDTH - 4 - (ToggleSwitch.HEIGHT - 4)) * knob.value();
			assertTrue(Math.abs(x - exact) <= 0.5F / SCALE + 1e-4F, "within half a physical px of the eased position: " + x + " for " + exact);
			halves += isHalf(x) ? 1 : 0;
		}
		assertTrue(halves > 0, "some frames lie on a half GUI px");
		assertEquals(102F, ToggleSwitch.knobX(100, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT, 0F, SCALE), "off end");
		assertEquals(112F, ToggleSwitch.knobX(100, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT, 1F, SCALE), "on end");
	}

	@Test
	void theSliderKnobIsDrawnOnThePhysicalGrid() {
		long[] value = {0};
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> value[0] = v, () -> { }, Long::toString, () -> { });
		slider.setBounds(20, 0, 120, 16);
		int halves = 0;
		for (value[0] = 0; value[0] <= 100; value[0]++) {
			float x = slider.drawnKnobX(SCALE);
			float exact = 20 + value[0] / 100F * (120 - Slider.KNOB_SIZE);
			assertOnGrid(x, "value " + value[0]);
			assertTrue(Math.abs(x - exact) <= 0.5F / SCALE + 1e-4F, "value " + value[0] + ": " + x + " for " + exact);
			assertEquals(Math.round(exact), slider.knobX(), "value " + value[0] + ": the layout stays whole GUI px");
			halves += isHalf(x) ? 1 : 0;
		}
		assertTrue(halves > 0, "some values put the knob on a half GUI px");
	}

	/** A drag at half-GUI-px pointer positions moves the drawn knob on the grid; its layout stays whole GUI px. */
	@Test
	void aDraggedSliderKnobIsDrawnOnThePhysicalGrid() {
		long[] value = {0};
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> value[0] = v, () -> { }, Long::toString, () -> { });
		slider.setBounds(20, 0, 120, 16);
		assertTrue(slider.press(20 + Slider.KNOB_SIZE / 2.0, 8, 0));
		int halves = 0;
		for (double mouse = 20 + Slider.KNOB_SIZE / 2.0; mouse <= 140 - Slider.KNOB_SIZE / 2.0; mouse += 0.5) {
			slider.drag(mouse, 8);
			float x = slider.drawnKnobX(SCALE);
			float exact = 20 + value[0] / 100F * (120 - Slider.KNOB_SIZE);
			assertOnGrid(x, "pointer at " + mouse);
			assertTrue(Math.abs(x - exact) <= 0.5F / SCALE + 1e-4F, "pointer at " + mouse + ": " + x + " for " + exact);
			assertEquals(Math.round(exact), slider.knobX(), "pointer at " + mouse + ": the layout stays whole GUI px");
			halves += isHalf(x) ? 1 : 0;
		}
		slider.release(140, 8);
		assertEquals(100, value[0], "dragged to the end");
		assertTrue(halves > 0, "the drag puts the knob on a half GUI px");
	}

	private static void assertOnGrid(float x, String what) {
		float physical = x * SCALE;
		assertEquals(Math.round(physical), physical, 1e-4F, what + ": " + x + " is on the 1/" + SCALE + " grid");
	}

	private static boolean isHalf(float x) {
		return Math.abs(x - Math.round(x)) > 0.25F;
	}
}
