package com.kesuhi.skyblockhighlighter;

import com.kesuhi.skyblockhighlighter.config.ConfigManager;
import com.kesuhi.skyblockhighlighter.highlight.HighlightManager;
import com.kesuhi.skyblockhighlighter.keybind.ModKeybinds;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyblockMobHighlighterClient implements ClientModInitializer {
	public static final String MOD_ID = "skyblockhighlighter";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		HighlightManager.rebuild();
		ModKeybinds.register();
		LOGGER.info("Skyblock Mob Highlighter initialized");
	}
}
