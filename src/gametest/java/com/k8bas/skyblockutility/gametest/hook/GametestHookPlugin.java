package com.k8bas.skyblockutility.gametest.hook;

import com.k8bas.skyblockutility.hook.Hook;
import com.k8bas.skyblockutility.hook.HookPlugin;

import java.util.List;

/**
 * The test hooks of {@code k8bas_skyblock_utility_gametest.mixins.json} (T3.0f, AC-BOB-07 missing-target part):
 * two that go in; one whose call site does not exist; one whose target class does not exist; one that goes in
 * but is lost because another mixin on its class fails late ({@code ForeignBadMixin}, standing in for another
 * mod, has no hook); and one of ours that fails late itself, costing {@code ForeignGoodMixin} its change.
 */
public final class GametestHookPlugin extends HookPlugin {
	private static final String MIXINS = "com.k8bas.skyblockutility.gametest.mixin.";
	private static final String HOOKS = "com.k8bas.skyblockutility.gametest.hook.";

	@Override
	protected List<Hook> hooks() {
		return List.of(
				new Hook("gametest.return", MIXINS + "HookTargetMixin", HOOKS + "HookTarget", "k8bas$doubled", ""),
				new Hook("gametest.wrap", MIXINS + "HookTargetMixin", HOOKS + "HookTarget", "k8bas$plusOne", ""),
				new Hook("gametest.unmatched", MIXINS + "UnmatchedHookMixin", HOOKS + "HookTarget", "k8bas$unmatched", ""),
				new Hook("gametest.missing", MIXINS + "MissingTargetMixin", HOOKS + "NoSuchClass", "k8bas$missing", "Test feature unavailable"),
				new Hook("gametest.reverted", MIXINS + "RevertedTargetMixin", HOOKS + "RevertedTarget", "k8bas$tripled", ""),
				new Hook("gametest.ownlate", MIXINS + "OwnBadLateMixin", HOOKS + "OwnLateTarget", "k8bas$ownBroken", ""));
	}
}
