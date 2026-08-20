package com.kesuhi.skyblockhighlighter.keybind;

import com.kesuhi.skyblockhighlighter.SkyblockMobHighlighterClient;
import com.kesuhi.skyblockhighlighter.config.ConfigManager;
import com.kesuhi.skyblockhighlighter.config.ModConfig;
import com.kesuhi.skyblockhighlighter.highlight.HighlightManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ModKeybinds {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(SkyblockMobHighlighterClient.MOD_ID, "general"));

	private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.skyblockhighlighter.toggle",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			CATEGORY));

	private ModKeybinds() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_KEY.consumeClick()) {
				ModConfig config = ConfigManager.get();
				config.modEnabled = !config.modEnabled;
				ConfigManager.save();
				HighlightManager.rebuild();
			}
		});
	}
}
