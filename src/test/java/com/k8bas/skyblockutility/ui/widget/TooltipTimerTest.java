package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UI-45 [A] (REQ-UI-44, EC-UI-22, EC-UI-26, T3.34): when a tooltip shows and how it fades in, on a test clock. */
class TooltipTimerTest {
	private final Object a = new Object();
	private final Object b = new Object();

	/** Over control {@code owner} at the anchor 10, 20, 40x16, the pointer moved there this frame or not. */
	private static boolean over(TooltipTimer timer, long now, Object owner, String text, boolean moved, boolean motion) {
		return timer.update(now, owner, text, 10, 20, 40, 16, moved, motion);
	}

	@Test
	void itShowsAfter400MsOfRestAndFadesInOver100() {
		TooltipTimer timer = new TooltipTimer();
		assertFalse(over(timer, 1_000, a, "A", true, true), "the pointer just arrived");
		assertFalse(over(timer, 1_399, a, "A", false, true), "399 ms of rest");
		assertTrue(over(timer, 1_400, a, "A", false, true), "400 ms");
		assertEquals(0F, timer.alpha(), 1e-6, "its fade starts");
		assertTrue(over(timer, 1_450, a, "A", true, true), "moving over the same control keeps it");
		assertEquals(0.5F, timer.alpha(), 1e-6, "half way");
		assertTrue(over(timer, 1_500, a, "A", false, true));
		assertEquals(1F, timer.alpha(), 1e-6, "full at 500 ms");
		assertTrue(over(timer, 2_000, a, "A", false, true));
		assertEquals(1F, timer.alpha(), 1e-6);
	}

	@Test
	void withAnimationsOffTheFadeIsInstantAndTheDelayStays() {
		TooltipTimer timer = new TooltipTimer();
		assertFalse(over(timer, 0, a, "A", true, false));
		assertFalse(over(timer, 399, a, "A", false, false), "the delay stays");
		assertTrue(over(timer, 400, a, "A", false, false));
		assertEquals(1F, timer.alpha(), 1e-6, "no fade");
	}

	@Test
	void theNextControlWithin300MsShowsAtOnceStillWithTheFade() {
		TooltipTimer timer = new TooltipTimer();
		over(timer, 0, a, "A", true, true);
		assertTrue(over(timer, 600, a, "A", false, true));
		assertTrue(over(timer, 620, b, "B", true, true), "moved straight to another control");
		assertEquals(0F, timer.alpha(), 1e-6, "with the fade");
		assertTrue(over(timer, 720, b, "B", false, true));
		assertEquals(1F, timer.alpha(), 1e-6);
		// Off every control for 300 ms, then onto one: still at once.
		assertFalse(over(timer, 800, null, "", true, true), "no control under the pointer");
		assertFalse(over(timer, 1_000, null, "", true, true));
		assertTrue(over(timer, 1_020, a, "A", true, true), "300 ms after the last one showed");
		// 301 ms later the delay counts again.
		over(timer, 1_100, null, "", true, true);
		assertFalse(over(timer, 1_321, b, "B", true, true), "301 ms after the last one showed");
		assertFalse(over(timer, 1_720, b, "B", false, true), "399 ms of rest");
		assertTrue(over(timer, 1_721, b, "B", false, true), "400 ms of rest");
	}

	@Test
	void noTooltipWhileAPressIsHeldAndAfterTheReleaseTheDelayStartsAgain() {
		TooltipTimer timer = new TooltipTimer();
		over(timer, 0, a, "A", true, true);
		assertTrue(over(timer, 500, a, "A", false, true));
		// The screen passes no control while a press is held, however long it rests.
		for (long now = 510; now <= 1_500; now += 10) {
			assertFalse(over(timer, now, null, "", false, true), "held at " + now);
		}
		timer.restart();
		assertFalse(over(timer, 1_510, a, "A", true, true), "after the release the delay starts again, not at once");
		assertFalse(over(timer, 1_909, a, "A", false, true), "399 ms after the release");
		assertTrue(over(timer, 1_910, a, "A", false, true), "400 ms after the release");
		// A short press, released with a move 100 ms after the tooltip showed: without the restart it would show at once.
		for (long now = 1_920; now <= 2_000; now += 10) {
			assertFalse(over(timer, now, null, "", false, true), "held at " + now);
		}
		timer.restart();
		assertFalse(over(timer, 2_010, a, "A", true, true), "a short press: after its release the delay starts again too");
		assertTrue(over(timer, 2_410, a, "A", false, true));
	}

