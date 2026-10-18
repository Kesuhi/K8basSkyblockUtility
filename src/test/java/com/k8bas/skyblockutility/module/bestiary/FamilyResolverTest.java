package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.location.Islands;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AC-BEST-15 [A] (T5.1b, REQ-BEST-16, EC-BEST-08, EC-BEST-11): a family name resolves to its table id by itself when
 * only one family has it, by the widget's next threshold, the island or the Bestiary menu area when several do, and an
 * unknown or still ambiguous name resolves to nothing and is logged once. The menu area names are the ones the G1
 * capture of the Bestiary menu shows ("Your Island", "The Garden", "The Catacombs"), which differ from NEU's.
 */
class FamilyResolverTest {
	private static final Pattern MENU_ITEM = Pattern.compile("^\\[K8BAS-DUMP] container \\d+ slot (\\d+) item=(\\S+) sbid=\\S+ count=\\d+ \\| (.*)$");

	private final List<String> log = new ArrayList<>();
	private final FamilyResolver resolver = new FamilyResolver(BestiaryTables.shipped(), log::add);

	private static String id(BestiaryFamily family) {
		return family == null ? null : family.id();
	}

	@Test
	void aNameOnlyOneFamilyHasResolvesAnywhere() {
		assertEquals("ghost", id(resolver.onIsland("Ghost", Islands.islandFor("mining_3"))));
		// The G1 Dwarven Mines widget lists Bal, a Crystal Hollows family: the island is no filter for a unique name.
		assertEquals("bal", id(resolver.onIsland("Bal", Islands.islandFor("mining_3"))));
		assertEquals("glacite_walker", id(resolver.onIsland("Glacite Walker", Islands.islandFor("mineshaft"))));
		assertEquals("ghost", id(resolver.onIsland("Ghost", null)));
		assertEquals("ghost", id(resolver.inMenu("Ghost", "Bestiary", "Dwarven Mines")));
		assertEquals("ghost", id(resolver.inMenu("Ghost", "Bestiary", "Hub")), "the table may predate a move");
		// Colour codes, case and spacing do not matter.
		assertEquals("glacite_walker", id(resolver.onIsland("§cGlacite  walker ", "Dwarven Mines")));
		assertEquals(List.of(), log);
	}

	@Test
	void aSharedNameResolvesByIsland() {
		assertEquals("bat_dynamic", id(resolver.onIsland("Bat", Islands.islandFor("dynamic"))));
		assertEquals("bat_catacombs", id(resolver.onIsland("Bat", Islands.CATACOMBS)));
		assertEquals("enderman_combat_3", id(resolver.onIsland("Enderman", Islands.islandFor("combat_3"))));
		assertEquals("enderman_dynamic", id(resolver.onIsland("Enderman", Islands.islandFor("dynamic"))));
		assertEquals(List.of(), log);
	}

	@Test
	void theWidgetsNextThresholdDecidesBeforeTheIsland() {
		// End Endermen still listed on the player's island: 1,500 is a threshold of the End's family only.
		assertEquals("enderman_combat_3", id(resolver.onIsland("Enderman", Islands.islandFor("dynamic"), 1_500)));
		assertEquals("enderman_dynamic", id(resolver.onIsland("Enderman", Islands.islandFor("combat_3"), 10)));
		// 5 is a threshold of both, and 7,777 of neither (a rebalance): the island decides.
		assertEquals("enderman_combat_3", id(resolver.onIsland("Enderman", Islands.islandFor("combat_3"), 5)));
		assertEquals("enderman_dynamic", id(resolver.onIsland("Enderman", Islands.islandFor("dynamic"), 7_777)));
		assertEquals(List.of(), log);
	}

	@Test
	void aSharedNameResolvesByMenuArea() {
		assertEquals("bat_dynamic", id(resolver.inMenu("Bat", "Bestiary", "Your Island")));
		assertEquals("bat_catacombs", id(resolver.inMenu("Bat", "Bestiary", "The Catacombs")));
		assertEquals("enderman_combat_3", id(resolver.inMenu("Enderman", "Bestiary", "The End")));
		// The start of an area name is enough (the game cuts titles at about 31 characters).
		assertEquals("bat_catacombs", id(resolver.inMenu("Bat", "Bestiary", "The Catacom")));
		assertEquals(List.of(), log);
	}

