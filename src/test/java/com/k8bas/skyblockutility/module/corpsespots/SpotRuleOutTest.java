package com.k8bas.skyblockutility.module.corpsespots;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ruling out corpse spots by the Organ Donor's silence (T3.3e, REQ-CORPSE-12, R44): AC-CORPSE-13 [A]. The ding plays
 * for an unlooted corpse within 20 blocks, so once it is known to work, 4 s without a ding within 16 blocks of a spot
 * means no unlooted corpse is there.
 */
class SpotRuleOutTest {
	/** #1 next to the player at the origin, #2 15 blocks away (inside 16), #3 30 blocks away. Centres at +0.5. */
	private static final List<CorpseSpot> SPOTS = List.of(new CorpseSpot(1, 1, 0, 0), new CorpseSpot(2, 15, 0, 0), new CorpseSpot(3, 30, 0, 0));
	private static final double[] ORIGIN = {0.5, 0.5, 0.5};

	/** The player stands at [at] from [from] to [to] ms, one tick every 50 ms. */
	private static void stay(SpotRuleOut ruleOut, double[] at, long from, long to) {
		for (long t = from; t <= to; t += 50) {
			ruleOut.tick(t, at, SPOTS, 2);
		}
	}

	@Test
	void nothingIsRuledOutBeforeTheFirstDing() {
		SpotRuleOut ruleOut = new SpotRuleOut();
		stay(ruleOut, ORIGIN, 0, 10_000);
		assertEquals(Set.of(), ruleOut.ruledOut(), "an Organ Donor not known to work rules nothing out");
	}

	@Test
	void fourSecondsOfSilenceRuleOutTheSpotsWithin16Blocks() {
		SpotRuleOut ruleOut = new SpotRuleOut();
		ruleOut.ding(0);
		stay(ruleOut, ORIGIN, 0, 3_900);
		assertEquals(Set.of(), ruleOut.ruledOut(), "3.9 s is not enough");
		stay(ruleOut, ORIGIN, 3_950, 4_000);
		assertEquals(Set.of(1, 2), ruleOut.ruledOut(), "#1 and #2 (15.0 blocks); #3 is 30 blocks away");
	}

	@Test
	void aSpotJustOutsideTheMarginStays() {
		SpotRuleOut ruleOut = new SpotRuleOut();
		ruleOut.ding(0);
		// #2's centre is 16.5 blocks from here.
		stay(ruleOut, new double[] {-1.0, 0.5, 0.5}, 0, 4_000);
		assertEquals(Set.of(1), ruleOut.ruledOut());
	}

	@Test
	void aDingInsideTheWindowStartsItAgain() {
		SpotRuleOut ruleOut = new SpotRuleOut();
		ruleOut.ding(0);
		stay(ruleOut, ORIGIN, 0, 2_000);
		ruleOut.ding(2_000);
		stay(ruleOut, ORIGIN, 2_050, 5_900);
		assertEquals(Set.of(), ruleOut.ruledOut(), "only 3.9 s since the last ding");
		stay(ruleOut, ORIGIN, 5_950, 6_000);
		assertEquals(Set.of(1, 2), ruleOut.ruledOut());
	}

	@Test
	void theWholeWindowMustBeWithinTheMargin() {
		SpotRuleOut ruleOut = new SpotRuleOut();
		ruleOut.ding(0);
		// 2 s at 30 blocks (next to #3), then 2 s at the origin: #1 was never within 16 blocks for 4 s, nor was #3.
		stay(ruleOut, new double[] {30.5, 0.5, 0.5}, 50, 2_000);
		stay(ruleOut, ORIGIN, 2_050, 4_050);
		assertEquals(Set.of(2), ruleOut.ruledOut(), "only #2 (15 blocks from both) was within 16 blocks the whole time");
	}

	@Test
	void aPauseInTheSamplesIsNotSilence() {
		// The option off (or a frozen client) from 500 to 3 500 ms while dings may have played: only samples taken back to
		// back count, so the window starts again at 3 500.
		SpotRuleOut ruleOut = new SpotRuleOut();
		ruleOut.ding(0);
		stay(ruleOut, ORIGIN, 0, 500);
		stay(ruleOut, ORIGIN, 3_500, 7_400);
		assertEquals(Set.of(), ruleOut.ruledOut(), "3.9 s of samples since the pause");
		stay(ruleOut, ORIGIN, 7_450, 7_450);
		assertEquals(Set.of(1, 2), ruleOut.ruledOut());
	}

	@Test
	void moreCorpsesOrANewShaftBringEveryoneBack() {
		SpotRuleOut ruleOut = new SpotRuleOut();
		ruleOut.ding(0);
		stay(ruleOut, ORIGIN, 0, 4_000);
		assertEquals(Set.of(1, 2), ruleOut.ruledOut());
		ruleOut.tick(4_050, ORIGIN, SPOTS, 2);
		assertEquals(Set.of(1, 2), ruleOut.ruledOut(), "the same corpse count keeps them out");
		ruleOut.tick(4_100, ORIGIN, SPOTS, 0);
		assertEquals(Set.of(1, 2), ruleOut.ruledOut(), "the widget gone for a moment is not fewer corpses");
		ruleOut.tick(4_150, ORIGIN, SPOTS, 3);
		assertEquals(Set.of(), ruleOut.ruledOut(), "a Dead Man's Chest corpse: all back");
		ruleOut.ding(4_150);
		stay(ruleOut, ORIGIN, 4_200, 8_200);
		assertEquals(Set.of(1, 2), ruleOut.ruledOut());
		ruleOut.reset();
		assertEquals(Set.of(), ruleOut.ruledOut(), "another shaft");
		stay(ruleOut, ORIGIN, 8_250, 15_000);
		assertEquals(Set.of(), ruleOut.ruledOut(), "and the Organ Donor must ding again there first");
	}
}
