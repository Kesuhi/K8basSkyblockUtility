package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.Module;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.NestedListListEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

		category.addEntry(new NestedListListEntry<HighlightRule, MultiElementListEntry<HighlightRule>>(
				Component.literal("Rules"),
				config.rules,
				false,
				Optional::empty,
				this::applyRules,
				() -> createDefaultConfig().rules,
				entryBuilder.getResetButtonKey(),
				false,
				true,
				(rule, listEntry) -> buildRuleEntry(rule, entryBuilder)));
	}

	private MultiElementListEntry<HighlightRule> buildRuleEntry(HighlightRule existing, ConfigEntryBuilder entryBuilder) {
		HighlightRule rule = existing != null ? existing : new HighlightRule();

		List<AbstractConfigListEntry<?>> fields = new ArrayList<>();
		fields.add(entryBuilder.startStrField(Component.literal("Label"), rule.label)
				.setSaveConsumer(value -> rule.label = value)
				.build());
		fields.add(entryBuilder.startBooleanToggle(Component.literal("Enabled"), rule.enabled)
				.setSaveConsumer(value -> rule.enabled = value)
				.build());
		fields.add(entryBuilder.startStrField(Component.literal("Entity Type (blank = any)"), rule.entityTypeId == null ? "" : rule.entityTypeId)
				.setSaveConsumer(value -> rule.entityTypeId = value.isBlank() ? null : value)
				.build());
		fields.add(entryBuilder.startEnumSelector(Component.literal("Name Match Mode"), NameMatchMode.class, rule.nameMatchMode)
				.setSaveConsumer(value -> rule.nameMatchMode = value)
				.build());
		fields.add(entryBuilder.startStrField(Component.literal("Name Pattern"), rule.namePattern)
				.setSaveConsumer(value -> rule.namePattern = value)
				.build());
		// A picker-based startColorField() didn't reliably commit edits made inside a nested
		// list cell (edits to other field types in the same cell did save correctly) — a plain
		// hex string sidesteps whatever that widget-specific issue is, and lets an exact color
		// be typed directly.
		fields.add(entryBuilder.startStrField(Component.literal("Color (hex RRGGBB)"), String.format("%06X", rule.color & 0xFFFFFF))
				.setErrorSupplier(value -> isValidHexColor(value) ? Optional.empty() : Optional.of(Component.literal("Expected 6 hex digits, e.g. FF4500")))
				.setSaveConsumer(value -> {
					if (isValidHexColor(value)) {
						rule.color = Integer.parseInt(value, 16);
					}
				})
				.build());
		fields.add(entryBuilder.startDoubleField(Component.literal("Max Distance (0 = unlimited)"), rule.maxDistance)
				.setSaveConsumer(value -> rule.maxDistance = value)
				.build());

		return new MultiElementListEntry<>(Component.literal(rule.label), rule, fields, true);
	}

	private void applyRules(List<HighlightRule> newRules) {
		config.rules = new ArrayList<>(newRules);
		ConfigManager.putModuleSection(ID, config);
		ConfigManager.save();
		HighlightManager.rebuild(config.rules);
	}

	private static boolean isValidHexColor(String value) {
		return value != null && value.matches("[0-9A-Fa-f]{6}");
	}
}
