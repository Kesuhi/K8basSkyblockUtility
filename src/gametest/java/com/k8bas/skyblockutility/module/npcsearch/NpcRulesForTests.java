package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;

import java.util.List;

/** For gametests in other packages: NPC Search's live rules. Call on the client thread. */
public final class NpcRulesForTests {
	private NpcRulesForTests() {
	}

	public static void use(List<NpcRule> rules) {
		module().useRulesForTest(rules);
	}

	public static List<NpcRule> rules() {
		return module().rulesForTest();
	}

	/** The NPC Database as if fetched (or failed); returns what it held, to put back. */
	public static List<NpcDatabaseEntry> useDatabase(List<NpcDatabaseEntry> entries, RuleDatabase.State state) {
		List<NpcDatabaseEntry> before = NpcDatabase.entries();
		NpcDatabase.useForTest(entries, state);
		return before;
	}

	public static RuleDatabase.State databaseState() {
		return NpcDatabase.state();
	}

	private static NpcSearchModule module() {
		return ModuleManager.modules().stream().filter(module -> module instanceof NpcSearchModule).map(module -> (NpcSearchModule) module)
				.findFirst().orElseThrow();
	}
}
