package com.k8bas.skyblockutility.config.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.module.npcsearch.WaypointColors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration step 3 (T3.4b, REQ-NPCWP-09, R8): AC-NPCWP-02 [A]. The fixture holds the user's five 1.0.1 rules
 * (Trinity, Tomioka and Duncan moving; Udel and Researcher Timmy fixed; all on the old green) plus a fixed rule
 * in red.
 */
class BeamColorOverrideTest {
	private static final String FIXTURE = "/fixtures/config-1.0.1-npc-colours.json";

	@TempDir
	Path dir;

	private JsonObject fixture() throws IOException {
		try (InputStream in = getClass().getResourceAsStream(FIXTURE)) {
			return JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static JsonArray rules(JsonObject root) {
		return root.getAsJsonObject("modules").getAsJsonObject("npc_search").getAsJsonArray("rules");
	}

	@Test
	void everyFieldStaysAndOnlyTheRedRuleGetsABeamColour() throws IOException {
		JsonObject before = fixture();
		JsonObject root = fixture();
		Migrator.Result result = ConfigMigrations.MIGRATOR.migrate(root);
		assertTrue(result.ran().contains(3), result.toString());

		JsonArray old = rules(before);
		JsonArray migrated = rules(root);
		assertEquals(old.size(), migrated.size());
		for (int i = 0; i < old.size(); i++) {
			JsonObject was = old.get(i).getAsJsonObject();
			JsonObject now = migrated.get(i).getAsJsonObject();
			for (Map.Entry<String, JsonElement> field : was.entrySet()) {
				assertEquals(field.getValue(), now.get(field.getKey()), was.get("label") + "." + field.getKey() + " is unchanged");
			}
			boolean red = "My red marker".equals(was.get("label").getAsString());
			assertEquals(red, now.has("beamColor"), was.get("label") + ": only the red rule gets a beam colour");
		}
		assertEquals(0xFF0000, migrated.get(5).getAsJsonObject().get("beamColor").getAsInt());
	}

	@Test
	void theLoadedRulesResolveAsAcNpcwp02Says() throws IOException {
		Path file = dir.resolve("k8bas_skyblock_utility.json");
		try (InputStream in = getClass().getResourceAsStream(FIXTURE)) {
			Files.copy(in, file);
		}
		ConfigManager.load(file);
		NpcSearchConfig config = ConfigManager.getModuleSection("npc_search", NpcSearchConfig.class, NpcSearchConfig::new);
		NpcRule red = rule(config.rules, "My red marker");
		NpcRule udel = rule(config.rules, "Udel");
		NpcRule timmy = rule(config.rules, "Researcher Timmy");
		NpcRule trinity = rule(config.rules, "Trinity");
		assertEquals(0xFF0000, WaypointColors.beamColor(red, config.islandBeamColors), "the red rule's beam is red");
		assertNull(udel.beamColor, "a rule on the old green follows its island");
		assertEquals(WaypointColors.ISLAND_DEFAULTS.get("Crimson Isle"), WaypointColors.beamColor(udel, config.islandBeamColors), "Udel: the Crimson Isle colour");
		assertEquals(WaypointColors.ISLAND_DEFAULTS.get("Lotus Atoll"), WaypointColors.beamColor(timmy, config.islandBeamColors));
		assertEquals(BeamColorOverride.OLD_DEFAULT, trinity.color, "Trinity's glow colour stays");
		assertNull(trinity.beamColor);
		ConfigManager.flush();
	}

	@Test
	void movingRulesAnOwnBeamAndAlphaAreHandled() {
		JsonObject root = JsonParser.parseString("""
				{"configVersion": 2, "modules": {"npc_search": {"rules": [
				  {"label": "moving", "fixed": false, "color": 16711680},
				  {"label": "own", "fixed": true, "color": 16711680, "beamColor": 255},
				  {"label": "alpha", "fixed": true, "color": -16777216},
				  {"label": "green with alpha", "fixed": true, "color": -16080047},
				  {"label": "no colour", "fixed": true},
				  {"label": "damaged", "fixed": true, "color": 1e100000}]}}}
				""").getAsJsonObject();
		assertEquals(List.of(3), ConfigMigrations.MIGRATOR.migrate(root).ran(), "a 1.2.0 config (version 2) gets step 3 only");
		JsonArray rules = rules(root);
		assertFalse(rules.get(0).getAsJsonObject().has("beamColor"), "a moving rule keeps its colour as glow and gets no beam");
		assertEquals(255, rules.get(1).getAsJsonObject().get("beamColor").getAsInt(), "a beam colour already set is kept");
		assertEquals(0x000000, rules.get(2).getAsJsonObject().get("beamColor").getAsInt(), "the alpha byte is dropped: black");
		assertFalse(rules.get(3).getAsJsonObject().has("beamColor"), "the old green with an alpha byte is still the old green");
		assertFalse(rules.get(4).getAsJsonObject().has("beamColor"));
		assertFalse(rules.get(5).getAsJsonObject().has("beamColor"), "a number no int holds is left alone, not a crash");
		String once = root.toString();
		assertTrue(ConfigMigrations.MIGRATOR.migrate(root).ran().isEmpty());
		assertEquals(once, root.toString(), "a second load changes nothing");
	}

	private static NpcRule rule(List<NpcRule> rules, String label) {
		return rules.stream().filter(rule -> label.equals(rule.label)).findFirst().orElseThrow();
	}
}
