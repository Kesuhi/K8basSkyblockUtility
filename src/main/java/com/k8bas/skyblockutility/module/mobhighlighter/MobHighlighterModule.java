package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.settings.ButtonEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rule fields used to live inside a NestedListListEntry cell (a list-of-lists, two levels of
 * nesting) — keyboard text input didn't reliably reach fields at that depth, while click-based
 * controls (toggles, the enum selector) worked fine. Each rule is now its own SubCategory
 * (one level of nesting, same depth as General's already-working fields) instead.
 *
 * Add/Delete/Open-Database are real clickable buttons (ButtonEntry — Cloth Config has no
 * built-in button widget, so this hosts a real vanilla Button modeled on Cloth Config's own
 * BooleanListEntry source) that act immediately, not gated behind the screen's Save button.
 * Editing an existing rule's fields (name, color, etc.) still follows the normal Cloth Config
 * save cycle, reconciled in onConfigScreenSaved().
 */
public final class MobHighlighterModule implements Module {
	public static final String ID = "mob_highlighter";

	private MobHighlighterConfig config;

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
		config = ConfigManager.getModuleSection(ID, MobHighlighterConfig.class, MobHighlighterConfig::new);
		HighlightManager.setEnabled(config.enabled);
		HighlightManager.rebuild(config.rules);
		MobDatabase.fetchInBackground();
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

		category.addEntry(new ButtonEntry(Component.literal("Mob Database"), Component.literal("Open"), () -> {
			Minecraft client = Minecraft.getInstance();
			client.setScreen(buildMobPickerScreen(client.screen));
		}));

		for (HighlightRule rule : new ArrayList<>(config.rules)) {
			category.addEntry(buildRuleSubCategory(rule, entryBuilder));
		}
	}

	@Override
	public void onConfigScreenSaved() {
		ConfigManager.putModuleSection(ID, config);
		HighlightManager.rebuild(config.rules);
	}

	/** A separate screen, opened via the button above, instead of inline in the main category —
	 *  with 200+ database entries, showing them all the time would swamp the module's own
	 *  settings. Island/event folders default to expanded: Cloth Config's built-in screen search
	 *  (verified against the jar) only filters entries that are already displayed — a collapsed
	 *  SubCategory's contents are excluded from search entirely, with no auto-expand-on-match
	 *  hook available (confirmed against the real source), so leaving them open is what actually
	 *  makes "search finds a mob in this folder" work. */
	private Screen buildMobPickerScreen(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("Mob Database"))
				.setSavingRunnable(() -> {
				});

		ConfigEntryBuilder entryBuilder = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.literal("Mob Database"));

		category.addEntry(new ButtonEntry(Component.literal("Back"), Component.literal("Return"),
				() -> Minecraft.getInstance().setScreen(parent)));

		Map<String, List<MobDatabaseEntry>> byIsland = MobDatabase.byIsland();
		if (byIsland.isEmpty()) {
			category.addEntry(entryBuilder.startTextDescription(Component.literal(
					"Mob database hasn't finished loading yet — close and reopen this screen in a moment.")).build());
		}
		for (Map.Entry<String, List<MobDatabaseEntry>> island : byIsland.entrySet()) {
			category.addEntry(buildIslandSubCategory(island.getKey(), island.getValue(), entryBuilder));
		}

		return builder.build();
	}

	private AbstractConfigListEntry<?> buildIslandSubCategory(String island, List<MobDatabaseEntry> mobs, ConfigEntryBuilder entryBuilder) {
		SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.literal(island)).setExpanded(true);

		Map<String, List<MobDatabaseEntry>> bySubfolder = new LinkedHashMap<>();
		List<MobDatabaseEntry> direct = new ArrayList<>();
		for (MobDatabaseEntry mob : mobs) {
			if (mob.subfolder != null) {
				bySubfolder.computeIfAbsent(mob.subfolder, key -> new ArrayList<>()).add(mob);
			} else {
				direct.add(mob);
			}
		}

		for (MobDatabaseEntry mob : direct) {
			sub.add(buildMobButton(mob));
		}
		for (Map.Entry<String, List<MobDatabaseEntry>> event : bySubfolder.entrySet()) {
			SubCategoryBuilder eventSub = entryBuilder.startSubCategory(Component.literal(event.getKey())).setExpanded(true);
			for (MobDatabaseEntry mob : event.getValue()) {
				eventSub.add(buildMobButton(mob));
			}
			sub.add(eventSub.build());
		}

		return sub.build();
	}

	private ButtonEntry buildMobButton(MobDatabaseEntry mob) {
		return new ButtonEntry(Component.literal(mob.displayName), Component.literal("Add rule"), () -> {
			config.rules.add(createRuleForMob(mob));
			ConfigManager.putModuleSection(ID, config);
			ConfigManager.save();
			HighlightManager.rebuild(config.rules);

			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("Added highlight rule for " + mob.displayName + "."));
			}
		});
	}

	private HighlightRule createRuleForMob(MobDatabaseEntry mob) {
		HighlightRule rule = new HighlightRule();
		rule.label = mob.displayName;
		rule.namePattern = mob.matchText;
		rule.nameMatchMode = NameMatchMode.CONTAINS;
		rule.color = 0xFF0000;
		return rule;
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
		// snapshot and clobber an edit made through one of the others.
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
		sub.add(new ButtonEntry(Component.literal("Delete"), Component.literal("Delete this rule"), () -> {
			config.rules.remove(rule);
			ConfigManager.putModuleSection(ID, config);
			ConfigManager.save();
			HighlightManager.rebuild(config.rules);
			// The already-open list still shows this row until the screen is closed and reopened
			// (Cloth Config screens are built once per open) — the deletion itself is immediate.
		}));

		return sub.build();
	}

	private static boolean isValidHexColor(String value) {
		return value != null && value.matches("[0-9A-Fa-f]{6}");
	}
}
