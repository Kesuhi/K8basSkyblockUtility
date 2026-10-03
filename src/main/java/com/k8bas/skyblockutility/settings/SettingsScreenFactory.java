package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreenFactory {
	private SettingsScreenFactory() {
	}

	public static Screen build(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("K8bas Skyblock Utility"))
				.setSavingRunnable(() -> {
					// Let modules reconcile pending add/delete actions and rebuild derived state
					// (e.g. Mob Highlighter's rule index) before the single write to disk.
					for (Module module : ModuleManager.modules()) {
						module.onConfigScreenSaved();
					}
					ConfigManager.save();
				});

		ConfigEntryBuilder entryBuilder = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
		// Declared once in GeneralOptions (T2.1); the mob scan range now sits with Mob Highlighter (REQ-UI-04).
		ClothOptions.addCards(general, entryBuilder, GeneralOptions.cards(ConfigManager.general()));

		for (Module module : ModuleManager.modules()) {
			ConfigCategory category = builder.getOrCreateCategory(Component.literal(module.displayName()));
			module.buildConfigScreen(category, entryBuilder);
		}

		return builder.build();
	}
}
