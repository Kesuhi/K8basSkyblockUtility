package com.k8bas.skyblockutility.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureAllowlistTest {
	@Test
	void levelingAndComponentMenusMatch() {
		for (String title : new String[]{"SkyBlock Leveling", "Ways to Level Up", "Skill Related Tasks", "Your Skills",
				"Collections", "Your Museum", "Heart of the Mountain", "Heart of the Forest", "Pets", "Accessory Bag"}) {
			assertTrue(CaptureAllowlist.matches(title), title);
		}
	}

	@Test
	void submenusOnlyUnderAllowedParents() {
		assertTrue(CaptureAllowlist.matches("Tasks ➜ Core"));
		assertTrue(CaptureAllowlist.matches("Essence Shop ➜ Undead"));
		assertTrue(CaptureAllowlist.matches("Bestiary ➜ The Park"));
		assertTrue(CaptureAllowlist.matches("Fishing ➜ Water"));
		assertFalse(CaptureAllowlist.matches("Pets ➜ Cats"));
		assertFalse(CaptureAllowlist.matches("Profile ➜ Kesuhi"));
	}

	@Test
	void dungeonRewardsAndRngMeters() {
		assertTrue(CaptureAllowlist.matches("Croesus"));
		assertTrue(CaptureAllowlist.matches("Bedrock Chest"));
		assertTrue(CaptureAllowlist.matches("The Catacombs - Floor VII"));
		assertTrue(CaptureAllowlist.matches("Master Mode The Catacombs - Floor III"));
		assertTrue(CaptureAllowlist.matches("Catacombs RNG Meter"));
		assertTrue(CaptureAllowlist.matches("Slayer RNG Meter"));
		assertFalse(CaptureAllowlist.matches("Large Chest"));
		assertFalse(CaptureAllowlist.matches("Chest"));
	}

	@Test
	void unrelatedMenusDoNotMatch() {
		for (String title : new String[]{"Auction House", "Bazaar", "Your Bank", "Ender Chest", "SkyBlock Menu", "Trades", ""}) {
			assertFalse(CaptureAllowlist.matches(title), title);
		}
	}

	@Test
	void colourCodesSpacesAndPageSuffixesAreIgnored() {
		assertTrue(CaptureAllowlist.matches("§8Pets (1/3)"));
		assertTrue(CaptureAllowlist.matches("(2/2) Accessory Bag"));
		assertTrue(CaptureAllowlist.matches("  Croesus "));
		assertEquals("Accessory Bag", CaptureAllowlist.normalise("§rAccessory Bag (3/4)"));
	}
}
