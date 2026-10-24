package com.k8bas.skyblockutility.module.bestiary;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bestiary family table read through the registry as the game reads it, and its tier math (T5.1): AC-BEST-02 [A]
 * (Ghost at 12,449 kills is tier 15 of 20 with 51 to the next tier and 12,551 to max; Stoneworm at 250 is tier 15,
 * "MAX"; a cap outside its bracket list ends on the cap), EC-BEST-09 to EC-BEST-11.
 */
class BestiaryTableTest {
	@Test
	void ghostAt12449IsTier15Of20() {
		BestiaryFamily ghost = BestiaryTables.shipped().family("ghost");
		assertNotNull(ghost);
		assertEquals("Ghost", ghost.name());
		assertEquals("mining_3", ghost.category());
		assertEquals("2", ghost.bracket());
		assertEquals(25_000, ghost.cap());
		assertEquals(20, ghost.maxTier());
		TierProgress at = ghost.progress(12_449);
		assertEquals(15, at.tier());
		assertEquals(20, at.maxTier());
		assertEquals(12_500, at.nextThreshold());
		assertEquals(51, at.toNext());
		assertEquals(12_551, at.toMax());
		assertFalse(at.isMax());
	}

	@Test
	void stonewormAt250IsMax() {
		BestiaryFamily stoneworm = BestiaryTables.shipped().family("stoneworm");
		assertNotNull(stoneworm);
		assertEquals("6", stoneworm.bracket());
		assertEquals(250, stoneworm.cap());
		assertEquals(15, stoneworm.maxTier());
		// One family for worms and Scathas, as the server counts it (EC-BEST-11).
		assertEquals(List.of("worm_5", "scatha_10"), stoneworm.mobs());
		TierProgress at = stoneworm.progress(250);
		assertEquals(15, at.tier());
		assertTrue(at.isMax());
		assertNull(at.nextThreshold());
		assertNull(at.toNext());
		assertEquals(0, at.toMax());
		assertTrue(stoneworm.progress(9_999).isMax(), "past the cap stays MAX");
		assertEquals(15, stoneworm.progress(9_999).tier());
	}

	@Test
	void theShippedTableHasEveryFamily() {
		BestiaryFamilies families = BestiaryTables.shipped();
		assertEquals(359, families.families().size());
		assertEquals("Dwarven Mines", families.categoryName("mining_3", null));
		assertEquals("Lava", families.categoryName("fishing", "lava"));
		assertNull(families.categoryName("fishing", "nope"));
		assertNull(families.family("nope"));
		// A name on two islands gets the category in its id (EC-BEST-08).
		assertEquals("Bat", families.family("bat_catacombs").name());
		assertEquals("dynamic", families.family("bat_dynamic").category());
	}

	@Test
	void aShippedCapOutsideItsBracketEndsOnTheCap() {
		// Bracket 3 has no 125, so the tiers are the thresholds below it and then 125 (EC-BEST-09).
		BestiaryFamily grubber = BestiaryTables.shipped().family("mithril_grubber");
		assertNotNull(grubber);
		assertEquals(List.of(4, 8, 12, 16, 20, 40, 80, 125), grubber.thresholds());
		assertEquals(8, grubber.maxTier());
		TierProgress at = grubber.progress(124);
		assertEquals(7, at.tier());
		assertEquals(1, at.toNext());
		assertTrue(grubber.progress(125).isMax());
	}

	@Test
	void critterFamiliesUseTheirOwnSet() {
		// EC-BEST-10 [decided R58]: Hewver's cap 50 ends Critter set 4 (NEU named set 3, which gave 9 tiers; the logs show tier X).
		BestiaryFamily hewver = BestiaryTables.shipped().family("hewver");
		assertNotNull(hewver);
		assertEquals("critters_4", hewver.bracket());
		assertEquals(List.of(1, 3, 6, 10, 15, 20, 25, 30, 40, 50), hewver.thresholds());
		TierProgress none = hewver.progress(0);
		assertEquals(0, none.tier());
		assertEquals(1, none.nextThreshold());
		assertEquals(1, none.toNext());
		assertEquals(50, none.toMax());
		assertEquals(10, hewver.progress(50).tier());
	}

