package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EC-GS-14: the one number parser every reader and consumer shares. Values from the G1 captures:
 * tab-dwarven-mines.txt (`3,196,526`, `2.6k`, `1B`, `913k`, `68.2%` without the sign), tab-mineshaft.txt (`12`,
 * `13`) and sidebar-mineshaft.txt (`-8`) and sidebar-dungeon-hub.txt (`311,732,284` once joined). The Roman and
 * `12.4k` bestiary forms were not captured (UNVERIFIED, from SPEC REQ-BEST-17's examples).
 */
class SbNumbersTest {
	@Test
	void separatorsAndPlainNumbersAreExact() {
		assertEquals(new SbNumber(3_196_526, false), SbNumbers.parse("3,196,526"));
		assertEquals(new SbNumber(12, false), SbNumbers.parse("12"));
		assertEquals(new SbNumber(-8, false), SbNumbers.parse("-8"));
		assertEquals(new SbNumber(68.2, false), SbNumbers.parse("68.2"));
		assertEquals(new SbNumber(311_732_284, false), SbNumbers.parse(" 311,732,284 "));
		assertEquals(3_196_526L, SbNumbers.parse("3,196,526").exact());
	}

	@Test
	void abbreviatedValuesAreApproximateAndHaveNoExactCount() {
		SbNumber gems = SbNumbers.parse("12.4k");
		assertEquals(12_400, gems.getValue(), 1e-6);
		assertTrue(gems.getApproximate());
		assertNull(gems.exact(), "never an exact value guessed from an abbreviation");
		assertEquals(new SbNumber(2_600, true), SbNumbers.parse("2.6k"));
		assertEquals(new SbNumber(1_000_000_000, true), SbNumbers.parse("1B"));
		assertEquals(new SbNumber(913_000, true), SbNumbers.parse("913k"));
		assertEquals(new SbNumber(4_500_000, true), SbNumbers.parse("4.5M"));
		assertNull(SbNumbers.parse("1,2k"), "a comma is a thousands separator, never a decimal point");
		assertEquals(new SbNumber(1_234_000, true), SbNumbers.parse("1,234k"));
	}

	@Test
	void romanAndArabicTiersBothParse() {
		assertEquals(261, SbNumbers.parseLevel("CCLXI"));
		assertEquals(262, SbNumbers.parseLevel("262"));
		assertEquals(9, SbNumbers.parseLevel("IX"));
		assertEquals(4, SbNumbers.parseLevel("IV"));
		assertEquals(1, SbNumbers.parseLevel("I"));
		assertEquals(3999, SbNumbers.parseLevel("MMMCMXCIX"));
		assertNull(SbNumbers.parseLevel("IIII"), "not a canonical numeral");
		assertNull(SbNumbers.parseLevel("VX"));
		assertNull(SbNumbers.parseLevel("ix"), "Hypixel writes numerals in capitals");
		assertNull(SbNumbers.parseLevel(""));
		assertNull(SbNumbers.parseLevel("12.5"));
		assertEquals(new SbNumber(261, false), SbNumbers.parse("CCLXI"));
	}

	@Test
	void anythingElseIsUnknownNotZero() {
		for (String text : new String[] {"MAX", "", "  ", "1,23", "12,3456", "k", "1.2.3", "Available", "100%", "1.5x", "NaN", "Infinity"}) {
			assertNull(SbNumbers.parse(text), text);
		}
		assertFalse(SbNumbers.parse("100").getApproximate());
	}
}
