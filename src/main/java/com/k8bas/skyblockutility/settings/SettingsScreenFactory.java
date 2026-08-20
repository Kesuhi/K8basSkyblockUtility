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
				.setSavingRunnable(ConfigManager::save);

		ConfigEntryBuilder entryBuilder = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
		general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Check for updates on startup"),
						ConfigManager.general().autoUpdateCheckEnabled)
				.setSaveConsumer(value -> ConfigManager.general().autoUpdateCheckEnabled = value)
				.build());
		general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Automatically download updates"),
						ConfigManager.general().autoUpdateDownloadEnabled)
				.setSaveConsumer(value -> ConfigManager.general().autoUpdateDownloadEnabled = value)
				.build());

		for (Module module : ModuleManager.modules()) {
			ConfigCategory category = builder.getOrCreateCategory(Component.literal(module.displayName()));
			module.buildConfigScreen(category, entryBuilder);
		}

		return builder.build();
	}
}