	@Test
	void everyAreaOfTheCapturedBestiaryMenuIsKnown() {
		// The 21 area items of the G1 capture of the Bestiary menu, slots 10 to 34.
		Map<String, String> expected = new LinkedHashMap<>();
		for (String line : com.k8bas.skyblockutility.skyblock.CaptureFixtures.lines("/fixtures/captures/g1-2026-10-01/containers/bestiary.txt")) {
			Matcher m = MENU_ITEM.matcher(line);
			int slot = m.matches() ? Integer.parseInt(m.group(1)) : -1;
			if (slot >= 10 && slot <= 34 && !m.group(2).endsWith("glass_pane")) {
				expected.put(m.group(3), null);
			}
		}
		assertEquals(21, expected.size(), expected.toString());
		Map<String, String> areas = new HashMap<>();
		for (String area : expected.keySet()) {
			areas.put(area, resolver.menuArea("Bestiary", area));
		}
		assertEquals(Map.of("Your Island", "dynamic", "The Garden", "garden", "The Catacombs", "catacombs", "Dwarven Mines", "mining_3",
				"Fishing", "fishing", "Critter Safari", "safari", "Jerry", "jerry", "Mythological Creatures", "mythological_creatures",
				"Spooky Festival", "spooky_festival", "Moonglade Marsh", "foraging_2"), filter(areas, List.of("Your Island", "The Garden",
				"The Catacombs", "Dwarven Mines", "Fishing", "Critter Safari", "Jerry", "Mythological Creatures", "Spooky Festival", "Moonglade Marsh")));
		assertEquals(List.of(), areas.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).toList(), "every area is known");
		assertEquals(21, new java.util.HashSet<>(areas.values()).size(), "each a different category");
		// "Bestiary ➜ " leaves 20 characters of a 31-character title.
		assertEquals("mythological_creatures", resolver.menuArea("Bestiary", "Mythological Creatur"));
	}

	private static Map<String, String> filter(Map<String, String> map, List<String> keys) {
		Map<String, String> out = new HashMap<>();
		keys.forEach(key -> out.put(key, map.get(key)));
		return out;
	}

	@Test
	void everyIslandHasItsArea() {
		Map<String, String> expected = new HashMap<>();
		expected.put("Hub", "hub");
		expected.put("Private Island", "dynamic");
		expected.put("Garden", "garden");
		expected.put("The Farming Islands", "farming_1");
		expected.put("The Park", "foraging_1");
		expected.put("Moonglade Marsh", "foraging_2");
		expected.put("Torrhus Canyon", "foraging_3");
		expected.put("Spider's Den", "combat_1");
		expected.put("The End", "combat_3");
		expected.put("Crimson Isle", "crimson_isle");
		expected.put("Deep Caverns", "mining_2");
		expected.put("Dwarven Mines", "mining_3");
		expected.put("Crystal Hollows", "crystal_hollows");
		expected.put("Glacite Mineshafts", "mining_3");
		expected.put("Backwater Bayou", "fishing/backwater_bayou");
		expected.put("Lotus Atoll", "lotus_atoll");
		expected.put("Critter Safari", "safari");
		expected.put("Kuudra", "kuudra");
		expected.put("Jerry", "fishing/winter");
		expected.put(Islands.CATACOMBS, "catacombs");
		Map<String, String> actual = new HashMap<>();
		for (Islands.Island island : Islands.all()) {
			String area = resolver.islandArea(island.name());
			if (area != null) {
				actual.put(island.name(), area);
			}
		}
		// Gold Mine, The Rift, Dark Auction and the Dungeon Hub have no bestiary area.
		assertEquals(expected, actual);
		assertNull(resolver.islandArea(null));
	}

	@Test
	void aSharedNameWithoutAUsefulContextIsAmbiguousAndLoggedOnce() {
		assertNull(resolver.onIsland("Bat", null));
		assertNull(resolver.onIsland("Bat", "Hub"));
		assertNull(resolver.inMenu("Bat", "Bestiary", "Hub"));
		assertEquals(List.of("Bestiary family name 'Bat' is ambiguous (bat_dynamic, bat_catacombs); its values show without a family"), log);
	}

	@Test
	void anUnknownNameIsLoggedOnce() {
		assertNull(resolver.onIsland("Nope Beast", "Hub"));
		assertNull(resolver.inMenu("nope beast", "Bestiary", "Hub"));
		// Stoneworm stays one family (EC-BEST-11): a Scatha is no family of its own.
		assertNull(resolver.onIsland("Scatha", "Crystal Hollows"));
		assertEquals("stoneworm", id(resolver.onIsland("Stoneworm", "Crystal Hollows")));
		assertEquals(List.of("Bestiary family name 'Nope Beast' is not in the table; its values show without a family",
				"Bestiary family name 'Scatha' is not in the table; its values show without a family"), log);
	}

	@Test
	void subcategoryPagesResolveByTheirParent() {
		// A fixture: "Twin" on two islands (Hub, Spider's Den), in the Fishing subcategories Lava, Fishing, Fishing Festival
		// and Spooky Festival, in the category Spooky Festival, and in Mythological Creatures (a long name).
		List<String> warnings = new ArrayList<>();
		String[][] places = {{"hub", null}, {"combat_1", null}, {"fishing", "lava"}, {"fishing", "fishing"}, {"fishing", "fishing_festival"},
				{"fishing", "spooky_festival"}, {"spooky_festival", null}, {"mythological_creatures", null}};
		StringBuilder rows = new StringBuilder();
		for (String[] place : places) {
			String id = place[1] == null ? place[0] : place[0] + "_" + place[1];
			rows.append(rows.isEmpty() ? "" : ",").append("{\"id\": \"").append(id).append("\", \"name\": \"Twin\", \"category\": \"").append(place[0])
					.append(place[1] == null ? "" : "\", \"subcategory\": \"" + place[1])
					.append("\", \"bracket\": \"x\", \"cap\": 40, \"maxTiers\": 4, \"mobs\": [\"").append(id).append("_1\"]}");
		}
		BestiaryFamilies families = BestiaryTables.fixture(BestiaryTables.table(
				"\"hub\": {\"name\": \"Hub\"}, \"combat_1\": {\"name\": \"Spider's Den\"}, \"spooky_festival\": {\"name\": \"Spooky Festival\"},"
						+ " \"mythological_creatures\": {\"name\": \"Mythological Creatures\"}, \"fishing\": {\"name\": \"Fishing\", \"subcategories\": {"
						+ "\"lava\": {\"name\": \"Lava\"}, \"fishing\": {\"name\": \"Fishing\"}, \"fishing_festival\": {\"name\": \"Fishing Festival\"},"
						+ " \"spooky_festival\": {\"name\": \"Spooky Festival\"}}}", rows.toString()), warnings);
		assertEquals(List.of(), warnings);
		FamilyResolver fixture = new FamilyResolver(families, log::add);
		assertEquals("hub", id(fixture.onIsland("Twin", "Hub")), "a fixture name on two islands resolves by island");
		assertEquals("combat_1", id(fixture.onIsland("Twin", "Spider's Den")));
		assertEquals("fishing_lava", id(fixture.inMenu("Twin", "Fishing", "Lava")));
		assertEquals("hub", id(fixture.inMenu("Twin", "Bestiary", "Hub")));
		assertEquals("mythological_creatures", id(fixture.inMenu("Twin", "Bestiary", "Mythological Creatur")));
		// An exact name wins over a longer one it starts ("Fishing" and "Fishing Festival").
		assertEquals("fishing_fishing", id(fixture.inMenu("Twin", "Fishing", "Fishing")));
		assertEquals("fishing_fishing_festival", id(fixture.inMenu("Twin", "Fishing", "Fishing Fest")));
		// Spooky Festival is a category and a Fishing subcategory; the parent tells them apart.
		assertEquals("spooky_festival", id(fixture.inMenu("Twin", "Bestiary", "Spooky Festival")));
		assertEquals("fishing_spooky_festival", id(fixture.inMenu("Twin", "Fishing", "Spooky Festival")));
		assertEquals(List.of(), log);
		// A subcategory name under the wrong parent, or a page of another menu (a /sblevels page), decides nothing.
		assertNull(fixture.inMenu("Twin", "Bestiary", "Lava"));
		assertNull(fixture.inMenu("Twin", "Fishing", "Trophy Fish"));
		assertNull(fixture.inMenu("Twin", "Fishing", "Fish"), "the start of two names");
		assertEquals(List.of("Bestiary family name 'Twin' is ambiguous (hub, combat_1, fishing_lava, fishing_fishing, fishing_fishing_festival,"
				+ " fishing_spooky_festival, spooky_festival, mythological_creatures); its values show without a family"), log);
	}
}
