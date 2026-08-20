package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class ModKeybinds {
	private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.k8bas_skyblock_utility.mob_highlighter_toggle",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			K8basSkyblockUtilityClient.KEY_CATEGORY));

	private ModKeybinds() {
	}

	public static void register(MobHighlighterModule module) {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_KEY.consumeClick()) {
				module.setEnabled(!module.isEnabled());
			}
		});
	}
}
