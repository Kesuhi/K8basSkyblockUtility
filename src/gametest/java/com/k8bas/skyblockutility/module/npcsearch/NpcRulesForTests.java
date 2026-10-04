package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.module.ModuleManager;

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

	private static NpcSearchModule module() {
		return ModuleManager.modules().stream().filter(module -> module instanceof NpcSearchModule).map(module -> (NpcSearchModule) module)
				.findFirst().orElseThrow();
	}
}