	@Test
	void backOnTheSameControlAfterLeavingItWaitsTheDelay() {
		TooltipTimer timer = new TooltipTimer();
		over(timer, 0, a, "A", true, true);
		assertTrue(over(timer, 400, a, "A", false, true));
		assertFalse(over(timer, 410, null, "", true, true), "off every control");
		assertFalse(over(timer, 910, a, "A", true, true), "back on the same control 500 ms later: the delay again");
		assertFalse(over(timer, 1_309, a, "A", false, true));
		assertTrue(over(timer, 1_310, a, "A", false, true));
		assertEquals(0F, timer.alpha(), 1e-6, "and the fade from its start");
	}

	@Test
	void aFrameAfterTheDelaysEndFadesFromThatEnd() {
		TooltipTimer timer = new TooltipTimer();
		over(timer, 0, a, "A", true, true);
		assertFalse(over(timer, 399, a, "A", false, true));
		assertTrue(over(timer, 450, a, "A", false, true), "no frame at 400 ms");
		assertEquals(0.5F, timer.alpha(), 1e-6, "50 ms into the fade, which began at 400 ms");
	}

	@Test
	void anotherTextOnTheSameControlStartsAgain() {
		TooltipTimer timer = new TooltipTimer();
		over(timer, 0, a, "A", true, true);
		assertTrue(over(timer, 400, a, "A", false, true));
		assertFalse(over(timer, 410, a, "A changed", false, true), "its text changed under the still pointer");
		assertTrue(over(timer, 810, a, "A changed", false, true));
	}

	@Test
	void aRestartBeforeTheNextControlCancelsTheAtOnce() {
		TooltipTimer timer = new TooltipTimer();
		over(timer, 0, a, "A", true, true);
		assertTrue(over(timer, 500, a, "A", false, true));
		// The wheel turned (or an overlay opened) while it showed: it hides and the delay starts again (EC-UI-26).
		timer.restart();
		assertFalse(over(timer, 510, b, "B", true, true), "within 300 ms, but after a restart");
		assertTrue(over(timer, 910, b, "B", false, true));
	}

	@Test
	void aControlThatChangesUnderAStillPointerWaitsTheWholeDelay() {
		TooltipTimer timer = new TooltipTimer();
		over(timer, 0, a, "A", true, true);
		assertTrue(over(timer, 500, a, "A", false, true));
		// The page glided: another control came under the pointer, which did not move (EC-UI-26).
		assertFalse(over(timer, 520, b, "B", false, true), "not at once");
		assertFalse(over(timer, 919, b, "B", false, true));
		assertTrue(over(timer, 920, b, "B", false, true));
	}

	@Test
	void theSameControlScrolledToAnotherPlaceStartsAgain() {
		TooltipTimer timer = new TooltipTimer();
		assertFalse(timer.update(0, a, "A", 10, 20, 40, 16, true, true));
		assertTrue(timer.update(500, a, "A", 10, 20, 40, 16, false, true));
		assertFalse(timer.update(510, a, "A", 10, 8, 40, 16, false, true), "scrolled under the still pointer: it hides");
		assertFalse(timer.update(909, a, "A", 10, 8, 40, 16, false, true));
		assertTrue(timer.update(910, a, "A", 10, 8, 40, 16, false, true), "and shows after the whole delay");
		// A list's row: the same list, another row's text at another place.
		assertTrue(timer.update(920, a, "A", 10, 8, 40, 16, false, true));
		assertTrue(timer.update(930, a, "Row 2", 10, 26, 40, 16, true, true), "the pointer moved to the next row: at once");
	}
}
