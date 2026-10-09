package com.k8bas.skyblockutility.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.data.DataRegistry;
import com.k8bas.skyblockutility.data.TableHandle;
import com.k8bas.skyblockutility.data.TableSpec;
import com.k8bas.skyblockutility.location.Islands.Island;
import com.k8bas.skyblockutility.location.Islands;
import com.k8bas.skyblockutility.module.mobhighlighter.MobDatabaseEntry;
import com.k8bas.skyblockutility.module.mobhighlighter.MobList;
import com.k8bas.skyblockutility.module.mobhighlighter.MobTable;
import com.k8bas.skyblockutility.module.npcsearch.NpcDatabaseEntry;
import com.k8bas.skyblockutility.module.npcsearch.NpcList;
import com.k8bas.skyblockutility.module.npcsearch.NpcTable;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import com.k8bas.skyblockutility.ui.screen.PickerRows;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled NPC and mob tables (T3.8, REQ-NPCDB-03, REQ-NPCDB-06, AC-NPCDB-05, AC-NPCDB-08, AC-DATA-05 [A]),
 * read through the registry exactly as the game reads them; the data was converted from the frozen gists
 * audited in docs/data-audit-npc-mob.md. The schemas' island values stay in sync with the location module.
 */
class NpcMobTablesTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "."));

	private static byte[] resource(String path) {
		try (InputStream in = NpcMobTablesTest.class.getResourceAsStream("/assets/k8bas_skyblock_utility/data/" + path)) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static <T> T load(TableSpec<T> spec, Map<String, byte[]> files, List<String> warnings) {
		DataRegistry registry = new DataRegistry(path -> files.containsKey(path) ? files.get(path) : resource(path), Runnable::run, Runnable::run,
				warnings::add);
		TableHandle<T> handle = registry.register(spec);
		registry.start();
		return handle.value();
	}

	private static <T> T bundled(TableSpec<T> spec) {
		List<String> warnings = new ArrayList<>();
		T value = load(spec, Map.of(), warnings);
		assertEquals(List.of(), warnings, "the bundled table loads without a warning");
		return value;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A one-table registry source with {@code rows} under the given table id (LF, no BOM, so the checksum is plain). */
	private static Map<String, byte[]> synthetic(String table, String license, String rows) {
		byte[] body = ("{\"table\":\"" + table + "\",\"schemaVersion\":1,\"dataVersion\":1,\"gameVersion\":\"0.27.1\","
				+ "\"generatedAt\":\"2026-10-09T18:00:00Z\",\"license\":\"" + license + "\",\"sources\":{\"t\":{}},\"rows\":" + rows + "}")
				.getBytes(StandardCharsets.UTF_8);
		String index = "{\"indexVersion\":1,\"tables\":{\"" + table + "\":{\"path\":\"t.json\",\"sha256\":\"" + sha256(body)
				+ "\",\"rows\":2,\"schemaVersion\":1,\"dataVersion\":1,\"gameVersion\":\"0.27.1\"}}}";
		return Map.of("index.json", index.getBytes(StandardCharsets.UTF_8), "t.json", body);
	}

	@Test
	void theNpcTableHoldsTheCorrectedList() {
		NpcList npcs = bundled(NpcTable.SPEC);
		// The 367 frozen entries less the 5 merged into others (T3.8b).
		assertEquals(362, npcs.getEntries().size());
		assertEquals(357, npcs.getEntries().stream().filter(npc -> npc.fixed).count());
		// REQ-LOC-03: the 6 fixed NPCs filed under "Catacombs" stand in the Dungeon Hub.
		for (String id : List.of("adventurer_saul", "croesus", "guildford", "malik", "mort", "ophelia")) {
			assertEquals(Islands.DUNGEON_HUB, npcs.byId(id).island, id);
		}
		// D-2: the dungeon NPCs that move stay in "Catacombs"; the found-title ids (R20) keep their ids.
		for (String id : List.of("trinity", "tomioka", "duncan")) {
			assertFalse(npcs.byId(id).fixed, id);
			assertEquals(Islands.CATACOMBS, npcs.byId(id).island, id);
		}
		for (String id : List.of("xalx", "pete")) {
			assertNotNull(npcs.byId(id), id);
		}
	}

	private static JsonObject rawTable(String path) {
		return JsonParser.parseString(new String(resource(path), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void noFixedNpcIsFiledUnderCatacombs() {
		// AC-NPCDB-06 (D-2): in the data itself, not only after the load-time normalisation.
		JsonObject table = rawTable("thirdparty/neu-repo/npcdb/npcs.json");
		assertEquals(2, table.get("dataVersion").getAsInt(), "T3.8b's corrections are data version 2");
		for (var row : table.getAsJsonArray("rows")) {
			JsonObject npc = row.getAsJsonObject();
			assertFalse(npc.get("fixed").getAsBoolean() && npc.get("island").getAsString().equals("Catacombs"), npc.toString());
		}
	}

	@Test
	void everyNearDuplicateIsResolvedAsTheAuditRecords() {
		// AC-NPCDB-06, REQ-NPCDB-07(b)(d), docs/data-audit-npc-mob.md: each merged id resolves through its alias to
		// the entry it was merged into, at the position the audit settled on.
		NpcList npcs = bundled(NpcTable.SPEC);
		Map<String, String> merged = Map.of("bramass_beastslayer_2", "bramass_beastslayer", "bestiary", "bramass_beastslayer",
				"hendrik_2", "hendrik", "fishing_merchant_2", "fishing_merchant", "fred", "forge_foreman");
		merged.forEach((old, into) -> {
			assertEquals(into, npcs.byId(old).id, old);
			assertTrue(npcs.getEntries().stream().noneMatch(npc -> npc.id.equals(old)), old + " is no entry of its own");
		});
		assertPosition(npcs.byId("bramass_beastslayer"), -272, 113, -197);
		assertPosition(npcs.byId("hendrik"), 16, 62, -31);
		assertPosition(npcs.byId("fishing_merchant"), 112, 71, -45);
		assertPosition(npcs.byId("forge_foreman"), -3, 149, -69);
		// Positions NEU corrected since the gist was compiled.
		assertPosition(npcs.byId("gemma"), 475, 106, 513);
		assertPosition(npcs.byId("scoop"), -54, 169, 70);
		// Confirmed distinct: both stay.
		for (String id : List.of("arba", "arbadak", "bobby_joe", "farm_merchant", "lukas_the_aquarist", "pig_shop", "grelius", "krevius",
				"keran", "prie", "fear_mongerer", "oringo", "freddy")) {
			assertEquals(id, npcs.byId(id).id, id);
		}
	}

	@Test
	void aMergedEntryIsFoundByItsOldName() {
		// The picker searches an entry's aliases too (as the NPC Search module maps it): "bestiary" finds Bramass.
		List<RuleDatabase.Entry> entries = bundled(NpcTable.SPEC).getEntries().stream()
				.map(npc -> new RuleDatabase.Entry(npc.id, npc.displayName, npc.island, null, TableRows.INSTANCE.searchText(npc.matchText, npc.aliases)))
				.toList();
		List<String> found = PickerRows.rows(entries, Set.of(), "bestiary", Set.of()).stream()
				.filter(row -> row.entry() != null).map(PickerRows.Row::label).toList();
		assertEquals(List.of("Bramass Beastslayer"), found);
		assertEquals("Trinity", TableRows.INSTANCE.searchText("Trinity", List.of()));
		assertEquals("Trinity hendrik 2", TableRows.INSTANCE.searchText("Trinity", List.of("hendrik_2")));
		assertNull(TableRows.INSTANCE.searchText(null, List.of()));
	}

	@Test
	void aRuleLinkedToAnOldIdHidesTheEntryItWasMergedInto() {
		// The picker hides entries a rule already uses; a rule on "fred" uses the Forge Foreman (REQ-UI-11).
		NpcList npcs = bundled(NpcTable.SPEC);
		Set<String> used = TableRows.usedIds(java.util.Arrays.asList("fred", "bestiary", "gone", null), id -> {
			NpcDatabaseEntry entry = npcs.byId(id);
			return entry == null ? null : entry.id;
		});
		assertEquals(Set.of("forge_foreman", "bramass_beastslayer", "gone"), used);
	}

	private static void assertPosition(NpcDatabaseEntry npc, int x, int y, int z) {
		assertEquals(List.of((double) x, (double) y, (double) z), List.of(npc.x, npc.y, npc.z), npc.id);
	}

	@Test
	void theMobCorrectionsAreInTheData() {
		MobList mobs = bundled(MobTable.SPEC);
		assertEquals(2, rawTable("npcdb/mobs.json").get("dataVersion").getAsInt(), "T3.8b's corrections are data version 2");
		// REQ-NPCDB-08: exactly the four invisible-by-design mobs are flagged; Shadow Assassin is only invisible at times.
		Set<String> invisible = mobs.getEntries().stream().filter(mob -> mob.invisible).map(mob -> mob.id).collect(Collectors.toSet());
		assertEquals(Set.of("ghost", "fels", "sneaky_creeper", "invisibug"), invisible);
		assertFalse(mobs.byId("shadow_assassin").invisible);
		// AC-NPCDB-06: at least one "Glacite Mineshafts" group; each entry there matches a Glacite Tunnels mob too.
		List<MobDatabaseEntry> shafts = mobs.getEntries().stream().filter(mob -> mob.island.equals("Glacite Mineshafts")).toList();
		// The Glacite mobs NEU tags "mineshaft", and Littlefoot ("Littlefoot's Den"); not the Ice Wall's Glacite Walker.
		assertEquals(Set.of("glacite_bowman_mineshafts", "glacite_caver_mineshafts", "glacite_mage_mineshafts", "glacite_mutt_mineshafts",
				"littlefoot_mineshafts"), shafts.stream().map(mob -> mob.id).collect(Collectors.toSet()));
		// Named apart from their Dwarven Mines twins, so the two rule cards can be told apart.
		assertTrue(shafts.stream().allMatch(mob -> mob.displayName.endsWith(" (Mineshafts)")), shafts.toString());
		for (MobDatabaseEntry mob : shafts) {
			assertTrue(mobs.getEntries().stream().anyMatch(other -> other != mob && other.island.equals("Dwarven Mines") && other.matchText.equals(mob.matchText)),
					mob.id + " is also listed under Dwarven Mines");
		}
	}

	@Test
	void fixedNpcsHaveIntegerPositionsInsideTheWorld() {
		// AC-NPCDB-05: integer coordinates, y in [-64, 320) (the schema), and none at (0,0,0).
		for (NpcDatabaseEntry npc : bundled(NpcTable.SPEC).getEntries()) {
			if (npc.fixed) {
				for (Double c : List.of(npc.x, npc.y, npc.z)) {
					assertEquals(Math.rint(c), c, npc.id);
				}
				assertTrue(npc.y >= -64 && npc.y < 320, npc.id);
				assertFalse(npc.x == 0 && npc.y == 0 && npc.z == 0, npc.id + " at (0,0,0)");
			} else {
				assertTrue(npc.matchText != null && !npc.matchText.isBlank(), npc.id);
			}
		}
	}

	@Test
	void theMobTableHoldsTheFrozenListWithTheHiddenJerriesGroup() {
		MobList mobs = bundled(MobTable.SPEC);
		// The 308 frozen entries plus the 5 Glacite mobs also listed under "Glacite Mineshafts" (T3.8b).
		assertEquals(313, mobs.getEntries().size());
		Set<String> jerries = mobs.getEntries().stream().filter(mob -> mob.island.equals("Hidden Jerries")).map(mob -> mob.id).collect(Collectors.toSet());
		assertEquals(Set.of("blue_jerry", "green_jerry", "purple_jerry", "golden_jerry"), jerries);
		assertTrue(mobs.getEntries().stream().noneMatch(mob -> mob.island.equals("Jerry")), "no mob is gated to Jerry's Workshop by name collision");
		// REQ-NPCDB-08: the invisible-by-design mobs stay in the data (T3.8b flags them).
		for (String id : List.of("ghost", "fels", "sneaky_creeper", "invisibug")) {
			assertNotNull(mobs.byId(id), id);
		}
	}

	@Test
	void everyIslandIsATrackerIslandOrAnUngatedGroup() {
		Set<String> islands = Islands.all().stream().map(Island::name).collect(Collectors.toSet());
		for (NpcDatabaseEntry npc : bundled(NpcTable.SPEC).getEntries()) {
			assertTrue(islands.contains(npc.island), npc.id + ": " + npc.island);
		}
		for (MobDatabaseEntry mob : bundled(MobTable.SPEC).getEntries()) {
			assertTrue(islands.contains(mob.island) || MobTable.UNGATED_GROUPS.contains(mob.island), mob.id + ": " + mob.island);
		}
		assertTrue(MobTable.UNGATED_GROUPS.stream().noneMatch(islands::contains), "an ungated group is never named like an island");
	}

	@Test
	void theSchemasNameExactlyTheTrackerIslandsAndTheUngatedGroups() throws IOException {
		// EC-NPCDB-03: the build-time validator rejects an island the location module does not know.
		Set<String> islands = Islands.all().stream().map(Island::name).collect(Collectors.toCollection(LinkedHashSet::new));
		assertEquals(islands, schemaIslands("npcdb.npcs"));
		Set<String> mobIslands = new HashSet<>(islands);
		mobIslands.addAll(MobTable.UNGATED_GROUPS);
		assertEquals(mobIslands, schemaIslands("npcdb.mobs"));
	}

	private static Set<String> schemaIslands(String table) throws IOException {
		JsonObject schema = JsonParser.parseString(Files.readString(PROJECT.resolve("data-schemas/" + table + ".schema.json"))).getAsJsonObject();
		JsonArray values = schema.getAsJsonObject("properties").getAsJsonObject("rows").getAsJsonObject("items")
				.getAsJsonObject("properties").getAsJsonObject("island").getAsJsonArray("enum");
		return values.asList().stream().map(value -> value.getAsString()).collect(Collectors.toSet());
	}

	@Test
	void aLookupFollowsAliases() {
		// REQ-NPCDB-07(d), AC-NPCDB-08: a merged entry's old id still finds it, so its rules follow its position.
		List<String> warnings = new ArrayList<>();
		NpcList npcs = load(NpcTable.SPEC, synthetic("npcdb.npcs", "MIT", """
				[{"id":"hendrik","displayName":"Hendrik","island":"Jerry","fixed":true,"x":16,"y":62,"z":-31,"aliases":["hendrik_2"]},
				 {"id":"trinity","displayName":"Trinity","island":"Catacombs","fixed":false,"matchText":"Trinity"}]"""), warnings);
		assertSame(npcs.byId("hendrik"), npcs.byId("hendrik_2"));
		assertEquals(16.0, npcs.byId("hendrik_2").x);
		assertNull(npcs.byId("gone"), "a missing id keeps the rule's stored values (EC-NPCDB-01)");
		assertNull(npcs.byId(null));
		assertEquals(2, npcs.getEntries().size(), "an alias is no entry of its own");
		assertEquals(List.of(), warnings);
	}

	@Test
	void anAliasThatClashesIsIgnoredWithAWarning() {
		List<String> warnings = new ArrayList<>();
		MobList mobs = load(MobTable.SPEC, synthetic("npcdb.mobs", "CC0-1.0", """
				[{"id":"zealot","displayName":"Zealot","island":"The End","matchText":"Zealot","aliases":["enderman"]},
				 {"id":"enderman","displayName":"Enderman","island":"The End","matchText":"Enderman"}]"""), warnings);
		assertEquals("enderman", mobs.byId("enderman").id, "an id wins over another entry's alias");
		assertEquals(1, warnings.size(), warnings.toString());
	}

	@Test
	void aRowWithoutARequiredFieldIsSkippedAndTheRestLoad() {
		// AC-DATA-05 [A]: one WARN names the table and the row; no exception escapes.
		List<String> warnings = new ArrayList<>();
		MobList mobs = load(MobTable.SPEC, synthetic("npcdb.mobs", "CC0-1.0", """
				[{"id":"zealot","displayName":"Zealot","island":"The End"},
				 {"id":"enderman","displayName":"Enderman","island":"The End","matchText":"Enderman"}]"""), warnings);
		assertEquals(List.of("enderman"), mobs.getEntries().stream().map(mob -> mob.id).toList());
		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("npcdb.mobs") && warnings.get(0).contains("zealot"), warnings.get(0));
	}

	@Test
	void theMainSourceSetHasNoRemoteNpcOrMobSource() throws IOException {
		// AC-NPCDB-02 [R], AC-NPCDB-03 [R]: no gist URL in code, no raw gist URL anywhere (a table's sources may name
		// the gist page it was converted from), and no fetch helper left for a remote load.
		List<String> hits = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(PROJECT.resolve("src/main"))) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				String name = file.toString();
				boolean code = name.endsWith(".java") || name.endsWith(".kt");
				if (!code && !name.endsWith(".json")) {
					continue;
				}
				String text = Files.readString(file);
				if (text.contains("gist.githubusercontent.com") || code && (text.contains("gist.github.com") || text.contains("fetchText"))) {
					hits.add(name);
				}
			}
		}
		assertEquals(List.of(), hits);
	}
}
