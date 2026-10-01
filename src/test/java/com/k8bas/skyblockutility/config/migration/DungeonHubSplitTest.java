package com.k8bas.skyblockutility.config.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Migration step 1 (T1.9b): AC-LOC-02 and AC-GLOW-12 [A]. */
class DungeonHubSplitTest {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@TempDir
	Path dir;

	private static final String VERSION_0 = """
			{"modules": {
			  "mob_highlighter": {"enabled": true, "rules": [
			    {"id": "m1", "label": "Shadow Assassin", "nameMatchMode": "CONTAINS", "namePattern": "Shadow Assassin", "island": "Catacombs"}]},
			  "npc_search": {"enabled": true, "rules": [
			    {"id": "n1", "label": "Croesus", "island": "Catacombs", "fixed": true, "x": -12.5, "y": 121.0, "z": -43.5},
			    {"id": "n2", "label": "Trinity", "island": "Catacombs", "fixed": false, "namePattern": "Trinity"},
			    {"id": "n3", "label": "Tomioka", "island": "Catacombs", "fixed": false, "namePattern": "Tomioka"},
			    {"id": "n4", "label": "Duncan", "island": "Catacombs", "fixed": false, "namePattern": "Duncan"},
			    {"id": "n5", "label": "Elizabeth", "island": "Hub", "fixed": true}]}}}
			""";

	private static String island(JsonObject root, String section, int index) {
		return root.getAsJsonObject("modules").getAsJsonObject(section).getAsJsonArray("rules")
				.get(index).getAsJsonObject().get("island").getAsString();
	}

	@Test
	void onlyFixedNpcRulesOnCatacombsMoveToTheDungeonHub() {
		JsonObject root = JsonParser.parseString(VERSION_0).getAsJsonObject();
		Migrator.Result result = ConfigMigrations.MIGRATOR.migrate(root);

		assertEquals(1, result.ran().getFirst());
		assertEquals("Dungeon Hub", island(root, "npc_search", 0), "fixed Croesus");
		for (int moving = 1; moving <= 3; moving++) {
			assertEquals("Catacombs", island(root, "npc_search", moving), "moving NPCs stay in runs");
		}
		assertEquals("Hub", island(root, "npc_search", 4));
		assertEquals("Catacombs", island(root, "mob_highlighter", 0), "mob rules keep Catacombs");
	}

	@Test
	void aSecondLoadChangesNothing() {
		JsonObject root = JsonParser.parseString(VERSION_0).getAsJsonObject();
		ConfigMigrations.MIGRATOR.migrate(root);
		String once = GSON.toJson(root);
		assertTrue(ConfigMigrations.MIGRATOR.migrate(root).ran().isEmpty());
		assertEquals(once, GSON.toJson(root));
	}

	@Test
	void theUsersThreeDungeonNpcRulesStayEnabled() throws IOException {
		Path file = dir.resolve("k8bas_skyblock_utility.json");
		try (InputStream in = getClass().getResourceAsStream("/fixtures/config-1.0.1-shaped.json")) {
			Files.copy(in, file);
		}
		ConfigManager.load(file);
		List<NpcRule> rules = ConfigManager.getModuleSection("npc_search", NpcSearchConfig.class, NpcSearchConfig::new).rules;
		List<NpcRule> moving = rules.stream().filter(rule -> !rule.fixed && "Catacombs".equals(rule.island)).toList();
		assertEquals(List.of("Trinity", "Tomioka", "Duncan"), moving.stream().map(rule -> rule.label).toList());
		assertTrue(moving.stream().allMatch(rule -> rule.enabled));
		assertEquals("Dungeon Hub", rules.stream().filter(rule -> rule.label.equals("Croesus")).findFirst().orElseThrow().island);
		ConfigManager.flush();
	}

	@Test
	void aFreshInstallHasNoNpcRules() {
		ConfigManager.load(dir.resolve("fresh.json"));
		assertTrue(ConfigManager.getModuleSection("npc_search", NpcSearchConfig.class, NpcSearchConfig::new).rules.isEmpty());
		ConfigManager.flush();
	}
}
