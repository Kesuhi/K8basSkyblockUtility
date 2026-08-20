package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.Module;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Rule fields used to live inside a NestedListListEntry cell (a list-of-lists, two levels of
 * nesting) — keyboard text input didn't reliably reach fields at that depth, while click-based
 * controls (toggles, the enum selector) worked fine. Each rule is now its own SubCategory
 * (one level of nesting, same depth as General's already-working fields) instead. Cloth Config
 * has no plain button widget (checked directly), so add/delete are "toggle then press Save"
 * actions reconciled in onConfigScreenSaved(), not a dedicated +/- control.
 */
public final class MobHighlighterModule implements Module {
	public static final String ID = "mob_highlighter";

	private MobHighlighterConfig config;
	private final List<HighlightRule> pendingDeletions = new ArrayList<>();
	private boolean pendingAdd = false;

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
		pendingDeletions.clear();
		pendingAdd = false;

		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Enabled"), config.enabled)
				.setSaveConsumer(this::setEnabled)
				.build());

		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Add a new rule (toggle, then Save)"), false)
				.setSaveConsumer(shouldAdd -> pendingAdd = shouldAdd)
				.build());

		for (HighlightRule rule : new ArrayList<>(config.rules)) {
			category.addEntry(buildRuleSubCategory(rule, entryBuilder));
		}
	}

	@Override
	public void onConfigScreenSaved() {
		config.rules.removeAll(pendingDeletions);
		pendingDeletions.clear();
		if (pendingAdd) {
			config.rules.add(new HighlightRule());
			pendingAdd = false;
		}
		ConfigManager.putModuleSection(ID, config);
		HighlightManager.rebuild(config.rules);
	}

	private AbstractConfigListEntry<?> buildRuleSubCategory(HighlightRule rule, ConfigEntryBuilder entryBuilder) {
		SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.literal(rule.label)).setExpanded(false);

		sub.add(entryBuilder.startStrField(Component.literal("Label"), rule.label)
				.setSaveConsumer(value -> rule.label = value)
				.build());
		sub.add(entryBuilder.startBooleanToggle(Component.literal("Enabled"), rule.enabled)
				.setSaveConsumer(value -> rule.enabled = value)
				.build());
		sub.add(entryBuilder.startStrField(Component.literal("Entity Type (blank = any)"), rule.entityTypeId == null ? "" : rule.entityTypeId)
				.setSaveConsumer(value -> rule.entityTypeId = value.isBlank() ? null : value)
				.build());
		sub.add(entryBuilder.startEnumSelector(Component.literal("Name Match Mode"), NameMatchMode.class, rule.nameMatchMode)
				.setSaveConsumer(value -> rule.nameMatchMode = value)
				.build());
		sub.add(entryBuilder.startStrField(Component.literal("Name Pattern"), rule.namePattern)
				.setSaveConsumer(value -> rule.namePattern = value)
				.build());
		// Hex/preview/R/G/B all ultimately write the same rule.color int. Each captures its own
		// initial value at build time and only applies on save if it actually changed — otherwise,
		// whichever field the user *didn't* touch would unconditionally re-apply its stale
		// snapshot and clobber an edit made through one of the others (e.g. editing R/G/B would
		// get silently undone by hex's untouched original value re-saving over it, or vice versa).
		int initialColor = rule.color;
		String initialHex = String.format("%06X", initialColor & 0xFFFFFF);
		int initialRed = (initialColor >> 16) & 0xFF;
		int initialGreen = (initialColor >> 8) & 0xFF;
		int initialBlue = initialColor & 0xFF;

		sub.add(entryBuilder.startStrField(Component.literal("Color (hex RRGGBB)"), initialHex)
				.setErrorSupplier(value -> isValidHexColor(value) ? Optional.empty() : Optional.of(Component.literal("Expected 6 hex digits, e.g. FF4500")))
				.setSaveConsumer(value -> {
					if (isValidHexColor(value) && !value.equalsIgnoreCase(initialHex)) {
						rule.color = Integer.parseInt(value, 16);
					}
				})
				.build());
		sub.add(entryBuilder.startColorField(Component.literal("Color Preview"), initialColor)
				.setSaveConsumer(value -> {
					if (value != initialColor) {
						rule.color = value;
					}
				})
				.build());
		sub.add(entryBuilder.startIntField(Component.literal("Color Red"), initialRed)
				.setMin(0).setMax(255)
				.setSaveConsumer(value -> {
					if (value != initialRed) {
						rule.color = (rule.color & 0x00FFFF) | (value << 16);
					}
				})
				.build());
		sub.add(entryBuilder.startIntField(Component.literal("Color Green"), initialGreen)
				.setMin(0).setMax(255)
				.setSaveConsumer(value -> {
					if (value != initialGreen) {
						rule.color = (rule.color & 0xFF00FF) | (value << 8);
					}
				})
				.build());
		sub.add(entryBuilder.startIntField(Component.literal("Color Blue"), initialBlue)
				.setMin(0).setMax(255)
				.setSaveConsumer(value -> {
					if (value != initialBlue) {
						rule.color = (rule.color & 0xFFFF00) | value;
					}
				})
				.build());
		sub.add(entryBuilder.startDoubleField(Component.literal("Max Distance (0 = unlimited)"), rule.maxDistance)
				.setMin(0)
				.setSaveConsumer(value -> rule.maxDistance = value)
				.build());
		sub.add(entryBuilder.startBooleanToggle(Component.literal("Delete this rule (toggle, then Save)"), false)
				.setSaveConsumer(shouldDelete -> {
					if (shouldDelete) {
						pendingDeletions.add(rule);
					} else {
						pendingDeletions.remove(rule);
					}
				})
				.build());

		return sub.build();
	}

	private static boolean isValidHexColor(String value) {
		return value != null && value.matches("[0-9A-Fa-f]{6}");
	}
}
