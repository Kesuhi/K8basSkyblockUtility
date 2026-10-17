package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.UiTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-HUD-15 [A] (REQ-HUD-08, T3.53), on a test clock: the hint panel shows from the first frame, fades from 4 s and is gone at
 * 4.3 s, or from the first drag, nudge or scale; it fades back in over 300 ms while the pointer rests on the card. The card
 * fades to 30 % over 150 ms while an element is dragged over it. Reset All asks for a second click within 3 s. With
 * Animations OFF every fade is instant.
 */
class HudToolbarTest {
	private final long[] now = {20_000};
	private final boolean[] motion = {true};
	private final HudToolbar toolbar = new HudToolbar(UiTime.of(() -> now[0], () -> motion[0]));

	@Test
	void theHintShowsFor4sThenFadesOver300Ms() {
		now[0] += 500;
		assertEquals(1F, toolbar.hintAlpha(false), "the first frame: shown");
		now[0] += 3_999;
		assertEquals(1F, toolbar.hintAlpha(false), "3.999 s");
		now[0] += 1;
		assertEquals(1F, toolbar.hintAlpha(false), "4 s: the fade starts");
		now[0] += 150;
		assertEquals(1F - 0.875F, toolbar.hintAlpha(false), 1e-4, "4.15 s: ease-out cubic halfway, 1 - 0.5³");
		now[0] += 150;
		assertEquals(0F, toolbar.hintAlpha(false), "4.3 s: gone");
		now[0] += 10_000;
		assertEquals(0F, toolbar.hintAlpha(false), "and stays gone");
	}

	@Test
	void theFirstDragNudgeOrScaleStartsTheFade() {
		toolbar.hintAlpha(false);
		now[0] += 1_000;
		toolbar.acted();
		assertEquals(1F, toolbar.hintAlpha(false), "at the drag: still shown");
		now[0] += 150;
		float half = toolbar.hintAlpha(false);
		assertTrue(half > 0F && half < 1F, "150 ms later: fading, " + half);
		now[0] += 150;
		assertEquals(0F, toolbar.hintAlpha(false), "300 ms later: gone");
		now[0] += 10_000;
		toolbar.acted();
		assertEquals(0F, toolbar.hintAlpha(false), "a later one changes nothing");
	}

	@Test
	void restingOnTheCardBringsItBackOver300Ms() {
		toolbar.hintAlpha(false);
		now[0] += 5_000;
		assertEquals(0F, toolbar.hintAlpha(false), "gone");
		assertEquals(0F, toolbar.hintAlpha(true), "the pointer reaches the card");
		now[0] += 150;
		float rising = toolbar.hintAlpha(true);
		assertEquals(0.875F, rising, 1e-4, "150 ms on the card");
		now[0] += 150;
		assertEquals(1F, toolbar.hintAlpha(true), "300 ms on the card: shown");
		now[0] += 5_000;
		assertEquals(1F, toolbar.hintAlpha(true), "and stays while it rests there");
		assertEquals(1F, toolbar.hintAlpha(false), "the pointer leaves");
		now[0] += 300;
		assertEquals(0F, toolbar.hintAlpha(false), "300 ms later: gone again");
	}

	@Test
	void theCardFadesTo30PercentDuringADragOverIt() {
		assertEquals(1F, toolbar.cardAlpha(false), "no drag");
		assertEquals(1F, toolbar.cardAlpha(true), "a drag reaches the card");
		now[0] += 75;
		assertEquals(1F - 0.7F * 0.875F, toolbar.cardAlpha(true), 1e-4, "75 ms");
		now[0] += 75;
		assertEquals(HudToolbar.CARD_DIMMED, toolbar.cardAlpha(true), 1e-6, "150 ms: 30 %");
		assertEquals(0.3F, HudToolbar.CARD_DIMMED, 1e-6);
		assertEquals(HudToolbar.CARD_DIMMED, toolbar.cardAlpha(false), 1e-6, "the drag ends");
		now[0] += 150;
		assertEquals(1F, toolbar.cardAlpha(false), "150 ms later: back");
	}

	@Test
	void withAnimationsOffEveryFadeIsInstant() {
		motion[0] = false;
		assertEquals(1F, toolbar.hintAlpha(false), "the first frame");
		toolbar.acted();
		assertEquals(0F, toolbar.hintAlpha(false), "the first drag: gone at once");
		assertEquals(1F, toolbar.hintAlpha(true), "on the card: back at once");
		assertEquals(0F, toolbar.hintAlpha(false), "off it: gone at once");
		assertEquals(HudToolbar.CARD_DIMMED, toolbar.cardAlpha(true), 1e-6, "a drag over the card: 30 % at once");
		assertEquals(1F, toolbar.cardAlpha(false), "and back at once");
	}

	@Test
	void resetAllAsksForASecondClickWithin3s() {
		assertFalse(toolbar.confirming(), "idle");
		assertFalse(toolbar.resetAllClicked(), "the first click does not reset");
		assertTrue(toolbar.confirming(), "it asks to confirm");
		now[0] += 2_999;
		assertTrue(toolbar.confirming(), "2.999 s: still asking");
		assertTrue(toolbar.resetAllClicked(), "a second click within 3 s resets");
		assertFalse(toolbar.confirming(), "and it is back");
		assertFalse(toolbar.resetAllClicked(), "the next click asks again");
		now[0] += 3_000;
		assertFalse(toolbar.confirming(), "3 s later: back");
		assertFalse(toolbar.resetAllClicked(), "so one click does not reset");
		assertTrue(toolbar.confirming(), "it asks again");
	}
}
