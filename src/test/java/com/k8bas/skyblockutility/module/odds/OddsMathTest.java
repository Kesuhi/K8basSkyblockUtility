package com.k8bas.skyblockutility.module.odds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The odds math and its "1 in N (P%)" format (T3.9a): AC-ODDS-05 and AC-ODDS-07 [A], REQ-ODDS-06, REQ-ODDS-08. */
class OddsMathTest {
	@Test
	void atLeastOneSuccessExpectedAttemptsAndLuck() {
		// AC-ODDS-07: p = 0.004 over 250 and 500 attempts.
		assertEquals(0.633, OddsMath.atLeastOne(0.004, 250), 0.001);
		assertEquals(0.865, OddsMath.atLeastOne(0.004, 500), 0.001);
		assertEquals(250.0, OddsMath.expectedAttempts(0.004), 1e-9);
		assertEquals(OddsMath.atLeastOne(0.004, 312), OddsMath.luckPercentile(0.004, 312), 1e-12, "a drop on attempt k");
		assertEquals(0.0, OddsMath.atLeastOne(0.004, 0));
	}

	@Test
	void magicFindAndPetLuckScaleTheChance() {
		// AC-ODDS-07: Legendary 0.0004 with MF 100 + PL 50 is 0.001; MF 1,000 counts as 900; a 6 % base ignores MF.
		assertEquals(0.001, OddsMath.petChance(0.0004, 100, 50), 1e-12);
		assertEquals(OddsMath.dropChance(0.001, 900), OddsMath.dropChance(0.001, 1000), 1e-15);
		assertEquals(0.01, OddsMath.dropChance(0.001, 1000), 1e-12);
		assertEquals(0.06, OddsMath.dropChance(0.06, 300), 1e-12, "the < 5 % rule");
		assertEquals(0.0498 * 2, OddsMath.dropChance(0.0498, 100), 1e-12, "just under 5 % scales");
		assertEquals(0.05, OddsMath.dropChance(0.05, 100), 1e-12, "exactly 5 % does not");
		assertEquals(OddsMath.petChance(0.0004, 900, 0), OddsMath.petChance(0.0004, 1500, 0), 1e-15, "pets cap MF too");
	}

	@Test
	void perCorpseChanceIsTheMeanOverUniformRollCounts() {
		// AC-ODDS-07: Vanguard, 5 to 8 rolls, total weight 3,515.
		assertEquals(0.0183, OddsMath.perCorpse(10, 3515, 5, 8), 0.0001, "Caged Wisp");
		assertEquals(0.0092, OddsMath.perCorpse(5, 3515, 5, 8), 0.0001, "Shattered Locket");
		assertEquals(OddsMath.atLeastOne(0.5, 3), OddsMath.perCorpse(1, 2, 3, 3), 1e-12, "a fixed roll count");
		assertThrows(IllegalArgumentException.class, () -> OddsMath.perCorpse(1, 2, 4, 3));
	}

	@Test
	void theRngMeterBoostsUpToThreeTimesAndThenGuarantees() {
		// AC-ODDS-07: s/r = 0.5 gives x2; s/r >= 1 gives "guaranteed".
		assertEquals(2.0, OddsMath.meterBoost(500, 1000), 1e-12);
		assertEquals(1.0, OddsMath.meterBoost(0, 1000), 1e-12);
		assertEquals(3.0, OddsMath.meterBoost(999, 1000), 0.01);
		assertEquals(3.0, OddsMath.meterBoost(1000, 1000), 1e-12, "at most x3");
		assertEquals(3.0, OddsMath.meterBoost(1200, 1000), 1e-12, "a meter past full is still x3");
		assertFalse(OddsMath.meterGuaranteed(999, 1000));
		assertTrue(OddsMath.meterGuaranteed(1000, 1000));
		assertTrue(OddsMath.meterGuaranteed(1200, 1000));
	}

