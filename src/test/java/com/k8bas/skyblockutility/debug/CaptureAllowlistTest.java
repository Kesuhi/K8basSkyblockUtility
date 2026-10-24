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
		// The Bestiary pages T5.2b reads, so a field session can dump them (REQ-GS-14).
		assertTrue(CaptureAllowlist.matches("Critter Safari ➜ Icy Biome"));
		assertTrue(CaptureAllowlist.matches("(1/2) Bestiary ➜ The Catacombs"));
		assertFalse(CaptureAllowlist.matches("Pets ➜ Cats"));
		assertFalse(CaptureAllowlist.matches("Profile ➜ Kesuhi"));
	}

	@Test
	void dungeonRewardsAndRngMeters() {
		assertTrue(CaptureAllowlist.matches("Croesus"));
		assertTrue(CaptureAllowlist.matches("Catacombs - Floor I"), "a run's chests from Croesus, as captured at G3");
		assertTrue(CaptureAllowlist.matches("Bedrock Chest"));
		assertTrue(CaptureAllowlist.matches("The Catacombs - Floor VII"));
		assertTrue(CaptureAllowlist.matches("Master Mode The Catacombs - Floor III"));
		assertTrue(CaptureAllowlist.matches("Catacombs RNG Meter"));
		assertTrue(CaptureAllowlist.matches("Slayer RNG Meter"));
		assertFalse(CaptureAllowlist.matches("Large Chest"));
		assertFalse(CaptureAllowlist.matches("Chest"));
	}

	@Test
	void sackMenusMatch() {
		// The corpse keys in sacks (T3.2b, R39): the sack overview and each sack. "Dwarven Mines Sack" is the captured
		// title (G3, fixtures/captures/g3-2026-10-15/containers/dwarven-mines-sack-*.txt); the others are UNVERIFIED.
		for (String title : new String[]{"Dwarven Mines Sack", "Sacks", "Dwarven Sack", "Mining Sack", "Enchanted Mining Sack", "Large Dwarven Sack"}) {
			assertTrue(CaptureAllowlist.matches(title), title);
		}
		assertFalse(CaptureAllowlist.matches("Sack of Sacks ➜ Upgrades"));
		assertFalse(CaptureAllowlist.matches("Backpack"));
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
