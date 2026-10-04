package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.module.ModuleManager;

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

	private static MobHighlighterModule module() {
		return ModuleManager.modules().stream().filter(module -> module instanceof MobHighlighterModule).map(module -> (MobHighlighterModule) module)
				.findFirst().orElseThrow();
	}
}
