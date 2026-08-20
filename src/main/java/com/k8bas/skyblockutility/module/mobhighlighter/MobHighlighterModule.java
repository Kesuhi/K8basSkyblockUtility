package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.settings.ButtonEntry;
import com.k8bas.skyblockutility.settings.HexColorFieldEntry;
import com.k8bas.skyblockutility.settings.LiveTextFieldEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Rule fields used to live inside a NestedListListEntry cell (a list-of-lists, two levels of
 * nesting) — keyboard text input didn't reliably reach fields at that depth, while click-based
 * controls (toggles, the enum selector) worked fine. Each rule is now its own SubCategory
 * (one level of nesting, same depth as General's already-working fields) instead.
 *
 * Add/Delete/Open-Database/Return are real clickable buttons (ButtonEntry — Cloth Config has no
 * built-in button widget, so this hosts a real vanilla Button modeled on Cloth Config's own
 * BooleanListEntry source) that act immediately, not gated behind the screen's Save button. They
 * also patch the live, already-open screen's entry list directly (see liveAddRuleEntry /
 * liveRemoveRuleEntry) so the rule list reflects add/delete instantly, without needing to close
 * and reopen the settings screen. Editing an existing rule's other fields (name, entity type,
 * etc.) still follows the normal Cloth Config save cycle, reconciled in onConfigScreenSaved().
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
	 *  with 300+ database entries, showing them all the time would swamp the module's own
	 *  settings. Island/event folders default to collapsed; the search field above them expands
	 *  only the folders containing a match as you type (Cloth Config's own built-in search box,
	 *  confirmed against its real source, filters which top-level entries are shown but never
	 *  auto-expands a collapsed SubCategory to reveal a match inside it — so a second, custom
	 *  live-updating field drives the actual expand behavior). */
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
			return builder.build();
		}

		List<SearchableFolder> folders = new ArrayList<>();
		category.addEntry(new LiveTextFieldEntry(Component.literal("Search"), "Type to expand matching folders...", query -> {
			String normalized = query.trim().toLowerCase(Locale.ROOT);
			for (SearchableFolder folder : folders) {
				folder.entry().setExpanded(!normalized.isEmpty() && folder.matches(normalized));
			}
		}));

		for (Map.Entry<String, List<MobDatabaseEntry>> island : byIsland.entrySet()) {
			category.addEntry(buildIslandSubCategory(island.getKey(), island.getValue(), entryBuilder, parent, folders));
		}

		return builder.build();
	}

	private AbstractConfigListEntry<?> buildIslandSubCategory(String island, List<MobDatabaseEntry> mobs,
			ConfigEntryBuilder entryBuilder, Screen parentScreen, List<SearchableFolder> folders) {
		SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.literal(island)).setExpanded(false);

		Map<String, List<MobDatabaseEntry>> bySubfolder = new LinkedHashMap<>();
		List<MobDatabaseEntry> direct = new ArrayList<>();
		List<String> allNames = new ArrayList<>();
		for (MobDatabaseEntry mob : mobs) {
			allNames.add(mob.displayName);
			if (mob.subfolder != null) {
				bySubfolder.computeIfAbsent(mob.subfolder, key -> new ArrayList<>()).add(mob);
			} else {
				direct.add(mob);
			}
		}

		for (MobDatabaseEntry mob : direct) {
			sub.add(buildMobButton(mob, parentScreen));
		}
		for (Map.Entry<String, List<MobDatabaseEntry>> event : bySubfolder.entrySet()) {
			SubCategoryBuilder eventSub = entryBuilder.startSubCategory(Component.literal(event.getKey())).setExpanded(false);
			List<String> eventNames = new ArrayList<>();
			for (MobDatabaseEntry mob : event.getValue()) {
				eventNames.add(mob.displayName);
				eventSub.add(buildMobButton(mob, parentScreen));
			}
			SubCategoryListEntry eventEntry = eventSub.build();
			folders.add(new SearchableFolder(eventEntry, eventNames));
			sub.add(eventEntry);
		}

		SubCategoryListEntry islandEntry = sub.build();
		folders.add(new SearchableFolder(islandEntry, allNames));
		return islandEntry;
	}

	private ButtonEntry buildMobButton(MobDatabaseEntry mob, Screen parentScreen) {
		return new ButtonEntry(Component.literal(mob.displayName), Component.literal("Add rule"), () -> {
			HighlightRule newRule = createRuleForMob(mob);
			config.rules.add(newRule);
			ConfigManager.putModuleSection(ID, config);
			ConfigManager.save();
			HighlightManager.rebuild(config.rules);
			liveAddRuleEntry(parentScreen, newRule);

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
		// Filled in right after sub.build() below so the Delete button's own closure can remove
		// this exact entry instance from the live screen — it can't reference the built entry
		// before it exists, so it reads through this one-slot holder at click time instead.
		AbstractConfigListEntry<?>[] selfRef = new AbstractConfigListEntry<?>[1];

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
		// Hex/R/G/B all ultimately write the same rule.color int. Each captures its own initial
		// value at build time and only applies on save if it actually changed — otherwise,
		// whichever field the user *didn't* touch would unconditionally re-apply its stale
		// snapshot and clobber an edit made through one of the others.
		int initialColor = rule.color;
		String initialHex = String.format("%06X", initialColor & 0xFFFFFF);
		int initialRed = (initialColor >> 16) & 0xFF;
		int initialGreen = (initialColor >> 8) & 0xFF;
		int initialBlue = initialColor & 0xFF;

		sub.add(new HexColorFieldEntry(Component.literal("Color (hex RRGGBB)"), initialHex, initialColor, value -> {
			if (isValidHexColor(value) && !value.equalsIgnoreCase(initialHex)) {
				rule.color = Integer.parseInt(value, 16);
			}
		}));
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
			liveRemoveRuleEntry(Minecraft.getInstance().screen, selfRef[0]);
		}));

		SubCategoryListEntry built = sub.build();
		selfRef[0] = built;
		return built;
	}

	/** Patches an already-built, possibly not-currently-displayed screen's live entry list so a
	 *  rule added from the Mob Database picker shows up immediately when the user returns to it,
	 *  instead of only after closing and reopening the settings screen. Cloth Config copies each
	 *  category's entries into the screen at construction time (ClothConfigScreen's constructor,
	 *  confirmed via its real source) rather than reading live from the ConfigCategory, so the
	 *  screen's own already-built lists have to be mutated directly. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void liveAddRuleEntry(Screen screenObj, HighlightRule rule) {
		if (!(screenObj instanceof ClothConfigScreen clothScreen)) {
			return;
		}
		AbstractConfigListEntry<?> entry = buildRuleSubCategory(rule, ConfigEntryBuilder.create());
		entry.setScreen(clothScreen);
		List<AbstractConfigEntry<?>> categoryEntries = findLiveCategoryEntries(clothScreen);
		if (categoryEntries != null) {
			categoryEntries.add(entry);
		}
		((List) clothScreen.listWidget.children()).add(entry);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void liveRemoveRuleEntry(Screen screenObj, AbstractConfigListEntry<?> entry) {
		if (entry == null || !(screenObj instanceof ClothConfigScreen clothScreen)) {
			return;
		}
		List<AbstractConfigEntry<?>> categoryEntries = findLiveCategoryEntries(clothScreen);
		if (categoryEntries != null) {
			categoryEntries.remove(entry);
		}
		((List) clothScreen.listWidget.children()).remove(entry);
	}

	/** Component doesn't reliably participate as a Map key across this codebase (Cloth Config
	 *  itself routes around Component-keyed lookups elsewhere in favor of comparing the plain
	 *  string), so this matches by category title string instead of trusting Component#equals. */
	private List<AbstractConfigEntry<?>> findLiveCategoryEntries(ClothConfigScreen clothScreen) {
		for (Map.Entry<Component, List<AbstractConfigEntry<?>>> entry : clothScreen.getCategorizedEntries().entrySet()) {
			if (entry.getKey().getString().equals(displayName())) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static boolean isValidHexColor(String value) {
		return value != null && value.matches("[0-9A-Fa-f]{6}");
	}

	private record SearchableFolder(SubCategoryListEntry entry, List<String> mobNames) {
		boolean matches(String normalizedQuery) {
			for (String name : mobNames) {
				if (name.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
					return true;
				}
			}
			return false;
		}
	}
}
