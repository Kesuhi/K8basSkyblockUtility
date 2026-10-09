package com.k8bas.skyblockutility.hook;

import java.util.List;

/**
 * The plugin of {@code k8bas_skyblock_utility.mixins.json}, the one optional cosmetic mixin config (AD-9).
 * T3.5 (bobber), T3.6 (particle observer) and T3.7 (drill) add their hooks here.
 */
public final class MainHookPlugin extends HookPlugin {
	@Override
	protected List<Hook> hooks() {
		// Plain strings: the plugin runs before the mods are initialised, so it loads no feature class (BobberFixModule ids).
		String fishingHook = "net.minecraft.world.entity.projectile.FishingHook";
		String mixin = "com.k8bas.skyblockutility.mixin.FishingHookMixin";
		return List.of(
				new Hook("bobber.link", mixin, fishingHook, "k8bas$skipTimerStand", ""),
				new Hook("bobber.lava", mixin, fishingHook, "k8bas$floatOnLava", ""));
	}
}
