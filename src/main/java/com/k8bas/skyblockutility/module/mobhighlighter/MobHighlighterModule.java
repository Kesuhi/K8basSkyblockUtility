package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.highlight.HighlightManager;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.settings.ButtonEntry;
import com.k8bas.skyblockutility.settings.DirtyMarkerEntry;
import com.k8bas.skyblockutility.settings.HexColorFieldEntry;
import com.k8bas.skyblockutility.settings.LiveTextFieldEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.EmptyEntry;
import me.shedaniel.clothconfig2.gui.widget.SearchFieldEntry;
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
import java.util.Set;

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

	/** Islands the mob database groups mobs under that aren't a single physical location — Jerry's
	 *  Workshop can start on whatever island you're already on, and fishing happens all over the
	 *  place, so a rule sourced from one of these is left ungated (active everywhere) rather than
	 *  restricted to an island it isn't really tied to. */
	private static final Set<String> UNGATED_ISLANDS = Set.of("Jerry", "Fishing", "Spooky Festival", "Mythological Creatures");

	private final HighlightManager highlightManager = new HighlightManager();
	private MobHighlighterConfig config;
	/** A working copy of config.rules for the lifetime of one open settings screen. Add/Delete
	 *  mutate this, not config.rules directly, so nothing actually takes effect (persisted or
	 *  live) unless the screen is actually saved — Cancel/Escape just discards it, same as
	 *  Cloth Config's own field-level edits already behave. Re-seeded fresh from config.rules
	 *  every time buildConfigScreen runs (i.e. every time the settings screen opens). */
	private List<HighlightRule> workingRules;

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
		highlightManager.setEnabled(config.enabled);
		highlightManager.rebuild(config.rules);
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
		highlightManager.setEnabled(enabled);
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

		workingRules = new ArrayList<>(config.rules);
		category.addEntry(new DirtyMarkerEntry(() -> !workingRules.equals(config.rules)));
		for (HighlightRule rule : workingRules) {
			category.addEntry(buildRuleSubCategory(rule, entryBuilder));
		}
	}

	@Override
	public void onConfigScreenSaved() {
		config.rules = new ArrayList<>(workingRules);
		ConfigManager.putModuleSection(ID, config);
		highlightManager.rebuild(config.rules);
	}

	/** A separate screen, opened via the button above, instead of inline in the main category —
	 *  with 300+ database entries, showing them all the time would swamp the module's own
	 *  settings. Cloth Config's own built-in search box (confirmed against its real source) can't
	 *  do what's needed here — it only filters which already-visible top-level entries render, it
	 *  never reveals a match buried inside a collapsed folder — so it's stripped out via
	 *  stripBuiltInSearchBox() and replaced with a custom live search field. Every keystroke
	 *  throws away and rebuilds the folder tree from scratch, containing only islands/events/mobs
	 *  that actually match (auto-expanded), which is real hide-on-no-match rather than the
	 *  earlier expand/collapse-only approach. */
	private Screen buildMobPickerScreen(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("Mob Database"))
				.setSavingRunnable(() -> {
				})
				.setAfterInitConsumer(MobHighlighterModule::stripBuiltInSearchBox);

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

		List<AbstractConfigListEntry<?>> currentFolderEntries = new ArrayList<>();
		category.addEntry(new LiveTextFieldEntry(Component.literal("Search"), "Search mobs...", query -> {
			String normalized = query.trim().toLowerCase(Locale.ROOT);
			Screen active = Minecraft.getInstance().screen;
			if (active instanceof ClothConfigScreen clothScreen) {
				replaceFolderEntries(clothScreen, currentFolderEntries,
						buildFolderList(byIsland, entryBuilder, parent, normalized));
			}
		}));

		currentFolderEntries.addAll(buildFolderList(byIsland, entryBuilder, parent, ""));
		for (AbstractConfigListEntry<?> entry : currentFolderEntries) {
			category.addEntry(entry);
		}

		return builder.build();
	}

	/** Cloth Config unconditionally injects its own search box (plus a blank spacer row on each
	 *  side) into every screen it builds — there's no builder flag to opt out (confirmed against
	 *  the real source: ClothConfigScreen.init() adds it directly, not gated by any setting).
	 *  With our own search field doing the actual filtering, Cloth's box would just sit there as
	 *  a second, non-functional search-looking field, so this removes it from the live screen
	 *  right after each init() (afterInitConsumer fires on every init, including on resize). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void stripBuiltInSearchBox(Screen screenObj) {
		if (!(screenObj instanceof ClothConfigScreen clothScreen)) {
			return;
		}
		List children = clothScreen.listWidget.children();
		for (Object entry : new ArrayList<>(children)) {
			if (entry instanceof SearchFieldEntry || entry instanceof EmptyEntry) {
				children.remove(entry);
			}
		}
	}

	/** Builds the full island/event/mob folder tree. With a blank query this is the unfiltered,
	 *  all-collapsed browse view; with a query, islands/events with no matching mob are omitted
	 *  entirely and everything remaining is force-expanded, since it's guaranteed to contain only
	 *  matches. */
	private List<AbstractConfigListEntry<?>> buildFolderList(Map<String, List<MobDatabaseEntry>> byIsland,
			ConfigEntryBuilder entryBuilder, Screen parentScreen, String normalizedQuery) {
		List<AbstractConfigListEntry<?>> result = new ArrayList<>();
		for (Map.Entry<String, List<MobDatabaseEntry>> island : byIsland.entrySet()) {
			AbstractConfigListEntry<?> entry = buildIslandSubCategory(
					island.getKey(), island.getValue(), entryBuilder, parentScreen, normalizedQuery);
			if (entry != null) {
				result.add(entry);
			}
		}
		return result;
	}

	/** Replaces the currently-shown folder entries with a freshly filtered set, patching the
	 *  live, already-open screen directly (same technique as liveAddRuleEntry/liveRemoveRuleEntry)
	 *  rather than rebuilding the whole screen, so the search field itself never loses focus. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void replaceFolderEntries(ClothConfigScreen clothScreen, List<AbstractConfigListEntry<?>> current,
			List<AbstractConfigListEntry<?>> replacement) {
		List categoryEntries = clothScreen.getCategorizedEntries().values().iterator().next();
		List liveChildren = clothScreen.listWidget.children();
		for (AbstractConfigListEntry<?> old : current) {
			categoryEntries.remove(old);
			liveChildren.remove(old);
		}
		current.clear();
		for (AbstractConfigListEntry<?> fresh : replacement) {
			fresh.setScreen(clothScreen);
			categoryEntries.add(fresh);
			liveChildren.add(fresh);
			current.add(fresh);
		}
	}

	/** Returns null (island omitted entirely) when filtering and nothing under it matches. */
	private AbstractConfigListEntry<?> buildIslandSubCategory(String island, List<MobDatabaseEntry> mobs,
			ConfigEntryBuilder entryBuilder, Screen parentScreen, String normalizedQuery) {
		boolean filtering = !normalizedQuery.isEmpty();

		Map<String, List<MobDatabaseEntry>> bySubfolder = new LinkedHashMap<>();
		List<MobDatabaseEntry> direct = new ArrayList<>();
		for (MobDatabaseEntry mob : mobs) {
			if (filtering && !mob.displayName.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
				continue;
			}
			if (mob.subfolder != null) {
				bySubfolder.computeIfAbsent(mob.subfolder, key -> new ArrayList<>()).add(mob);
			} else {
				direct.add(mob);
			}
		}
		if (filtering && direct.isEmpty() && bySubfolder.isEmpty()) {
			return null;
		}

		SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.literal(island)).setExpanded(filtering);
		for (MobDatabaseEntry mob : direct) {
			sub.add(buildMobButton(mob, parentScreen));
		}
		for (Map.Entry<String, List<MobDatabaseEntry>> event : bySubfolder.entrySet()) {
			SubCategoryBuilder eventSub = entryBuilder.startSubCategory(Component.literal(event.getKey())).setExpanded(filtering);
			for (MobDatabaseEntry mob : event.getValue()) {
				eventSub.add(buildMobButton(mob, parentScreen));
			}
			sub.add(eventSub.build());
		}

		return sub.build();
	}

	private ButtonEntry buildMobButton(MobDatabaseEntry mob, Screen parentScreen) {
		return new ButtonEntry(Component.literal(mob.displayName), Component.literal("Add rule"), () -> {
			HighlightRule newRule = createRuleForMob(mob);
			workingRules.add(newRule);
			liveAddRuleEntry(parentScreen, newRule);
		});
	}

	private HighlightRule createRuleForMob(MobDatabaseEntry mob) {
		HighlightRule rule = new HighlightRule();
		rule.label = mob.displayName;
		rule.namePattern = mob.matchText;
		rule.nameMatchMode = NameMatchMode.CONTAINS;
		rule.color = 0xFF0000;
		rule.island = UNGATED_ISLANDS.contains(mob.island) ? null : mob.island;
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
		sub.add(entryBuilder.startStrField(Component.literal("Restrict to Island (blank = any)"), rule.island == null ? "" : rule.island)
				.setSaveConsumer(value -> rule.island = value.isBlank() ? null : value)
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
			workingRules.remove(rule);
			liveRemoveRuleEntry(Minecraft.getInstance().screen, selfRef[0]);
		}));

		AbstractConfigListEntry<?> built = sub.build();
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
}
