package com.k8bas.skyblockutility.module.npcsearch;

import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** AC-GLOW-13 [A] (T1.11). */
class FoundTitleGateTest {
	private static final BooleanSupplier IN_VIEW = () -> true;
	private static final BooleanSupplier BEHIND_A_WALL = () -> false;
	private static final BooleanSupplier NOT_ASKED = () -> fail("line of sight is checked last");

	private final FoundTitleGate gate = new FoundTitleGate();

	@Test
	void noTitleWithoutLineOfSight() {
		assertFalse(gate.allow("trinity", true, false, BEHIND_A_WALL));
		// Not used up: the title shows once the NPC comes into view.
		assertTrue(gate.allow("trinity", true, false, IN_VIEW));
	}

	@Test
	void exactlyOneTitlePerRuleAndServer() {
		assertTrue(gate.allow("trinity", true, false, IN_VIEW));
		assertFalse(gate.allow("trinity", true, false, NOT_ASKED));
		assertTrue(gate.allow("tomioka", true, false, IN_VIEW));
	}

	/** EC-GLOW-07: the same NPC in the next run shows its title again. */
	@Test
	void aLocationChangeShowsItAgain() {
		assertTrue(gate.allow("trinity", true, false, IN_VIEW));
		gate.reset();
		assertTrue(gate.allow("trinity", true, false, IN_VIEW));
	}

	@Test
	void toggleOffShowsNothing() {
		assertFalse(gate.allow("trinity", false, false, NOT_ASKED));
		assertTrue(gate.allow("trinity", true, false, IN_VIEW));
	}

	@Test
	void anInvisibleEntityShowsNothing() {
		assertFalse(gate.allow("trinity", true, true, NOT_ASKED));
		assertTrue(gate.allow("trinity", true, false, IN_VIEW));
	}
}
