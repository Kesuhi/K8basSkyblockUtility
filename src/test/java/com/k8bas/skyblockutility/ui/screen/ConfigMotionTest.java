package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.Easing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-18 (T2.9): the open scale and the category slide-and-fade, on a clock the tests control. */
class ConfigMotionTest {
	private final long[] now = {1_000};
	private boolean enabled = true;
	private final ConfigMotion motion = new ConfigMotion(() -> now[0], () -> enabled);

	@Test
	void openScalesFrom090To100Over220Ms() {
		motion.open();
		motion.frame();
		assertEquals(0.90F, motion.scale());
		assertTrue(motion.animating());
		now[0] += 110;
		motion.frame();
		assertEquals(0.90F + 0.10F * Easing.outCubic(0.5F), motion.scale(), 1e-6);
		now[0] += 110;
		motion.frame();
		assertEquals(1F, motion.scale());
		assertFalse(motion.animating());
	}

	@Test
	void openStartsOnTheFirstFrameNotWhenArmed() {
		motion.open();
		now[0] += 500;
		motion.frame();
		assertEquals(0.90F, motion.scale(), "a slow first frame does not eat the animation");
	}

	@Test
	void openScaleStaysWithin090And100() {
		motion.open();
		motion.frame();
		for (int t = -100; t <= 1000; t++) {
			now[0] = 1_000 + t;
			motion.frame();
			assertTrue(motion.scale() >= 0.90F && motion.scale() <= 1F, "t " + t + ": " + motion.scale());
		}
		now[0] = 500;
		motion.frame();
		assertTrue(motion.scale() >= 0.90F && motion.scale() <= 1F, "a clock that went back");
	}

	@Test
	void valuesHoldBetweenFrames() {
		motion.open();
		motion.switchCategory(0, 2);
		motion.frame();
		now[0] += 100;
		assertEquals(0.90F, motion.scale(), "input sees the frame that was drawn");
		assertEquals(ConfigMotion.SLIDE_PX, motion.slideOffset());
		assertEquals(255, motion.veilAlpha());
	}

	@Test
	void switchSlidesAndFadesOver200Ms() {
		motion.switchCategory(0, 2);
		motion.frame();
		assertEquals(12, motion.slideOffset());
		assertEquals(255, motion.veilAlpha());
		now[0] += 40;
		motion.frame();
		assertEquals(6, motion.slideOffset(), "round(12 * 0.512)");
		assertEquals(131, motion.veilAlpha(), "round(255 * 0.512)");
		now[0] += 60;
		motion.frame();
		assertEquals(2, motion.slideOffset(), "round(12 * 0.125)");
		assertEquals(32, motion.veilAlpha());
		now[0] += 100;
		motion.frame();
		assertEquals(0, motion.slideOffset());
		assertEquals(0, motion.veilAlpha());
		assertFalse(motion.animating());
	}

	@Test
	void slideDirectionFollowsTabOrder() {
		motion.switchCategory(0, 2);
		motion.frame();
		assertTrue(motion.slideOffset() > 0, "a tab further down: the page rises into place");
		motion.switchCategory(2, 0);
		motion.frame();
		assertTrue(motion.slideOffset() < 0, "a tab further up: the page drops in");
		motion.finish();
		motion.switchCategory(1, 1);
		motion.frame();
		assertFalse(motion.animating(), "the same tab does not animate");
	}

	@Test
	void aSecondSwitchRestarts() {
		motion.switchCategory(0, 2);
		motion.frame();
		now[0] += 150;
		motion.switchCategory(2, 1);
		motion.frame();
		assertEquals(-ConfigMotion.SLIDE_PX, motion.slideOffset());
		assertEquals(255, motion.veilAlpha());
	}

	@Test
	void finishSettlesEverythingAtOnce() {
		motion.open();
		motion.switchCategory(0, 1);
		motion.frame();
		motion.finish();
		assertEquals(1F, motion.scale());
		assertEquals(0, motion.slideOffset());
		assertEquals(0, motion.veilAlpha());
		assertFalse(motion.animating());
		motion.frame();
		assertFalse(motion.animating(), "and stays settled");
	}

	@Test
	void motionOffNeverAnimates() {
		enabled = false;
		motion.open();
		motion.switchCategory(0, 2);
		motion.frame();
		assertEquals(1F, motion.scale());
		assertEquals(0, motion.slideOffset());
		assertEquals(0, motion.veilAlpha());
		assertFalse(motion.animating());
	}

	@Test
	void aSwitchDuringTheOpenRunsBoth() {
		motion.open();
		motion.frame();
		now[0] += 100;
		motion.switchCategory(0, 1);
		motion.frame();
		assertTrue(motion.scale() > 0.90F && motion.scale() < 1F);
		assertEquals(ConfigMotion.SLIDE_PX, motion.slideOffset());
		now[0] += 120;
		motion.frame();
		assertEquals(1F, motion.scale(), "the open ends at 220 ms");
		assertTrue(motion.slideOffset() > 0, "the switch, started later, still runs");
	}
}
