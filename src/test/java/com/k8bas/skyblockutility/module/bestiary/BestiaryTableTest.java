package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.data.DataRegistry;
import com.k8bas.skyblockutility.data.TableHandle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
	private static byte[] resource(String path) {
		try (InputStream in = BestiaryTableTest.class.getResourceAsStream("/assets/k8bas_skyblock_utility/data/" + path)) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static BestiaryFamilies shipped() {
		List<String> warnings = new ArrayList<>();
		DataRegistry registry = new DataRegistry(BestiaryTableTest::resource, Runnable::run, Runnable::run, warnings::add);
		TableHandle<BestiaryFamilies> handle = registry.register(BestiaryTable.SPEC);
		registry.start();
		assertEquals(List.of(), warnings, "the table loads without a warning");
		assertNotNull(handle.value(), "loaded");
		return handle.value();
	}

	/** A one-file registry holding [table] as the bestiary table, with its index entry. */
	private static BestiaryFamilies fixture(String table, List<String> warnings) {
		byte[] bytes = table.getBytes(StandardCharsets.UTF_8);
		String index = "{\"indexVersion\": 1, \"tables\": {\"bestiary\": {\"path\": \"bestiary.json\", \"sha256\": \"" + sha256(bytes)
				+ "\", \"rows\": 1, \"schemaVersion\": 1, \"dataVersion\": 1, \"gameVersion\": \"0.27.1\"}}}";
		Map<String, byte[]> files = Map.of("index.json", index.getBytes(StandardCharsets.UTF_8), "bestiary.json", bytes);
		DataRegistry registry = new DataRegistry(files::get, Runnable::run, Runnable::run, warnings::add);
		TableHandle<BestiaryFamilies> handle = registry.register(BestiaryTable.SPEC);
		registry.start();
		assertNotNull(handle.value(), "loaded: " + warnings);
		return handle.value();
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void ghostAt12449IsTier15Of20() {
		BestiaryFamily ghost = shipped().family("ghost");
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
		BestiaryFamily stoneworm = shipped().family("stoneworm");
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
		BestiaryFamilies families = shipped();
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
		BestiaryFamily grubber = shipped().family("mithril_grubber");
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
		// CRITTERS set 3 is 1, 5, 10, 15, 20, 25, 35, 45, 60, 75; Hewver's cap 50 is not in it (EC-BEST-10). Its 9 tiers follow
		// REQ-BEST-15; the in-game count of the five such critters is UNVERIFIED, and the widget's threshold wins (EC-BEST-09).
		BestiaryFamily hewver = shipped().family("hewver");
		assertNotNull(hewver);
		assertEquals("critters_3", hewver.bracket());
		assertEquals(List.of(1, 5, 10, 15, 20, 25, 35, 45, 50), hewver.thresholds());
		TierProgress none = hewver.progress(0);
		assertEquals(0, none.tier());
		assertEquals(1, none.nextThreshold());
		assertEquals(1, none.toNext());
		assertEquals(50, none.toMax());
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
		BestiaryFamilies families = fixture(table(
				"{\"id\": \"twice\", \"name\": \"First\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 40, \"maxTiers\": 4, \"mobs\": [\"a_1\"]},"
						+ "{\"id\": \"twice\", \"name\": \"Second\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 20, \"maxTiers\": 2, \"mobs\": [\"b_1\"]}"), warnings);
		assertEquals(1, families.families().size());
		assertEquals("First", families.family("twice").name());
		assertEquals(List.of("Data table bestiary: family twice listed twice; the first is kept"), warnings);
	}

	@Test
	void aFixtureFamilyWithItsCapOutsideItsBracketEndsOnTheCap() {
		List<String> warnings = new ArrayList<>();
		BestiaryFamilies families = fixture(table(
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
		BestiaryFamilies families = fixture(table(
				"{\"id\": \"good\", \"name\": \"Good\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 40, \"maxTiers\": 4, \"mobs\": [\"good_1\"]},"
						+ "{\"id\": \"counted\", \"name\": \"Counted\", \"category\": \"hub\", \"bracket\": \"x\", \"cap\": 40, \"maxTiers\": 5, \"mobs\": [\"c_1\"]},"
						+ "{\"id\": \"lost\", \"name\": \"Lost\", \"category\": \"hub\", \"bracket\": \"y\", \"cap\": 40, \"maxTiers\": 4, \"mobs\": [\"l_1\"]}"), warnings);
		assertEquals(List.of("good"), families.families().stream().map(BestiaryFamily::id).toList());
		assertEquals(2, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("row counted skipped") && warnings.get(0).contains("maxTiers 5"), warnings.toString());
		assertTrue(warnings.get(1).contains("row lost skipped") && warnings.get(1).contains("bracket y"), warnings.toString());
	}

	private static String table(String rows) {
		return "{\"table\": \"bestiary\", \"schemaVersion\": 1, \"dataVersion\": 1, \"gameVersion\": \"0.27.1\", \"generatedAt\": \"2026-10-18T18:00:00Z\","
				+ " \"license\": \"MIT\", \"sources\": {}, \"brackets\": {\"x\": [10, 20, 30, 40]}, \"categories\": {\"hub\": {\"name\": \"Hub\"}},"
				+ " \"rows\": [" + rows + "]}";
	}
}
