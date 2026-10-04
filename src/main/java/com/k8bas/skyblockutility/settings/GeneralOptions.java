package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.ui.notice.NoticePosition;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.Toggle;

import java.util.List;

/**
 * The General category's declarations (T2.1, T2.4d, T2.8b): Interface (accent, notices), HUD, Keybinds and
 * Updates. The key names are compile-time constants and the HUD editor is opened through the action given, so
 * this loads no Minecraft class.
 */
public final class GeneralOptions {
	private GeneralOptions() {
	}

	/** With an "Edit HUD layout" that does nothing (for tests). */
	public static List<Card> cards(GeneralConfig config) {
		return cards(config, () -> {
		});
	}

	/** @param editHudLayout opens the HUD editor */
	public static List<Card> cards(GeneralConfig config, Runnable editHudLayout) {
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
		List<Option> look = List.of(
				ColorOption.of("interface.accent", "general.accentColor",
						new OptionText("Accent colour", "The colour of selections, focus and section titles in this screen.",
								"Used for the header line, the selected category, focused fields and section titles. Destructive and "
										+ "error colours stay red whatever you pick.",
								List.of("accent", "colour", "color", "theme")),
						GeneralConfig.DEFAULT_ACCENT, false, Binding.of(() -> config.accentColor == null ? GeneralConfig.DEFAULT_ACCENT : config.accentColor & 0xFFFFFF,
								value -> config.accentColor = value & 0xFFFFFF)),
				Choice.of("interface.notice_position", "general.noticePosition",
						new OptionText("Notice position", "Where notices appear on the screen.",
								"Notices are short messages, e.g. that an update is out. Vanilla toasts show top right too and are drawn "
										+ "over ours there.",
								List.of("notice", "toast", "popup", "corner")),
						GeneralConfig.DEFAULT_NOTICE_POSITION, List.of(NoticePosition.values()), NoticePosition::label,
						Binding.of(config::noticePosition, value -> config.noticePosition = value)),
				IntSlider.of("interface.notice_seconds", "general.noticeSeconds",
						new OptionText("Notice duration", "How long a notice stays on screen.", "", List.of("notice", "toast", "time", "seconds")),
						GeneralConfig.DEFAULT_NOTICE_SECONDS, 1, 15, 1, "s", "", Binding.of(config::noticeSeconds, value -> config.noticeSeconds = value)));
		return List.of(
				new Card("interface", Category.GENERAL, "Interface", null, look),
				HudOptions.layoutCard(editHudLayout),
				new Card("keybinds", Category.GENERAL, "Keybinds", null, keys),
				new Card("updates", Category.GENERAL, "Updates", updateCheck, List.of()));
	}
}
