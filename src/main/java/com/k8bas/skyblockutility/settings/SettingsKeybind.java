package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class SettingsKeybind {
	private static final KeyMapping OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.k8bas_skyblock_utility.open_settings",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			K8basSkyblockUtilityClient.KEY_CATEGORY));

	private SettingsKeybind() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_SETTINGS_KEY.consumeClick()) {
				client.setScreen(SettingsScreenFactory.build(client.screen));
			}
		});
	}
}
