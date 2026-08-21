package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.highlight.HighlightManager;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.settings.ButtonEntry;
import com.k8bas.skyblockutility.settings.DirtyMarkerEntry;
import com.k8bas.skyblockutility.settings.ColorWheelFieldEntry;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rule fields used to live inside a NestedListListEntry cell (a list-of-lists, two levels of
 * nesting) — keyboard text input didn't reliably reach fields at that depth, while click-based
 * controls (toggles, the enum selector) worked fine. Each rule is now its own SubCategory
 * (one level of nesting, same depth as General's already-working fields) instead.
 *
 * Add/Delete/Open-Database are real clickable buttons (ButtonEntry — Cloth Config has no
 * built-in button widget, so this hosts a real vanilla Button modeled on Cloth Config's own
 * BooleanListEntry source) that patch the live, already-open screen's entry list directly (see
 * liveAddRuleEntry/liveRemoveRuleEntry) so the rule list reflects add/delete instantly. The
 * picker screen has no separate Return button — Cloth Config's own Cancel/Save & Done footer
 * buttons already navigate back to the parent screen (confirmed: Cloth Config overwrites that
 * button's own label every tick based on its own isEdited() state, so it can't be relabeled
 * through the public API either — a custom "Return" button next to it would just be a duplicate
 * of what's already there). Editing an existing rule's other fields (name, entity type,
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
		// Fetched at startup rather than lazily on first picker-open, so opening the picker for
		// the first time doesn't show the "still loading" message / a moment of an empty list.
		MobDatabase.fetchIfNeeded();
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
		ConfigManager.saveAsync();
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

		// Cloth Config disables "Save & Done" unless it thinks something's edited, which nothing
		// on this screen ever reports (picking a mob doesn't touch any Cloth-tracked field) — an
		// always-true marker keeps it permanently clickable so it works as the Return button
		// (its saving runnable is a no-op, so clicking it just navigates back to parent).
		category.addEntry(new DirtyMarkerEntry(() -> true));

		Map<String, List<MobDatabaseEntry>> byIsland = MobDatabase.byIsland();
		if (byIsland.isEmpty()) {
			category.addEntry(entryBuilder.startTextDescription(Component.literal(
					"Mob database hasn't finished loading yet — close and reopen this screen in a moment.")).build());
			return builder.build();
		}

		List<AbstractConfigListEntry<?>> currentFolderEntries = new ArrayList<>();
		String[] lastQuery = {""};
		// Shared by the search field and every "Add rule" button: re-derives the folder tree from
		// scratch against the *current* workingRules and the *last typed* query, and live-patches
		// the picker screen with it. Adding a mob needs this same rebuild (to make it disappear
		// from the list immediately) as much as typing a new search query does. Declared as a
		// one-slot holder first, since the Runnable's own body needs to refer to itself (each
		// rebuilt "Add rule" button needs a way to trigger the *next* rebuild too).
		Runnable[] refreshPickerRef = new Runnable[1];
		refreshPickerRef[0] = () -> {
			Screen active = Minecraft.getInstance().screen;
			if (active instanceof ClothConfigScreen clothScreen) {
				replaceFolderEntries(clothScreen, currentFolderEntries,
						buildFolderList(byIsland, entryBuilder, parent, lastQuery[0], refreshPickerRef[0]));
			}
		};

		category.addEntry(new LiveTextFieldEntry(Component.literal("Search"), "Search mobs...", query -> {
			lastQuery[0] = query.trim().toLowerCase(Locale.ROOT);
			refreshPickerRef[0].run();
		}));

		currentFolderEntries.addAll(buildFolderList(byIsland, entryBuilder, parent, "", refreshPickerRef[0]));
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

	/** Builds the full island/event/mob folder tree, skipping any mob whose database id is
	 *  already backing a tracked rule (added-but-not-yet-saved rules count too, via
	 *  workingRules — no point letting the same mob be "added" twice). With a blank query this
	 *  is the unfiltered, all-collapsed browse view; with a query, islands/events with no
	 *  matching mob are omitted entirely and everything remaining is force-expanded, since it's
	 *  guaranteed to contain only matches. */
	private List<AbstractConfigListEntry<?>> buildFolderList(Map<String, List<MobDatabaseEntry>> byIsland,
			ConfigEntryBuilder entryBuilder, Screen parentScreen, String normalizedQuery, Runnable refreshPicker) {
		Set<String> addedSourceIds = new HashSet<>();
		for (HighlightRule rule : workingRules) {
			if (rule.sourceId != null) {
				addedSourceIds.add(rule.sourceId);
			}
		}

		List<AbstractConfigListEntry<?>> result = new ArrayList<>();
		for (Map.Entry<String, List<MobDatabaseEntry>> island : byIsland.entrySet()) {
			AbstractConfigListEntry<?> entry = buildIslandSubCategory(
					island.getKey(), island.getValue(), entryBuilder, parentScreen, normalizedQuery, addedSourceIds, refreshPicker);
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

	/** Returns null (island omitted entirely) when filtering/already-added leaves nothing under it. */
	private AbstractConfigListEntry<?> buildIslandSubCategory(String island, List<MobDatabaseEntry> mobs,
			ConfigEntryBuilder entryBuilder, Screen parentScreen, String normalizedQuery, Set<String> addedSourceIds,
			Runnable refreshPicker) {
		boolean filtering = !normalizedQuery.isEmpty();

		Map<String, List<MobDatabaseEntry>> bySubfolder = new LinkedHashMap<>();
		List<MobDatabaseEntry> direct = new ArrayList<>();
		for (MobDatabaseEntry mob : mobs) {
			if (addedSourceIds.contains(mob.id)) {
				continue;
			}
			if (filtering && !mob.displayName.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
				continue;
			}
			if (mob.subfolder != null) {
				bySubfolder.computeIfAbsent(mob.subfolder, key -> new ArrayList<>()).add(mob);
			} else {
				direct.add(mob);
			}
		}
		if (direct.isEmpty() && bySubfolder.isEmpty()) {
			return null;
		}

		SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.literal(island)).setExpanded(filtering);
		for (MobDatabaseEntry mob : direct) {
			sub.add(buildMobButton(mob, parentScreen, refreshPicker));
		}
		for (Map.Entry<String, List<MobDatabaseEntry>> event : bySubfolder.entrySet()) {
			SubCategoryBuilder eventSub = entryBuilder.startSubCategory(Component.literal(event.getKey())).setExpanded(filtering);
			for (MobDatabaseEntry mob : event.getValue()) {
				eventSub.add(buildMobButton(mob, parentScreen, refreshPicker));
			}
			sub.add(eventSub.build());
		}

		return sub.build();
	}

	private ButtonEntry buildMobButton(MobDatabaseEntry mob, Screen parentScreen, Runnable refreshPicker) {
		return new ButtonEntry(Component.literal(mob.displayName), Component.literal("Add rule"), () -> {
			HighlightRule newRule = createRuleForMob(mob);
			workingRules.add(newRule);
			liveAddRuleEntry(parentScreen, newRule);
			refreshPicker.run();
		});
	}

	private HighlightRule createRuleForMob(MobDatabaseEntry mob) {
		HighlightRule rule = new HighlightRule();
		rule.label = mob.displayName;
		rule.namePattern = mob.matchText;
		rule.nameMatchMode = NameMatchMode.CONTAINS;
		rule.color = 0xFF0000;
		rule.sourceId = mob.id;
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
		String initialHex = String.format("%06X", rule.color & 0xFFFFFF);
		sub.add(new ColorWheelFieldEntry(Component.literal("Color"), rule.color, value -> {
			if (isValidHexColor(value) && !value.equalsIgnoreCase(initialHex)) {
				rule.color = Integer.parseInt(value, 16);
			}
		}));
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
