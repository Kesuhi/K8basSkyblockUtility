package com.k8bas.skyblockutility.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.migration.ConfigMigrations;
import com.k8bas.skyblockutility.config.migration.MigrationStep;
import com.k8bas.skyblockutility.config.migration.Migrator;
import com.k8bas.skyblockutility.highlight.CompiledRule;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.highlight.NameMatcher;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T1.7b: loading never loses settings (SPEC REQ-CFG-01, -03, -06, -07, -08, -11). */
class ConfigSafeLoadTest {
	@TempDir
	Path dir;

	private static final ConfigManager.Disk REAL_DISK = ConfigManager.disk;

	@AfterEach
	void restoreDisk() {
		ConfigManager.disk = REAL_DISK;
	}

	private static ConfigManager.Disk disk(int failingReads, boolean backupsFail) {
		AtomicInteger reads = new AtomicInteger();
		return new ConfigManager.Disk() {
			@Override
			public byte[] read(Path path) throws IOException {
				if (reads.incrementAndGet() <= failingReads) {
					throw new IOException("The process cannot access the file because it is being used by another process");
				}
				return REAL_DISK.read(path);
			}

			@Override
			public void writeBackup(Path path, byte[] bytes) throws IOException {
				if (backupsFail) {
					throw new IOException("disk full");
				}
				REAL_DISK.writeBackup(path, bytes);
			}
		};
	}

	/** Review I-1 (G1): a locked file is retried, and if it stays unreadable it is never overwritten. */
	@Test
	void anUnreadableFileIsNeverOverwritten() throws IOException {
		byte[] original = Files.readAllBytes(fixture());
		ConfigManager.disk = disk(Integer.MAX_VALUE, false);
		ConfigManager.load(file());
		assertEquals(64, ConfigManager.general().mobScanRangeBlocks, "defaults are used");
		mobs();
		npcs();
		ConfigManager.general().mobScanRangeBlocks = 10;
		ConfigManager.save();
		ConfigManager.flush();

		assertArrayEquals(original, Files.readAllBytes(file()), "the file is left as it is");
		List<String> notices = ConfigManager.drainNotices();
		assertEquals(1, notices.size(), notices.toString());
		assertTrue(notices.getFirst().contains("could not be read"), notices.getFirst());
	}

	@Test
	void aBrieflyLockedFileIsReadOnARetry() throws IOException {
		fixture();
		ConfigManager.disk = disk(ConfigManager.READ_ATTEMPTS - 1, false);
		ConfigManager.load(file());
		assertEquals(128, ConfigManager.general().mobScanRangeBlocks);
		assertTrue(ConfigManager.drainNotices().isEmpty());
	}

	/** Review I-1 (G1): when the backup a broken file needs cannot be written, the file is kept. */
	@Test
	void aBackupThatFailsStopsSaving() throws IOException {
		byte[] original = "{\"general\": {\"mobScanRangeBlocks\": 128,".getBytes(StandardCharsets.UTF_8);
		Files.write(file(), original);
		ConfigManager.disk = disk(0, true);
		ConfigManager.load(file());
		mobs();
		ConfigManager.save();
		ConfigManager.flush();

		assertArrayEquals(original, Files.readAllBytes(file()));
		assertTrue(backups().isEmpty());
		List<String> notices = ConfigManager.drainNotices();
		assertEquals(1, notices.size(), notices.toString());
		assertTrue(notices.getFirst().contains("left untouched"), notices.getFirst());
	}

	/** Re-review (G1): a save queued before the suspension is not written either. */
	@Test
	void aSaveQueuedBeforeTheSuspensionIsDiscarded() throws IOException {
		byte[] original = ("{\"configVersion\": " + ConfigMigrations.MIGRATOR.currentVersion()
				+ ", \"general\": {\"mobScanRangeBlocks\": 100}, \"modules\": {\"npc_search\": {\"rules\": 5}}}")
				.getBytes(StandardCharsets.UTF_8);
		Files.write(file(), original);
		ConfigManager.disk = disk(0, true);
		ConfigManager.load(file());
		mobs(); // a missing section: its defaults are queued for saving
		npcs(); // a broken section whose backup fails: saving stops
		ConfigManager.flush();
		assertArrayEquals(original, Files.readAllBytes(file()));
	}

	/** The same when the one-time copy before the first migration cannot be written. */
	@Test
	void aMigrationWithoutItsCopyIsNotSaved() throws IOException {
		byte[] original = Files.readAllBytes(fixture());
		ConfigManager.disk = disk(0, true);
		ConfigManager.load(file());
		assertEquals("Dungeon Hub", npcs().rules.get(3).island, "migrated in memory");
		ConfigManager.save();
		ConfigManager.flush();
		assertArrayEquals(original, Files.readAllBytes(file()));
		assertFalse(Files.exists(dir.resolve("k8bas_skyblock_utility.json.v0.bak")));
	}

	private Path file() {
		return dir.resolve("k8bas_skyblock_utility.json");
	}

