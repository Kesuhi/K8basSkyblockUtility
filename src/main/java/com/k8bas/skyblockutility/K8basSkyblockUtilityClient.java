package com.k8bas.skyblockutility;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterModule;
import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.update.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8basSkyblockUtilityClient implements ClientModInitializer {
	public static final String MOD_ID = "k8bas_skyblock_utility";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Shared by every keybind this mod registers — a Category identifier can only be registered once. */
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "general"));

	@Override
	public void onInitializeClient() {
		ConfigManager.load();

		ModuleManager.register(new MobHighlighterModule());
		// Future modules get registered here, one line each.

		SettingsKeybind.register();
		UpdateChecker.checkInBackgroundIfEnabled();

		LOGGER.info("K8bas Skyblock Utility initialized with {} module(s)", ModuleManager.modules().size());
	}
}
