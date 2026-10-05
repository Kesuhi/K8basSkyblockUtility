package com.k8bas.skyblockutility.ui.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R31: a double click on a rule's Remove removes that rule only, not the one that slides under the cursor next. */
class RepeatGuardTest {
	private long now = 1_000;
	private final RepeatGuard guard = new RepeatGuard(500, () -> now);

	@Test
	void theFirstActionPasses() {
		assertTrue(guard.allow());
	}

	@Test
	void aSecondActionWithinTheWindowIsDropped() {
		assertTrue(guard.allow());
		now += 120;
		assertFalse(guard.allow(), "the second click of a double click");
		now += 379;
		assertFalse(guard.allow(), "still inside 500 ms of the first");
	}

	@Test
	void anActionAfterTheWindowPassesAgain() {
		assertTrue(guard.allow());
		now += 500;
		assertTrue(guard.allow());
		now += 499;
		assertFalse(guard.allow(), "the window starts again from the last action that passed");
	}

	@Test
	void aClockAtItsLowestValueStillWorks() {
		now = Long.MIN_VALUE;
		assertTrue(guard.allow(), "no overflow from the 'never' start");
		now += 10;
		assertFalse(guard.allow());
	}
}
