package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class ModKeybinds {
	/** Public so it can also be exposed as a rebindable entry in the General Cloth Config
	 *  category (fillKeybindingField binds to this same KeyMapping instance). */
	public static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.k8bas_skyblock_utility.npc_search_toggle",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			K8basSkyblockUtilityClient.KEY_CATEGORY));

	private ModKeybinds() {
	}

	public static void register(NpcSearchModule module) {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_KEY.consumeClick()) {
				module.setEnabled(!module.isEnabled());
			}
		});
	}
}
