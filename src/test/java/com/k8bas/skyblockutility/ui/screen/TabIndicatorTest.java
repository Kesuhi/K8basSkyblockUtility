package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.UiTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-46 [A] (REQ-UI-45, T3.35): the selected tab's highlight slides from the old tab to the new one over 150 ms; AC-UI-35
 * (REQ-UI-34): it is drawn on the physical-pixel grid.
 */
class TabIndicatorTest {
	private final long[] now = {5_000};
	private final boolean[] motion = {true};
	private final UiTime time = UiTime.of(() -> now[0], () -> motion[0]);

	@Test
	void itSlidesToTheNewTabIn150Ms() {
		TabIndicator indicator = new TabIndicator(time);
		indicator.jumpTo(1);
		assertEquals(1F, indicator.position(), "a jump is there at once");
		indicator.slideTo(4);
		assertEquals(1F, indicator.position(), 1e-6, "at the switch, still on the old tab");
		now[0] += 75;
		float halfway = indicator.position();
		assertTrue(halfway > 1F && halfway < 4F, "75 ms after the switch, strictly between: " + halfway);
		now[0] += 75;
		assertEquals(4F, indicator.position(), 1e-6, "on the new tab at 150 ms");
		assertEquals(4, indicator.target());
	}

	@Test
	void aSwitchDuringASlideGoesOnFromWhereItIs() {
		TabIndicator indicator = new TabIndicator(time);
		indicator.jumpTo(0);
		indicator.slideTo(4);
		now[0] += 50;
		float there = indicator.position();
		indicator.slideTo(2);
		assertEquals(there, indicator.position(), 1e-6, "no jump");
		now[0] += 150;
		assertEquals(2F, indicator.position(), 1e-6);
	}

	@Test
	void withAnimationsOffItIsThereAtOnce() {
		motion[0] = false;
		TabIndicator indicator = new TabIndicator(time);
		indicator.jumpTo(0);
		indicator.slideTo(3);
		assertEquals(3F, indicator.position());
	}

	/** AC-UI-35: stepped every 10 ms at GUI scale 2, its drawn top is a multiple of 0.5 GUI px, and at least once a half one. */
	@Test
	void itIsDrawnOnThePhysicalPixelGrid() {
		TabIndicator indicator = new TabIndicator(time);
		indicator.jumpTo(0);
		indicator.slideTo(5);
		int pitch = ConfigLayout.TAB + ConfigLayout.TAB_GAP;
		boolean half = false;
		for (int step = 0; step <= 15; step++) {
			float top = indicator.drawnTop(100, pitch, 2);
			assertEquals(0F, top * 2 - Math.round(top * 2), 1e-4, "on the grid at " + step * 10 + " ms: " + top);
			half |= Math.abs(top - Math.floor(top) - 0.5F) < 1e-4;
			now[0] += 10;
		}
		assertTrue(half, "at least one frame on a half GUI px");
		assertEquals(100F + 5 * pitch, indicator.drawnTop(100, pitch, 2), 1e-6, "on the new tab at the end");
	}
}
