package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.location.Islands;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-NPCWP-01 and AC-NPCWP-08 [A] (T3.4): which colour a waypoint's beam gets. */
class WaypointColorsTest {
	private static NpcRule fixedRule(String island, Integer beamColor) {
		NpcRule rule = new NpcRule();
		rule.fixed = true;
		rule.island = island;
		rule.beamColor = beamColor;
		return rule;
	}

	/** AC-NPCWP-01: the resolution order. */
	@Test
	void overrideThenIslandThenGlobalDefault() {
		Map<String, Integer> islandColors = new HashMap<>(Map.of("Crimson Isle", 0x123456));
		assertEquals(0xFF0000, WaypointColors.beamColor(fixedRule("Crimson Isle", 0xFF0000), islandColors), "a per-rule override");
		assertEquals(0x123456, WaypointColors.beamColor(fixedRule("Crimson Isle", null), islandColors), "the edited island colour");
		assertEquals(WaypointColors.ISLAND_DEFAULTS.get("Hub"), WaypointColors.beamColor(fixedRule("Hub", null), islandColors),
				"an island's documented default");
		assertEquals(WaypointColors.GLOBAL_DEFAULT, WaypointColors.beamColor(fixedRule(null, null), islandColors), "no island");
		assertEquals(WaypointColors.GLOBAL_DEFAULT, WaypointColors.beamColor(fixedRule("  ", null), islandColors), "a blank island");
		assertEquals(WaypointColors.GLOBAL_DEFAULT, WaypointColors.beamColor(fixedRule("Galatea Reef", null), islandColors),
				"an island missing from the map (EC-NPCWP-02)");
		// "Use island colour" clears the override.
		NpcRule rule = fixedRule("Crimson Isle", 0xFF0000);
		rule.beamColor = null;
		assertEquals(0x123456, WaypointColors.beamColor(rule, islandColors));
	}

	/** REQ-NPCWP-05: the islands of the NPC list as fetched on 2026-10-02 have a default; T3.8 checks the bundled table itself. */
	@Test
	void everyIslandOfTheNpcListSnapshotHasADefault() {
		for (String island : new String[] {"Hub", "Dungeon Hub", "Catacombs", "Crimson Isle", "Critter Safari", "Crystal Hollows", "Deep Caverns",
				"Dwarven Mines", "Garden", "Gold Mine", "Jerry", "Lotus Atoll", "Moonglade Marsh", "Spider's Den", "The End", "The Park",
				"Torrhus Canyon"}) {
			assertTrue(WaypointColors.ISLAND_DEFAULTS.containsKey(island), island);
		}
	}

	/** Every island a rule can be restricted to has a default, and every default belongs to an island. */
	@Test
	void everyKnownIslandNameHasADefault() {
		for (Islands.Island island : Islands.all()) {
			assertTrue(WaypointColors.ISLAND_DEFAULTS.containsKey(island.name()), island.name());
		}
		assertEquals(Islands.all().size(), WaypointColors.ISLAND_DEFAULTS.size());
	}

	/** AC-NPCWP-08: beam colours and glow colours never change each other. */
	@Test
	void beamAndGlowColoursAreIndependent() {
		Map<String, Integer> islandColors = new HashMap<>();
		NpcRule fixed = fixedRule("Crimson Isle", null);
		NpcRule moving = new NpcRule();
		moving.fixed = false;
		moving.island = "Crimson Isle";
		moving.color = 0x0AA351;
		int beamBefore = WaypointColors.beamColor(fixed, islandColors);
		// An island colour edit or a beam override leaves a moving rule's outline colour as it is ...
		islandColors.put("Crimson Isle", 0x00FFFF);
		moving.beamColor = 0xFF0000;
		assertEquals(0x0AA351, NpcSearchModule.toHighlightRule(moving).color);
		assertNotEquals(beamBefore, WaypointColors.beamColor(fixed, islandColors));
		// ... and a glow colour edit leaves the resolved beam colour as it is.
		int beam = WaypointColors.beamColor(fixed, islandColors);
		fixed.color = 0x112233;
		assertEquals(beam, WaypointColors.beamColor(fixed, islandColors));
	}
}