	private Path write(String content) throws IOException {
		Files.writeString(file(), content, StandardCharsets.UTF_8);
		return file();
	}

	private Path fixture() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/fixtures/config-1.0.1-shaped.json")) {
			Files.copy(in, file());
		}
		return file();
	}

	/** Backups made because something was wrong (not the one-time .v0.bak pre-migration copy). */
	private List<Path> backups() throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(Path::getFileName).map(Path::toString)
					.filter(name -> name.endsWith(".bak") && !name.endsWith(".v0.bak"))
					.sorted().map(dir::resolve).toList();
		}
	}

	private static MobHighlighterConfig mobs() {
		return ConfigManager.getModuleSection("mob_highlighter", MobHighlighterConfig.class, MobHighlighterConfig::new);
	}

	private static NpcSearchConfig npcs() {
		return ConfigManager.getModuleSection("npc_search", NpcSearchConfig.class, NpcSearchConfig::new);
	}

	private static JsonObject json(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	@Test
	void a101ConfigSavesBackEveryValuePlusOnlyTheVersion() throws IOException {
		Path file = fixture();
		JsonObject original = json(file);
		ConfigManager.load(file);
		mobs();
		npcs();
		ConfigManager.save();
		ConfigManager.flush();

		// Explicit nulls load exactly like absent fields and Gson never writes them (1.0.1 used the
		// same Gson settings, so a real 1.0.1 file has none).
		JsonObject expected = withoutNulls(original).getAsJsonObject();
		expected.addProperty(ConfigMigrations.VERSION_FIELD, ConfigMigrations.MIGRATOR.currentVersion());
		// The documented migrated value: step 1 moves the fixed Croesus rule to the Dungeon Hub.
		expected.getAsJsonObject("modules").getAsJsonObject("npc_search").getAsJsonArray("rules").get(3)
				.getAsJsonObject().addProperty("island", "Dungeon Hub");
		// Step 2 drops the automatic-download flag; the check flag keeps its value.
		expected.getAsJsonObject("general").remove("autoUpdateDownloadEnabled");
		JsonObject saved = json(file);
		assertEquals(expected.get("general"), saved.get("general"));
		assertEquals(expected.get("modules"), saved.get("modules"));
		assertEquals(expected.keySet(), saved.keySet(), "no new top-level key");
		assertEquals(ConfigMigrations.MIGRATOR.currentVersion(), saved.get("configVersion").getAsInt());
		assertTrue(backups().isEmpty(), "nothing was wrong");
		assertTrue(Files.exists(dir.resolve("k8bas_skyblock_utility.json.v0.bak")), "the one-time pre-migration copy");
	}

	@Test
	void invalidJsonIsBackedUpBeforeAnythingIsWrittenAndRunsOnDefaults() throws IOException {
		byte[] original = "{\"general\": {\"mobScanRangeBlocks\": 128,".getBytes(StandardCharsets.UTF_8);
		Files.write(file(), original);
		ConfigManager.load(file());

		List<Path> backups = backups();
		assertEquals(1, backups.size());
		assertArrayEquals(original, Files.readAllBytes(backups.get(0)), "backup holds the exact original bytes");
		assertArrayEquals(original, Files.readAllBytes(file()), "config path not yet written");
		assertEquals(64, ConfigManager.general().mobScanRangeBlocks);

		List<String> notices = ConfigManager.drainNotices();
		assertEquals(1, notices.size());
		assertTrue(notices.get(0).contains(backups.get(0).getFileName().toString()));
		assertTrue(ConfigManager.drainNotices().isEmpty(), "the notice is shown once");

		ConfigManager.save();
		ConfigManager.flush();
		assertEquals(64, json(file()).getAsJsonObject("general").get("mobScanRangeBlocks").getAsInt());
	}

	@Test
	void anEmptyFileCountsAsUnreadable() throws IOException {
		write("");
		ConfigManager.load(file());
		assertEquals(1, backups().size());
		assertEquals(64, ConfigManager.general().mobScanRangeBlocks);
	}

	@Test
	void oneBrokenModuleSectionOnlyResetsThatModule() throws IOException {
		JsonObject root = json(fixture());
		root.getAsJsonObject("modules").getAsJsonObject("npc_search").addProperty("rules", "not a list");
		write(root.toString());
		ConfigManager.load(file());

		assertEquals(5, mobs().rules.size(), "mob rules load intact");
		assertTrue(npcs().rules.isEmpty(), "NPC Search uses defaults");
		assertEquals(128, ConfigManager.general().mobScanRangeBlocks);
		assertEquals(1, backups().size());
		assertEquals(1, ConfigManager.drainNotices().size());
	}

	@Test
	void invalidValuesAreCleanedUp() throws IOException {
		write("""
				{"general": null,
				 "modules": {
				   "mob_highlighter": {"enabled": true, "rules": [
				     {"label": "no id", "nameMatchMode": "FOO", "namePattern": "Zealot"},
				     {"id": "m2", "label": null, "namePattern": null}]},
				   "npc_search": {"enabled": true, "rules": null}}}
				""");
		ConfigManager.load(file());
		// Review S-4 (G1): null text fields are repaired instead of crashing the renderer.
		assertEquals("New Rule", mobs().rules.get(1).label);
		assertEquals("", mobs().rules.get(1).namePattern);

		assertEquals(64, ConfigManager.general().mobScanRangeBlocks, "general has defaults");
		HighlightRule rule = mobs().rules.get(0);
		assertEquals(NameMatchMode.CONTAINS, rule.nameMatchMode);
		assertNotNull(rule.id);
		assertTrue(NameMatcher.matches(new CompiledRule(rule), "[Lv55] Zealot"), "the cleaned rule evaluates");
		assertTrue(npcs().rules.isEmpty());
		assertTrue(backups().isEmpty(), "clean-ups are not failures");

		ConfigManager.flush();
		String savedId = json(file()).getAsJsonObject("modules").getAsJsonObject("mob_highlighter")
				.getAsJsonArray("rules").get(0).getAsJsonObject().get("id").getAsString();
		assertEquals(rule.id, savedId, "the generated id is saved");
	}

	@Test
	void aNegativeScanRangeBecomesUnlimited() throws IOException {
		write("{\"general\": {\"mobScanRangeBlocks\": -5}}");
		ConfigManager.load(file());
		assertEquals(0, ConfigManager.general().mobScanRangeBlocks);
	}

	@Test
	void aFileFromANewerVersionIsBackedUpBeforeTheFirstSave() throws IOException {
		int newer = ConfigMigrations.MIGRATOR.currentVersion() + 1;
		byte[] original = ("{\"configVersion\": " + newer + ", \"general\": {\"futureFlag\": true}}").getBytes(StandardCharsets.UTF_8);
		Files.write(file(), original);
		ConfigManager.load(file());
		ConfigManager.save();
		ConfigManager.flush();

		List<Path> backups = backups();
		assertEquals(1, backups.size());
		assertTrue(backups.get(0).getFileName().toString().contains(".v" + newer + "-"));
		assertArrayEquals(original, Files.readAllBytes(backups.get(0)));
	}

	@Test
	void theFirstDataChangingMigrationKeepsAOneTimeV0Copy() throws IOException {
		byte[] original = "{\"general\": {\"mobScanRangeBlocks\": 128}}".getBytes(StandardCharsets.UTF_8);
		Files.write(file(), original);
		ConfigManager.load(file(), new Migrator("configVersion", List.of(step(1))));
		ConfigManager.flush();
		Path v0 = dir.resolve("k8bas_skyblock_utility.json.v0.bak");
		assertArrayEquals(original, Files.readAllBytes(v0));
		assertEquals(1, json(file()).get("configVersion").getAsInt());

		ConfigManager.load(file(), new Migrator("configVersion", List.of(step(1), step(2))));
		ConfigManager.flush();
		assertArrayEquals(original, Files.readAllBytes(v0), "a later migration leaves the v0 copy alone");
		assertEquals(2, json(file()).get("configVersion").getAsInt());
	}

	@Test
	void unknownModuleSectionsAreKept() throws IOException {
		write("{\"modules\": {\"future_module\": {\"answer\": 42, \"list\": [1, 2]}}}");
		ConfigManager.load(file());
		mobs();
		ConfigManager.save();
		ConfigManager.flush();
		assertEquals(JsonParser.parseString("{\"answer\": 42, \"list\": [1, 2]}"),
				json(file()).getAsJsonObject("modules").get("future_module"));
	}

	@Test
	void specialCharactersRoundTrip() throws IOException {
		write("{\"modules\": {\"npc_search\": {\"enabled\": true, \"rules\": [{\"id\": \"a\", \"label\": \"Spider's Den §c Größe\", \"island\": \"Spider's Den\"}]}}}");
		ConfigManager.load(file());
		npcs();
		ConfigManager.save();
		ConfigManager.flush();
		ConfigManager.load(file());
		assertEquals("Spider's Den §c Größe", npcs().rules.get(0).label);
		assertEquals("Spider's Den", npcs().rules.get(0).island);
		assertFalse(Files.readString(file()).isEmpty());
	}

	private static JsonElement withoutNulls(JsonElement element) {
		if (element.isJsonObject()) {
			JsonObject copy = new JsonObject();
			element.getAsJsonObject().entrySet().stream()
					.filter(entry -> !entry.getValue().isJsonNull())
					.forEach(entry -> copy.add(entry.getKey(), withoutNulls(entry.getValue())));
			return copy;
		}
		if (element.isJsonArray()) {
			JsonArray copy = new JsonArray();
			element.getAsJsonArray().forEach(item -> copy.add(withoutNulls(item)));
			return copy;
		}
		return element;
	}

	private static MigrationStep step(int number) {
		return new MigrationStep() {
			public int number() {
				return number;
			}

			public String description() {
				return "test step " + number;
			}

			public void apply(JsonObject root) {
				root.addProperty("migratedBy" + number, true);
			}
		};
	}
}
