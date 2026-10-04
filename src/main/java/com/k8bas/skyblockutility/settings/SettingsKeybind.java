package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.ui.screen.ConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class SettingsKeybind {
	/** The name options.txt stores the binding under; never changes (AC-CFG-12). Also declared as an option (GeneralOptions). */
	public static final String NAME = "key.k8bas_skyblock_utility.open_settings";
	public static final KeyMapping OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			NAME,
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			K8basSkyblockUtilityClient.KEY_CATEGORY));

	private SettingsKeybind() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_SETTINGS_KEY.consumeClick()) {
				// Queued for the next frame, as /ksu does, so the key press that opened it is spent.
				client.schedule(() -> client.gui.setScreen(new ConfigScreen(client.gui.screen())));
			}
		});
	}
}