	@Test
	void slayerBaseChanceAndBossesUntilGuaranteed() {
		// AC-ODDS-07: Warden Heart (required 3,674,250) is 1 in 7,349 at base.
		double warden = OddsMath.slayerBase(3_674_250, false);
		assertEquals("1 in 7,349 (0.014%) base", OddsFormat.oneIn(warden, OddsFormat.Basis.BASE));
		assertEquals(OddsMath.slayerBase(1_000_000, false) / 2, OddsMath.slayerBase(1_000_000, true), 1e-15, "the Riftstalker's multiplier is 250");
		assertEquals(Integer.valueOf(3), OddsMath.bossesUntilGuaranteed(1000, 700, 120));
		assertEquals(Integer.valueOf(0), OddsMath.bossesUntilGuaranteed(1000, 1000, 120));
		assertNull(OddsMath.bossesUntilGuaranteed(1000, 700, 0), "no gain seen yet");
	}

	@Test
	void theFormatIsOneInNWithTwoSignificantDigits() {
		// AC-ODDS-05.
		assertEquals("1 in 1,389 (0.072%) per kill", OddsFormat.oneIn(1 / 1389.4, OddsFormat.Basis.PER_KILL));
		assertEquals("1 in 250 (0.40%) per roll", OddsFormat.oneIn(0.004, OddsFormat.Basis.PER_ROLL));
		assertEquals("1 in 55 (1.8%) per corpse", OddsFormat.oneIn(0.0183, OddsFormat.Basis.PER_CORPSE));
		assertEquals("1 in 1,000 (0.10%) with your MF/PL", OddsFormat.oneIn(0.001, OddsFormat.Basis.WITH_LUCK));
		assertEquals("1 in 2 (50%) relative", OddsFormat.oneIn(0.5, OddsFormat.Basis.RELATIVE));
		assertEquals("1 in 1 (100%) base", OddsFormat.oneIn(1.0, OddsFormat.Basis.BASE));
		assertEquals("1 in 12,345,679 (<0.0001%) base", OddsFormat.oneIn(1 / 12_345_678.9, OddsFormat.Basis.BASE), "below 0.0001 %");
		assertEquals("1 in 100,000 (0.0010%) base", OddsFormat.oneIn(0.00001, OddsFormat.Basis.BASE));
		// Two significant digits also for values exact in binary, and half up on the decimal value.
		assertEquals("1 in 100 (1.0%) with your MF/PL", OddsFormat.oneIn(OddsMath.petChance(0.004, 100, 50), OddsFormat.Basis.WITH_LUCK));
		assertEquals("1 in 50 (2.0%) per corpse", OddsFormat.oneIn(0.02, OddsFormat.Basis.PER_CORPSE));
		assertEquals("1 in 690 (0.15%) base", OddsFormat.oneIn(0.00145, OddsFormat.Basis.BASE));
		assertEquals("1 in 1,000,000 (0.00010%) base", OddsFormat.oneIn(1e-6, OddsFormat.Basis.BASE), "exactly the minimum");
		assertEquals("1 in 1 (100%) with your MF/PL", OddsFormat.oneIn(OddsMath.petChance(0.1, 900, 100), OddsFormat.Basis.WITH_LUCK),
				"a chance scaled past 1 is a certainty");
	}

	@Test
	void unverifiedRowsAndEstimatesAreMarked() {
		// REQ-ODDS-06: "?" for a verified: false row; "≈" and "base, no bonuses" for the Croesus run estimate (REQ-ODDS-19).
		assertEquals("1 in 250 (0.40%) per roll ?", OddsFormat.oneIn(0.004, OddsFormat.Basis.PER_ROLL, false));
		assertEquals("≈ 1 in 3,000 runs (0.033%, base, no bonuses)", OddsFormat.runs(3000));
		assertEquals("≈ 1 in 1 runs (100%, base, no bonuses)", OddsFormat.runs(0.4), "never below one run");
		assertEquals("≈ 1 in 1,000,000 runs (0.00010%, base, no bonuses)", OddsFormat.runs(1_000_000));
		assertEquals("guaranteed", OddsFormat.GUARANTEED);
		assertThrows(IllegalArgumentException.class, () -> OddsFormat.oneIn(0, OddsFormat.Basis.BASE), "no number for p = 0 (REQ-ODDS-11)");
		assertThrows(IllegalArgumentException.class, () -> OddsFormat.oneIn(Double.NaN, OddsFormat.Basis.BASE));
	}
}
