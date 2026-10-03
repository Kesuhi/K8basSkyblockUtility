package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.Toggle;

import java.util.List;

/**
 * The General category's declarations (T2.1): Updates and Keybinds. Interface and HUD follow in T2.4d
 * and T2.8b. The key names are compile-time constants, so this loads no Minecraft class.
 */
public final class GeneralOptions {
	private GeneralOptions() {
	}

	public static List<Card> cards(GeneralConfig config) {
		Toggle updateCheck = Toggle.of("updates.check", "general.autoUpdateCheckEnabled",
				new OptionText("Check for updates (notify)", "Tells you in chat when a newer version is out. Nothing is installed.",
						"Asks GitHub (api.github.com) for new releases at most 4 times a day; after a successful check, not again for 6 "
								+ "hours. Tells you in chat once per session when a newer version is out. Nothing is downloaded or installed.",
						List.of("update", "version", "github", "release")),
				true, Binding.of(() -> config.autoUpdateCheckEnabled, value -> config.autoUpdateCheckEnabled = value));
		List<Option> keys = List.of(
				Keybind.of("keybinds.open_settings", SettingsKeybind.NAME,
						new OptionText("Open Settings Key", "Opens this screen.", "", List.of("keybind", "hotkey", "settings"))),
				Keybind.of("keybinds.mob_highlighter_toggle", com.k8bas.skyblockutility.module.mobhighlighter.ModKeybinds.NAME,
						new OptionText("Toggle Mob Highlighter Key", "Turns Mob Highlighter on or off.", "", List.of("keybind", "hotkey"))),
				Keybind.of("keybinds.npc_search_toggle", com.k8bas.skyblockutility.module.npcsearch.ModKeybinds.NAME,
						new OptionText("Toggle NPC Search Key", "Turns NPC Search on or off.", "", List.of("keybind", "hotkey"))));
		return List.of(
				new Card("keybinds", Category.GENERAL, "Keybinds", null, keys),
				new Card("updates", Category.GENERAL, "Updates", updateCheck, List.of()));
	}
}
