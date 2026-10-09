package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** REQ-UI-18 (T2.9b): the Tab order's steps, the focus left after a control goes, and steps through a list. */
class FocusCycleTest {
	private final List<String> order = List.of("tabs", "search", "toggle", "slider");

	@Test
	void anEmptyOrderHasNoFocus() {
		assertNull(FocusCycle.step(List.<String>of(), null, true));
	}

	@Test
	void fromNothingForwardTakesTheFirstBackwardTheLast() {
		assertEquals("tabs", FocusCycle.step(order, null, true));
		assertEquals("slider", FocusCycle.step(order, null, false));
	}

	@Test
	void anUnknownCurrentCountsAsNothing() {
		assertEquals("tabs", FocusCycle.step(order, "gone", true));
	}

	@Test
	void forwardAndBackwardMoveOneAndWrap() {
		assertEquals("toggle", FocusCycle.step(order, "search", true));
		assertEquals("tabs", FocusCycle.step(order, "search", false));
		assertEquals("tabs", FocusCycle.step(order, "slider", true), "wraps at the end");
		assertEquals("slider", FocusCycle.step(order, "tabs", false), "and at the start");
	}

	@Test
	void aSingleControlStaysFocused() {
		assertEquals("only", FocusCycle.step(List.of("only"), "only", true));
	}

	@Test
	void comparesByIdentity() {
		String first = new String("same");
		String second = new String("same");
		List<String> twins = List.of(first, second);
		assertSame(second, FocusCycle.step(twins, first, true));
		assertSame(first, FocusCycle.step(twins, second, true));
	}

	/** A removed rule's header and Remove are gone: the focus moves on to the next rule's header, not its Remove. */
	@Test
	void theSurvivorIsTheNextStillThere() {
		List<String> before = List.of("tabs", "search", "H1", "R1", "H2", "R2");
		Set<String> after = Set.of("tabs", "search", "H2", "R2");
		assertEquals("H2", FocusCycle.survivor(before, 3, after::contains));
	}

	@Test
	void theSurvivorFallsBackToTheOneBefore() {
		List<String> before = List.of("tabs", "search", "H1", "R1");
		Set<String> after = Set.of("tabs", "search");
		assertEquals("search", FocusCycle.survivor(before, 3, after::contains));
	}

	@Test
	void noSurvivorWhenNothingIsLeft() {
		assertNull(FocusCycle.survivor(List.of("a", "b"), 1, value -> false));
	}

	@Test
	void listStepsClampAndJump() {
		assertEquals(0, FocusCycle.listStep(0, 5, NavKey.UP));
		assertEquals(4, FocusCycle.listStep(4, 5, NavKey.DOWN));
		assertEquals(3, FocusCycle.listStep(2, 5, NavKey.DOWN));
		assertEquals(1, FocusCycle.listStep(2, 5, NavKey.LEFT));
		assertEquals(0, FocusCycle.listStep(2, 5, NavKey.HOME));
		assertEquals(4, FocusCycle.listStep(2, 5, NavKey.END));
		assertEquals(4, FocusCycle.listStep(0, 5, NavKey.PAGE_DOWN));
		assertEquals(0, FocusCycle.listStep(4, 5, NavKey.PAGE_UP));
		assertEquals(0, FocusCycle.listStep(-1, 5, NavKey.DOWN), "nothing chosen: down starts at the first");
		assertEquals(0, FocusCycle.listStep(-1, 5, NavKey.UP));
		assertEquals(-1, FocusCycle.listStep(2, 0, NavKey.DOWN), "an empty list");
		assertEquals(-1, FocusCycle.listStep(2, 5, NavKey.ACTIVATE), "not a list key");
	}
}
