package com.k8bas.skyblockutility.ui.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.module.mobhighlighter.MobRulesForTests;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcRulesForTests;
import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.ui.option.Category;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AC-UI-15 [A] (T2.6), with the settings screen as the only settings UI: the 1.0.1-shaped rules, a
 * hand-edited {@code mobScanRangeBlocks} of 200 and the mod's three bound keys go through opening every
 * category and closing the screen without an edit. Every key of the file before is in the file after with
 * an equal value (added keys aside), every key of the migrated fixture's rules is too, the range is still
 * 200 (EC-UI-05) and the three key lines of {@code options.txt} are unchanged (AC-CFG-12). Loading and
 * saving the 1.0.1 file itself is ConfigSafeLoadTest's.
 */
public class OpenCloseGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String KEY_PREFIX = "key_key.k8bas_skyblock_utility.";

	@Override
	public void runTest(ClientGameTestContext context) {
		List<HighlightRule> mobsBefore = context.computeOnClient(client -> MobRulesForTests.rules());
		List<NpcRule> npcsBefore = context.computeOnClient(client -> NpcRulesForTests.rules());
		int rangeBefore = context.computeOnClient(client -> ConfigManager.general().mobScanRangeBlocks);
		List<KeyMapping> keys = List.of(SettingsKeybind.OPEN_SETTINGS_KEY, com.k8bas.skyblockutility.module.mobhighlighter.ModKeybinds.TOGGLE_KEY,
				com.k8bas.skyblockutility.module.npcsearch.ModKeybinds.TOGGLE_KEY);
		JsonObject fixture = RuleCardsGameTest.migratedFixture(context);
		JsonObject fixtureModules = fixture.getAsJsonObject("modules");
		Gson gson = new Gson();
		List<HighlightRule> mobs = List.of(gson.fromJson(fixtureModules.getAsJsonObject("mob_highlighter").get("rules"), HighlightRule[].class));
		List<NpcRule> npcs = List.of(gson.fromJson(fixtureModules.getAsJsonObject("npc_search").get("rules"), NpcRule[].class));
		mobs.forEach(HighlightRule::normalize);
		npcs.forEach(NpcRule::normalize);
		try {
			context.runOnClient(client -> {
				MobRulesForTests.use(mobs);
				NpcRulesForTests.use(npcs);
				ConfigManager.general().mobScanRangeBlocks = 200;
				int[] glfw = {GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3};
				for (int i = 0; i < keys.size(); i++) {
					keys.get(i).setKey(InputConstants.Type.KEYSYM.getOrCreate(glfw[i]));
				}
				KeyMapping.resetMapping();
				client.options.save();
				// What a session would have written: the rules in their sections, then the file.
				MobRulesForTests.storeSection();
				NpcRulesForTests.storeSection();
				ConfigManager.save();
				ConfigManager.flush();
			});
			JsonObject before = configFile(context);
			List<String> keyLinesBefore = keyLines(context);
			check(keyLinesBefore.size() == 3, "the three key lines are in options.txt: " + keyLinesBefore);

			context.setScreen(() -> new ConfigScreen(null));
			context.waitForScreen(ConfigScreen.class);
			for (Category category : Category.values()) {
				context.runOnClient(client -> ((ConfigScreen) client.gui.screen()).select(category));
				context.waitTicks(2);
			}
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			check(!(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen), "Esc closed the screen");
			context.runOnClient(client -> {
				ConfigManager.flush();
				// As when the game exits.
				client.options.save();
			});
			JsonObject after = configFile(context);

			List<String> differences = new ArrayList<>();
			contained(before, after, "", differences);
			check(differences.isEmpty(), "every key of the file is still there with its value: " + differences);
			for (String module : List.of("mob_highlighter", "npc_search")) {
				contained(fixtureModules.getAsJsonObject(module).get("rules"), after.getAsJsonObject("modules").getAsJsonObject(module).get("rules"),
						module + ".rules", differences);
			}
			check(differences.isEmpty(), "every key of the 1.0.1 rules is in the file with its value: " + differences);
			int range = after.getAsJsonObject("general").get("mobScanRangeBlocks").getAsInt();
			check(range == 200, "the hand-edited range stays 200 (EC-UI-05): " + range);
			List<String> keyLinesAfter = keyLines(context);
			check(keyLinesAfter.equals(keyLinesBefore), "the three key lines are unchanged: " + keyLinesBefore + " -> " + keyLinesAfter);
			LOGGER.info("open and close: {} mob and {} NPC rules of the 1.0.1 fixture, the range 200 and the key lines {} came through unchanged",
					mobs.size(), npcs.size(), keyLinesAfter);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				MobRulesForTests.use(mobsBefore);
				NpcRulesForTests.use(npcsBefore);
				MobRulesForTests.storeSection();
				NpcRulesForTests.storeSection();
				ConfigManager.general().mobScanRangeBlocks = rangeBefore;
				keys.forEach(key -> key.setKey(InputConstants.UNKNOWN));
				KeyMapping.resetMapping();
				client.options.save();
				ConfigManager.save();
				ConfigManager.flush();
			});
		}
	}

	/**
	 * Adds to {@code out} each key of {@code expected} that {@code actual} lacks or holds with another value;
	 * a key {@code expected} holds as null counts as absent (Gson writes no nulls), and added keys are fine.
	 */
	private static void contained(JsonElement expected, JsonElement actual, String path, List<String> out) {
		if (expected.isJsonObject() && actual != null && actual.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject().entrySet()) {
				if (!entry.getValue().isJsonNull()) {
					contained(entry.getValue(), actual.getAsJsonObject().get(entry.getKey()), path + "." + entry.getKey(), out);
				}
			}
		} else if (expected.isJsonArray() && actual != null && actual.isJsonArray()) {
			JsonArray want = expected.getAsJsonArray();
			JsonArray have = actual.getAsJsonArray();
			if (want.size() != have.size()) {
				out.add(path + " has " + have.size() + " entries, not " + want.size());
				return;
			}
			for (int i = 0; i < want.size(); i++) {
				contained(want.get(i), have.get(i), path + "[" + i + "]", out);
			}
		} else if (actual == null || !expected.equals(actual)) {
			out.add(path + ": " + expected + " -> " + actual);
		}
	}

	private static JsonObject configFile(ClientGameTestContext context) {
		Path file = context.computeOnClient(client -> FabricLoader.getInstance().getConfigDir().resolve("k8bas_skyblock_utility.json"));
		try {
			return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		} catch (IOException e) {
			throw new AssertionError("cannot read " + file, e);
		}
	}

	private static List<String> keyLines(ClientGameTestContext context) {
		Path file = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("options.txt"));
		try {
			return Files.readAllLines(file).stream().filter(line -> line.startsWith(KEY_PREFIX)).sorted().toList();
		} catch (IOException e) {
			throw new AssertionError("cannot read " + file, e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
