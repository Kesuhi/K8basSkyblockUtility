package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class SettingsKeybind {
	/** Public so it can be exposed as a rebindable entry in the General Cloth Config category
	 *  too, not just vanilla's Controls screen (Cloth Config's fillKeybindingField binds to the
	 *  same KeyMapping instance, so both stay in sync automatically). */
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
				// Deferred to next tick: opening a screen synchronously from the same input
				// event that triggered it (also true for the /ksu command) risks the screen
				// swallowing a stray leftover keystroke and closing itself immediately.
				client.execute(() -> client.gui.setScreen(SettingsScreenFactory.build(client.gui.screen())));
			}
		});
	}
}
