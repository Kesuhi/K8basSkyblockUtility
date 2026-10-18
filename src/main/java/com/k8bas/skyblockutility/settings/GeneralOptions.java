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
import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.InfoOption;
import com.k8bas.skyblockutility.update.UpdateChannel;
import com.k8bas.skyblockutility.update.UpdatesPanel;

import java.util.List;

/**
 * The General category's declarations (T2.1, T2.4d, T2.8b, T2.9d): Interface (accent, smooth corners, notices), HUD, Keybinds and
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
		return cards(config, editHudLayout, UpdatesPanel.NONE);
	}

	/** @param updates what the Updates card shows and does (T4.4b) */
	public static List<Card> cards(GeneralConfig config, Runnable editHudLayout, UpdatesPanel updates) {
		Toggle updateCheck = Toggle.of("updates.check", "general.autoUpdateCheckEnabled",
				new OptionText("Check for updates (notify)", "Tells you when a newer version is out. It installs only after you confirm it.",
						"Asks GitHub (api.github.com) for new releases at startup and at your first server join of a session, at most 4 times "
								+ "a day; after a successful check, not again for 6 hours. A newer version shows a notice and, once per session, a "
								+ "chat line. Nothing is downloaded until you click Download & install on restart.",
						List.of("update", "version", "github", "release")),
				true, Binding.of(() -> config.autoUpdateCheckEnabled, value -> config.autoUpdateCheckEnabled = value));
		List<Option> updateRows = List.of(
				new InfoOption("updates.version", new OptionText("Version", "The version you run.", "", List.of("version")), updates::version),
				new InfoOption("updates.latest", new OptionText("Latest version", "The newest version found for your Minecraft.",
						"Marked (skipped) when you skipped it, (beta) when it is a pre-release.", List.of("latest", "newest", "skipped")),
						updates::latest),
				new InfoOption("updates.last_check", new OptionText("Last check", "When GitHub last answered.", "", List.of("last check", "time")),
						updates::lastCheck),
				new InfoOption("updates.status", new OptionText("Status", "", "A pending install, the last button's answer, or the last check's.",
						List.of("status", "pending")), updates::status, true),
				Choice.of("updates.channel", "general.updateChannel",
						new OptionText("Channel", "Stable releases only, or betas too.",
								"Beta also offers pre-releases; the highest version wins. Untouched, it follows the build you run: Beta on a beta "
										+ "build, else Stable.",
								List.of("channel", "beta", "pre-release", "stable")),
						updates.defaultChannel(), List.of(UpdateChannel.values()), UpdateChannel::label,
						// Read as its default while untouched; written only when you pick one (REQ-UPD-07).
						Binding.of(() -> config.updateChannel != null ? config.updateChannel : updates.defaultChannel(),
								value -> {
									config.updateChannel = value;
									updates.channelChanged();
								})),
				new ActionOption("updates.check_now", new OptionText("Check for updates", "Asks GitHub now, at most once a minute.", "",
						List.of("check now", "update")), "Check now", false, updates::checkNow),
				new ActionOption("updates.install", new OptionText("Install update", "Shows it first; it installs when you quit.",
						"", List.of("install", "update")), "Install", false, updates::install),
				new ActionOption("updates.release_page", new OptionText("Open release page", "The update's page on GitHub.", "",
						List.of("release page", "changelog", "github")), "Open", false, updates::openReleasePage),
				new ActionOption("updates.cancel", new OptionText("Cancel pending update", "Nothing is installed when you quit.", "",
						List.of("cancel", "pending")), "Cancel", true, updates::cancelPending));
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
				Toggle.of("interface.smooth_corners", "general.smoothCorners",
						new OptionText("Smooth corners", "Draws rounded corners with soft edges.",
								"Changes only how rounded corners are drawn. Off cuts them from whole pixels, as in 1.2.",
								List.of("smooth", "corners", "rounded", "anti-aliasing", "look")),
						true, Binding.of(config::smoothCorners, value -> config.smoothCorners = value)),
				Toggle.of("interface.animations", "general.animations",
						new OptionText("Animations", "Lets this mod's screens, lists, dialogs and notices move and fade.",
								"Off shows every change at once: the panel opening, the category switch, hover and press, switches, scrolling, lists and "
										+ "dialogs opening and closing, rule cards, the HUD editor's toolbar and hints, and notices. Tooltips still wait "
										+ "before they show, then appear at once.",
								List.of("animations", "motion", "fade", "slide", "reduce motion")),
						true, Binding.of(config::animations, value -> config.animations = value)),
				Choice.of("interface.notice_position", "general.noticePosition",
						new OptionText("Notice position", "Where notices appear on the screen.",
								"Notices are short messages, e.g. that your settings file was backed up. Vanilla toasts show top right too and are drawn "
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
				new Card("updates", Category.GENERAL, "Updates", updateCheck, updateRows));
	}
}
