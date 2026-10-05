package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** REQ-UI-18 (T2.9b): which keys navigate the settings screen, and which never do. */
class NavKeyTest {
	@Test
	void tabIsNextAndShiftTabIsPrevious() {
		assertEquals(NavKey.NEXT, NavKey.of(258, false, false, false));
		assertEquals(NavKey.PREVIOUS, NavKey.of(258, true, false, false));
	}

	@Test
	void ctrlOrAltTabIsNotNavigation() {
		assertEquals(NavKey.NONE, NavKey.of(258, false, true, false));
		assertEquals(NavKey.NONE, NavKey.of(258, false, false, true));
	}

	@Test
	void spaceEnterAndKeypadEnterActivate() {
		assertEquals(NavKey.ACTIVATE, NavKey.of(32, false, false, false));
		assertEquals(NavKey.ACTIVATE, NavKey.of(257, false, false, false));
		assertEquals(NavKey.ACTIVATE, NavKey.of(335, false, false, false));
		assertEquals(NavKey.NONE, NavKey.of(257, false, true, false), "Ctrl+Enter");
	}

	@Test
	void arrowsHomeEndPagesDelete() {
		assertEquals(NavKey.RIGHT, NavKey.of(262, false, false, false));
		assertEquals(NavKey.LEFT, NavKey.of(263, false, false, false));
		assertEquals(NavKey.DOWN, NavKey.of(264, false, false, false));
		assertEquals(NavKey.UP, NavKey.of(265, false, false, false));
		assertEquals(NavKey.HOME, NavKey.of(268, false, false, false));
		assertEquals(NavKey.END, NavKey.of(269, false, false, false));
		assertEquals(NavKey.PAGE_UP, NavKey.of(266, false, false, false));
		assertEquals(NavKey.PAGE_DOWN, NavKey.of(267, false, false, false));
		assertEquals(NavKey.DELETE, NavKey.of(261, false, false, false));
	}

	/** Esc, E and F3 are never navigation, so the Esc order and AC-UI-05 stay as they are. */
	@Test
	void lettersEscapeAndFunctionKeysAreNone() {
		assertEquals(NavKey.NONE, NavKey.of(69, false, false, false));
		assertEquals(NavKey.NONE, NavKey.of(256, false, false, false));
		assertEquals(NavKey.NONE, NavKey.of(292, false, false, false));
	}
}
