package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-17 (T2.2): ~150 ms eased transitions on a clock the tests control. */
class AnimatedTest {
	private final long[] now = {1000};

	/** AC-UI-40 (REQ-UI-39, T3.24): with motion off a value is at its end at 0 ms, also when motion goes off mid-way. */
	@Test
	void withMotionOffAValueIsAtItsEndAtOnce() {
		boolean[] motion = {false};
		Animated value = new Animated(0F, 150, UiTime.of(() -> now[0], () -> motion[0]));
		value.animateTo(1F);
		assertEquals(1F, value.value(), "at 0 ms");
		assertTrue(value.settled());
		motion[0] = true;
		value.animateTo(0F);
		now[0] += 50;
		assertTrue(value.value() > 0F && value.value() < 1F, "on: still moving");
		motion[0] = false;
		assertEquals(0F, value.value(), "switched off mid-way: at its end at once");
		assertTrue(value.settled());
	}

	/** EC-UI-23: a 500 ms hitch shows the value at the elapsed time, at its end and never past it. */
	@Test
	void aHitchLandsOnTheEnd() {
		Animated value = new Animated(0F, 150, () -> now[0]);
		value.animateTo(1F);
		now[0] += 500;
		assertEquals(1F, value.value());
		assertTrue(value.settled());
	}

	@Test
	void easeOutCubic() {
		assertEquals(0F, Easing.outCubic(0F));
		assertEquals(0.875F, Easing.outCubic(0.5F), 1e-6);
		assertEquals(1F, Easing.outCubic(1F));
		assertEquals(0F, Easing.outCubic(-1F), "clamped");
		assertEquals(1F, Easing.outCubic(2F), "clamped");
	}

	@Test
	void aValueEasesToItsTargetIn150Ms() {
		Animated value = new Animated(0F, 150, () -> now[0]);
		value.animateTo(1F);
		assertEquals(0F, value.value());
		assertFalse(value.settled());
		now[0] = 1075;
		assertEquals(0.875F, value.value(), 1e-6);
		value.animateTo(1F);
		now[0] = 1150;
		assertEquals(1F, value.value(), "the same target does not restart the animation");
		assertTrue(value.settled());
		now[0] = 5000;
		assertEquals(1F, value.value());
	}

	@Test
	void aNewTargetStartsFromWhereTheValueIs() {
		Animated value = new Animated(0F, 150, () -> now[0]);
		value.animateTo(1F);
		now[0] = 1075;
		value.animateTo(0F);
		assertEquals(0.875F, value.value(), 1e-6);
		now[0] = 1225;
		assertEquals(0F, value.value(), 1e-6);
	}

	@Test
	void snapsAndBadClocksAreSafe() {
		Animated instant = new Animated(0F, 0, () -> now[0]);
		instant.animateTo(1F);
		assertEquals(1F, instant.value(), "a duration of 0 snaps");
		Animated value = new Animated(0F, 150, () -> now[0]);
		value.animateTo(1F);
		now[0] = 500;
		assertEquals(0F, value.value(), "a clock that goes back gives the start, never NaN");
		value.snapTo(0.25F);
		assertEquals(0.25F, value.value());
		assertTrue(value.settled());
		assertEquals(0.25F, value.target());
	}
}
