package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.Module;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

public final class MobHighlighterModule implements Module {
	public static final String ID = "mob_highlighter";

	private MobHighlighterConfig config;

	/** Seeded only the first time this module's config section is created (no existing file entry). */
	private static MobHighlighterConfig createDefaultConfig() {
		MobHighlighterConfig defaultConfig = new MobHighlighterConfig();

		HighlightRule voidling = new HighlightRule();
		voidling.label = "Voidling Extremist";
		voidling.namePattern = "Voidling Extremist";
		voidling.color = 0xFF4500;
		defaultConfig.rules.add(voidling);

		HighlightRule sven = new HighlightRule();
		sven.label = "Sven Packmaster";
		sven.namePattern = "Sven Packmaster";
		sven.color = 0x00BFFF;
		defaultConfig.rules.add(sven);

		HighlightRule zombies = new HighlightRule();
		zombies.label = "Nearby Zombies";
		zombies.entityTypeId = "minecraft:zombie";
		zombies.nameMatchMode = NameMatchMode.NONE;
		zombies.namePattern = "";
		zombies.color = 0x00FF00;
		zombies.maxDistance = 32;
		defaultConfig.rules.add(zombies);

		return defaultConfig;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Mob Highlighter";
	}

	@Override
	public void onRegister() {
		config = ConfigManager.getModuleSection(ID, MobHighlighterConfig.class, MobHighlighterModule::createDefaultConfig);
		HighlightManager.setEnabled(config.enabled);
		HighlightManager.rebuild(config.rules);
		ModKeybinds.register(this);
	}

	@Override
	public boolean isEnabled() {
		return config.enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		config.enabled = enabled;
		HighlightManager.setEnabled(enabled);
		ConfigManager.putModuleSection(ID, config);
		ConfigManager.save();
	}

	@Override
	public void buildConfigScreen(ConfigCategory category, ConfigEntryBuilder entryBuilder) {
		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Enabled"), config.enabled)
				.setSaveConsumer(this::setEnabled)
				.build());
	}
}
