package com.k8bas.skyblockutility.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-HUD-02 (T2.7, R12): when HUD elements are drawn in game. */
class HudVisibilityTest {
	@Test
	void drawnInPlayAndOverChatOnly() {
		assertTrue(HudVisibility.drawn(false, HudVisibility.Screen.NONE, false));
		assertTrue(HudVisibility.drawn(false, HudVisibility.Screen.CHAT, false), "chat keeps them (R12)");
		assertFalse(HudVisibility.drawn(false, HudVisibility.Screen.OTHER, false), "any other screen hides them");
		assertFalse(HudVisibility.drawn(true, HudVisibility.Screen.NONE, false), "F1 hides them");
		assertFalse(HudVisibility.drawn(false, HudVisibility.Screen.NONE, true), "the editor draws them itself");
	}
}