	@Test
	void exactlyTheCorrectedRowsKeepNeusValuesAndCiteTheirSources() throws IOException {
		// R58: NEU's bracket and cap per corrected family, and the sources that show the correction.
		Map<String, List<Object>> neu = Map.of(
				"hewver", List.of("critters_3", 50, List.of("game_logs", "hypixel_api_reborn", "kyuwa_caps")),
				"murkbat", List.of("critters_3", 50, List.of("hypixel_api_reborn", "kyuwa_caps")),
				"honeymite", List.of("critters_2", 75, List.of("hypixel_api_reborn", "kyuwa_caps")),
				"woodlouse", List.of("critters_2", 75, List.of("hypixel_api_reborn", "kyuwa_caps")),
				"parched", List.of("3", 75, List.of("game_logs", "hypixel_api_reborn", "kyuwa_caps")),
				"stag_beetle", List.of("critters_1", 100, List.of("hypixel_api_reborn", "kyuwa_caps")),
				"honeyhog", List.of("1", 100, List.of("hypixel_api_reborn", "kyuwa_caps")),
				"beeheemoth", List.of("critters_5", 25, List.of("hypixel_api_reborn", "kyuwa_caps")),
				"tidetot", List.of("4", 1000, List.of("hypixel_api_reborn", "kyuwa_caps")),
				"grizzly_bear", List.of("5", 25, List.of("hypixel_api_reborn", "kyuwa_caps")));
		JsonObject table;
		try (InputStream in = BestiaryTableTest.class.getResourceAsStream("/assets/k8bas_skyblock_utility/data/thirdparty/neu-repo/bestiary/families.json")) {
			table = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		Map<String, List<Object>> actual = new HashMap<>();
		for (JsonElement element : table.getAsJsonArray("rows")) {
			JsonObject row = element.getAsJsonObject();
			if (row.has("neu") || row.has("sourceRefs")) {
				JsonObject original = row.getAsJsonObject("neu");
				actual.put(row.get("id").getAsString(), List.of(original.get("bracket").getAsString(), original.get("cap").getAsInt(),
						row.getAsJsonArray("sourceRefs").asList().stream().map(JsonElement::getAsString).toList()));
			}
		}
		assertEquals(neu, actual);
	}

	@Test
	void theFamiliesNeuGetsWrongAreCorrected() {
		// R58 (T5.1c): bracket and cap per family; each now has 10 tiers.
		Map<String, List<Object>> corrected = Map.of(
				"hewver", List.of("critters_4", 50), "murkbat", List.of("critters_4", 50),
				"honeymite", List.of("critters_3", 75), "woodlouse", List.of("critters_3", 75), "parched", List.of("critters_3", 75),
				"stag_beetle", List.of("critters_2", 100), "honeyhog", List.of("critters_2", 100), "beeheemoth", List.of("critters_4", 50),
				"tidetot", List.of("critters_4", 50), "grizzly_bear", List.of("critters_5", 25));
		BestiaryFamilies families = BestiaryTables.shipped();
		corrected.forEach((id, expected) -> {
			BestiaryFamily family = families.family(id);
			assertNotNull(family, id);
			assertEquals(expected, List.of(family.bracket(), family.cap()), id);
			assertEquals(10, family.maxTier(), id);
		});
		// Every Critter family ends its set, so each has 10 tiers.
		for (BestiaryFamily family : families.families()) {
			if (family.bracket().startsWith("critters_")) {
				assertEquals(10, family.maxTier(), family.id());
			}
		}
	}

	@Test
	void theTierMathEndsOnTheCap() {
		assertEquals(List.of(10, 20, 30, 35), BestiaryTiers.thresholds(List.of(10, 20, 30, 40), 35));
		assertEquals(List.of(10, 20, 30), BestiaryTiers.thresholds(List.of(10, 20, 30, 40), 30));
		TierProgress at = BestiaryTiers.progress(List.of(10, 20, 30, 35), 31);
		assertEquals(new TierProgress(31, 3, 4, 35, 4, 4), at);
		assertEquals(new TierProgress(10, 1, 4, 20, 10, 25), BestiaryTiers.progress(List.of(10, 20, 30, 35), 10), "a threshold reached is its tier");
		assertEquals(new TierProgress(0, 0, 4, 10, 10, 35), BestiaryTiers.progress(List.of(10, 20, 30, 35), -5), "no kills below 0");
		IllegalArgumentException empty = assertThrows(IllegalArgumentException.class, () -> BestiaryTiers.progress(List.of(), 3));
		assertEquals("no thresholds: a family has at least its cap", empty.getMessage());
	}

	@Test
	void aRepeatedIdKeepsTheFirstFamilyWithOneWarning() {
		List<String> warnings = new ArrayList<>();
		BestiaryFamilies families = BestiaryTables.fixture(BestiaryTables.table(
				"{\"id\": \"twice\", \"name\": \"First\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 40, \"maxTiers\": 4, \"mobs\": [\"a_1\"]},"
						+ "{\"id\": \"twice\", \"name\": \"Second\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 20, \"maxTiers\": 2, \"mobs\": [\"b_1\"]}"), warnings);
		assertEquals(1, families.families().size());
		assertEquals("First", families.family("twice").name());
		assertEquals(List.of("Data table bestiary: family twice listed twice; the first is kept"), warnings);
	}

	@Test
	void aFixtureFamilyWithItsCapOutsideItsBracketEndsOnTheCap() {
		List<String> warnings = new ArrayList<>();
		BestiaryFamilies families = BestiaryTables.fixture(BestiaryTables.table(
				"{\"id\": \"odd\", \"name\": \"Odd\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 35, \"maxTiers\": 4, \"mobs\": [\"odd_1\"]}"), warnings);
		assertEquals(List.of(), warnings);
		BestiaryFamily odd = families.family("odd");
		assertNotNull(odd);
		assertEquals(4, odd.maxTier());
		assertEquals(new TierProgress(31, 3, 4, 35, 4, 4), odd.progress(31));
		assertTrue(odd.progress(1_000).isMax());
		assertEquals(4, odd.progress(1_000).tier());
	}

	@Test
	void aRowWhoseTiersDisagreeOrWhoseBracketIsUnknownIsSkippedWithOneWarning() {
		List<String> warnings = new ArrayList<>();
		BestiaryFamilies families = BestiaryTables.fixture(BestiaryTables.table(
				"{\"id\": \"good\", \"name\": \"Good\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 40, \"maxTiers\": 4, \"mobs\": [\"good_1\"]},"
						+ "{\"id\": \"counted\", \"name\": \"Counted\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 40, \"maxTiers\": 5, \"mobs\": [\"c_1\"]},"
						+ "{\"id\": \"lost\", \"name\": \"Lost\", \"category\": \"hub\", \"bracket\": \"y\", \"cap\": 40, \"maxTiers\": 4, \"mobs\": [\"l_1\"]}"), warnings);
		assertEquals(List.of("good"), families.families().stream().map(BestiaryFamily::id).toList());
		assertEquals(2, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("row counted skipped") && warnings.get(0).contains("maxTiers 5"), warnings.toString());
		assertTrue(warnings.get(1).contains("row lost skipped") && warnings.get(1).contains("bracket y"), warnings.toString());
	}
}
