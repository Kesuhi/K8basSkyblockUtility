package com.k8bas.skyblockutility.module.mobhighlighter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** For gametests in other packages: Mob Highlighter's live rules. Call on the client thread. */
public final class MobRulesForTests {
	private MobRulesForTests() {
	}

	public static void use(List<HighlightRule> rules) {
		module().useRulesForTest(rules);
	}

	public static List<HighlightRule> rules() {
		return module().rulesForTest();
	}

	/** The rules as a restart would read them: from the Mob Highlighter section of the file on disk. */
	public static List<HighlightRule> useRulesFromFile() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("k8bas_skyblock_utility.json");
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			MobHighlighterConfig section = new Gson().fromJson(root.getAsJsonObject("modules").get(MobHighlighterModule.ID), MobHighlighterConfig.class);
			use(section.rules);
			return section.rules;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The Mob Database as if fetched (or failed); returns what it held, to put back. */
	public static List<MobDatabaseEntry> useDatabase(List<MobDatabaseEntry> entries, RuleDatabase.State state) {
		List<MobDatabaseEntry> before = MobDatabase.entries();
		MobDatabase.useForTest(entries, state);
		return before;
	}

	public static RuleDatabase.State databaseState() {
		return MobDatabase.state();
	}

	private static MobHighlighterModule module() {
		return ModuleManager.modules().stream().filter(module -> module instanceof MobHighlighterModule).map(module -> (MobHighlighterModule) module)
				.findFirst().orElseThrow();
	}
}
