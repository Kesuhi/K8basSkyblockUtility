package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-40 (REQ-UI-39, T3.24): one UI clock source, the time and whether motion is on. The game's follows the Animations
 * switch, and -Dk8bas.ui.motion=off (the gametests' default) turns motion off; a screen can draw on its own source, a test's,
 * and the one before is back afterwards.
 */
class UiClockTest {
	@TempDir
	Path dir;

	/** The game's source reads the loaded config's switch each time (the unit tests run without the property). */
	@Test
	void theGameSourceReadsTheAnimationsSwitch() {
		ConfigManager.load(dir.resolve("clock.json"));
		Boolean before = ConfigManager.general().animations;
		try {
			assertTrue(UiClock.GAME.motion(), "absent: ON");
			ConfigManager.general().animations = false;
			assertFalse(UiClock.GAME.motion(), "switched OFF: no motion, at once");
			ConfigManager.general().animations = true;
			assertTrue(UiClock.GAME.motion(), "back ON");
		} finally {
			ConfigManager.general().animations = before;
		}
	}

	@Test
	void theGameSourceFollowsTheSwitchAndThePropertyTurnsItOff() {
		boolean[] animations = {true};
		long[] now = {1234};
		assertTrue(UiClock.game(null, () -> animations[0], () -> now[0]).motion(), "switch ON, no property: motion");
		assertFalse(UiClock.game("off", () -> animations[0], () -> now[0]).motion(), "the property turns it off");
		animations[0] = false;
		assertFalse(UiClock.game(null, () -> animations[0], () -> now[0]).motion(), "switch OFF: no motion");
		assertTrue(UiClock.game("on", () -> true, () -> now[0]).motion(), "any other value leaves it on");
		assertEquals(1234, UiClock.game(null, () -> true, () -> now[0]).millis(), "the time comes from the clock");
	}

	/** A screen on a test's source: everything reading the UI clock follows it, and the source before is back after. */
	@Test
	void aScreenDrawsOnItsOwnSource() {
		UiTime test = UiTime.of(() -> 42, () -> true);
		UiTime before = UiClock.current();
		UiTime previous = UiClock.enter(test);
		try {
			assertSame(before, previous);
			assertEquals(42, UiClock.SOURCE.millis());
			assertEquals(42, UiClock.MILLIS.getAsLong());
			assertTrue(UiClock.SOURCE.motion());
		} finally {
			UiClock.leave(previous);
		}
		assertSame(before, UiClock.current(), "the source before is back");
	}
}
