package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-18 (T2.9b): the keyboard focus apart from typing: the ring, overlays, held keys and the Esc order. */
class KeyboardNavTest {
	private final KeyboardNav<String> nav = new KeyboardNav<>();
	private final List<String> order = List.of("tabs", "search", "toggle", "slider");

	@Test
	void tabFromNothingFocusesTheFirstAndShowsTheRing() {
		assertEquals("tabs", nav.navigate(order, true));
		assertEquals("tabs", nav.getFocus());
		assertTrue(nav.getRingVisible());
	}

	@Test
	void aClickHidesTheRingAndTabGoesOnFromTheClickedControl() {
		nav.navigate(order, true);
		nav.pointer("search", false);
		assertFalse(nav.getRingVisible());
		assertEquals("toggle", nav.navigate(order, true));
		assertTrue(nav.getRingVisible());
	}

	@Test
	void anOverlayParksTheFocusAndClosingItBringsItBack() {
		nav.navigate(order, true);
		nav.overlayOpened();
		assertNull(nav.getFocus(), "nothing in the base layer while the overlay is open");
		nav.overlayClosed();
		assertEquals("tabs", nav.getFocus());
		// A click that opened the overlay parks that control.
		nav.pointer("slider", true);
		nav.overlayOpened();
		nav.overlayClosed();
		assertEquals("slider", nav.getFocus());
	}

	@Test
	void aHeldActivationKeyIsSwallowedUntilItsRelease() {
		nav.activated(257, false);
		assertTrue(nav.isActivationRepeat(257));
		assertFalse(nav.isActivationRepeat(32), "another key is not");
		assertTrue(nav.released(257));
		assertFalse(nav.isActivationRepeat(257));
		assertFalse(nav.released(257), "released once");
	}

	@Test
	void theSpaceCharAfterASpaceActivationIsSwallowedOnce() {
		nav.activated(32, true);
		assertTrue(nav.takeSpaceChar(' '));
		assertFalse(nav.takeSpaceChar(' '));
		nav.activated(257, false);
		assertFalse(nav.takeSpaceChar(' '), "Enter types no space");
	}

	@Test
	void noRingOnTheControlThatIsTyping() {
		nav.navigate(order, true);
		nav.navigate(order, true);
		assertNull(nav.ringTarget("search", false), "the field draws its own outline");
		assertEquals("search", nav.ringTarget(null, false));
	}

	@Test
	void theRingIsOnlyInTheLayerThatHasTheFocus() {
		nav.navigate(order, true);
		assertNull(nav.ringTarget(null, true), "an overlay is open, the focus is in the base layer");
		nav.setFocusInOverlay(true);
		assertEquals("tabs", nav.ringTarget(null, true));
	}

	@Test
	void repairUsesTheSnapshotsSurvivor() {
		List<String> rules = List.of("tabs", "H1", "R1", "H2", "R2");
		nav.navigate(rules, true);
		nav.navigate(rules, true);
		nav.navigate(rules, true);
		assertEquals("R1", nav.getFocus());
		Set<String> after = Set.of("tabs", "H2", "R2");
		nav.repair(after::contains);
		assertEquals("H2", nav.getFocus(), "the next rule's header, never its Remove");
		nav.repair(after::contains);
		assertEquals("H2", nav.getFocus(), "a present focus stays");
	}

	@Test
	void clearForgetsEverything() {
		nav.navigate(order, true);
		nav.activated(32, true);
		nav.clear();
		assertNull(nav.getFocus());
		assertFalse(nav.getRingVisible());
		assertFalse(nav.isActivationRepeat(32));
		assertFalse(nav.takeSpaceChar(' '));
	}
}
