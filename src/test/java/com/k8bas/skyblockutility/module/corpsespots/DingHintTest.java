package com.k8bas.skyblockutility.module.corpsespots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Organ Donor ding hint without a game (T3.3b, REQ-CORPSE-11, R41 (c)): AC-CORPSE-11 [A] with the values of the
 * maintainer's capture (a block-category harp at the player's own position; pitch 1.968 within 3 blocks of a corpse,
 * 1.73 at 10 blocks, 1.159 at 13–17 blocks).
 */
class DingHintTest {
	private static final String HARP = "minecraft:block.note_block.harp";
	private static final double[] PLAYER = {-106.53, 4.25, -195.85};

	private static boolean ding(DingHint hint, String id, String source, double x, double y, double z, float pitch, boolean inShaft, long now) {
		return hint.heard(id, source, x, y, z, pitch, PLAYER, inShaft, now);
	}

	@Test
	void theWarmthComesFromThePitch() {
		assertEquals(DingHint.Warmth.VERY_CLOSE, DingHint.warmth(1.968f));
		assertEquals(DingHint.Warmth.VERY_CLOSE, DingHint.warmth(1.9f));
		assertEquals(DingHint.Warmth.CLOSE, DingHint.warmth(1.73f));
		assertEquals(DingHint.Warmth.CLOSE, DingHint.warmth(1.6f));
		assertEquals(DingHint.Warmth.FAR, DingHint.warmth(1.159f));
		assertEquals("very close", DingHint.Warmth.VERY_CLOSE.getLabel());
	}

	@Test
	void theHudLineNamesTheWarmth() {
		// R43 (b): the "Corpse ding" HUD element's line.
		assertEquals("§6Corpse ding: §every close", DingHint.hudLine(DingHint.Warmth.VERY_CLOSE));
		assertEquals("§6Corpse ding: §efar", DingHint.hudLine(DingHint.Warmth.FAR));
	}

	@Test
	void aDingAtThePlayerHoldsForTwoSeconds() {
		DingHint hint = new DingHint();
		assertNull(hint.warmth(0));
		assertEquals(true, ding(hint, HARP, "block", -106.50, 4.25, -195.75, 1.968f, true, 1_000));
		assertEquals(DingHint.Warmth.VERY_CLOSE, hint.warmth(1_000));
		assertEquals(DingHint.Warmth.VERY_CLOSE, hint.warmth(2_999));
		assertNull(hint.warmth(3_000), "cleared 2 s after the last ding");
		// The next ding counts from itself, with its own pitch.
		ding(hint, HARP, "block", -106.50, 4.25, -195.75, 1.73f, true, 3_500);
		assertEquals(DingHint.Warmth.CLOSE, hint.warmth(5_000));
	}

	@Test
	void onlyTheDingCounts() {
		DingHint hint = new DingHint();
		// 3.5 blocks off counts (the capture had dings up to 3.5 blocks from the player); 4.5 does not.
		assertEquals(true, ding(hint, HARP, "block", -106.53 + 3.5, 4.25, -195.85, 1.3f, true, 0));
		DingHint other = new DingHint();
		assertEquals(false, ding(other, HARP, "block", -106.53 + 4.5, 4.25, -195.85, 1.3f, true, 0), "too far from the player");
		assertEquals(false, ding(other, HARP, "record", -106.50, 4.25, -195.75, 1.3f, true, 0), "another category");
		assertEquals(false, ding(other, "minecraft:block.note_block.bell", "block", -106.50, 4.25, -195.75, 1.3f, true, 0), "another sound");
		assertEquals(false, ding(other, HARP, "block", -106.50, 4.25, -195.75, 1.3f, false, 0), "outside a shaft");
		assertNull(other.warmth(0));
	}

	@Test
	void theRuleOutTakesDingsFromFurtherAway() {
		// REQ-CORPSE-12: a ding the HUD misses (6 blocks off, e.g. while moving with latency) still stops a rule-out.
		double[] at = PLAYER;
		assertEquals(true, DingHint.isDing(HARP, "block", at[0] + 6, at[1], at[2], at, true, SpotRuleOut.DING_NEAR));
		assertEquals(false, DingHint.isDing(HARP, "block", at[0] + 6, at[1], at[2], at, true, DingHint.NEAR));
		assertEquals(false, DingHint.isDing(HARP, "block", at[0] + 9, at[1], at[2], at, true, SpotRuleOut.DING_NEAR));
		assertEquals(false, DingHint.isDing(HARP, "block", at[0], at[1], at[2], at, false, SpotRuleOut.DING_NEAR), "outside a shaft");
	}

	@Test
	void theWarmthIgnoresWhereTheDingWasPlayed() {
		// Two dings with the same pitch, one at the player and one 3 blocks off: the same warmth (R43: the HUD shows it).
		DingHint a = new DingHint();
		DingHint b = new DingHint();
		ding(a, HARP, "block", PLAYER[0], PLAYER[1], PLAYER[2], 1.73f, true, 0);
		ding(b, HARP, "block", PLAYER[0] + 3, PLAYER[1], PLAYER[2], 1.73f, true, 0);
		assertEquals(a.warmth(0), b.warmth(0));
	}

	@Test
	void leavingClearsIt() {
		DingHint hint = new DingHint();
		ding(hint, HARP, "block", -106.50, 4.25, -195.75, 1.968f, true, 0);
		hint.clear();
		assertNull(hint.warmth(0));
	}
}
