package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.highlight.HighlightManager;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.settings.ButtonEntry;
import com.k8bas.skyblockutility.settings.DirtyMarkerEntry;
import com.k8bas.skyblockutility.settings.ColorWheelFieldEntry;
import com.k8bas.skyblockutility.settings.LiveTextFieldEntry;
import com.k8bas.skyblockutility.settings.RuleWarning;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.EmptyEntry;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import me.shedaniel.clothconfig2.gui.widget.SearchFieldEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * NPC Search: the same rule-list-plus-database-picker concept as Mob Highlighter, applied to
 * NPCs instead of mobs, with two kinds of tracked NPC:
 *  - fixed: NPCs that stand at a known, unmoving spot get a permanent waypoint at those coordinates
 *    (NpcWaypointMarkers): a beacon beam plus a floating name and distance — no entity search needed.
 *  - unfixed: NPCs without known fixed coordinates get converted into a plain HighlightRule and
 *    fed into this module's own HighlightManager instance — exactly the nearby-nametag search
 *    Mob Highlighter uses, just sourced from NPC data with a green default color.
 * Both kinds only ever activate on their recorded island (see rebuildDerived), so a fixed NPC's
 * waypoint and an unfixed NPC's entity search alike cost nothing while the player isn't there.
 */
public final class NpcSearchModule implements Module {
	public static final String ID = "npc_search";

	private final HighlightManager highlightManager = new HighlightManager();
	private final FoundTitleGate foundTitles = new FoundTitleGate();
	/** A title allowed while a screen was open; shown once the screen closes (EC-GLOW-08). */
	private Component pendingTitle;
	private NpcSearchConfig config;
	/** See MobHighlighterModule.workingRules — same reasoning: Add/Delete mutate this, not
	 *  config.rules, so Cancel/Escape actually discards them instead of them having already
	 *  taken effect. */
	private List<NpcRule> workingRules;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "NPC Search";
	}

	@Override
	public void onRegister() {
		config = ConfigManager.getModuleSection(ID, NpcSearchConfig.class, NpcSearchConfig::new);
		highlightManager.setEnabled(config.enabled);
		highlightManager.setOnMatchListener(this::onNpcMatched);
		// Every location change is a new server or mode, so each run shows the titles again.
		IslandTracker.onChange(snapshot -> {
			foundTitles.reset();
			pendingTitle = null;
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::showPendingTitle);
		rebuildDerived();
		// Fetched at startup rather than lazily on first picker-open, so opening the picker for
		// the first time doesn't show the "still loading" message / a moment of an empty list.
		NpcDatabase.fetchIfNeeded();
		NpcWaypointMarkers.register();
		ModKeybinds.register(this);
	}

	/** Called on the client tick, from HighlightManager, whenever an unfixed NPC's rule matches a
	 *  nearby entity. For the special NPCs only (R20), the first time the player can see the NPC on
	 *  this server, a short vanilla title in the rule's own colour says so (FoundTitleGate). */
	private void onNpcMatched(HighlightRule rule, Entity entity) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && FoundTitleGate.isSpecial(rule.sourceId, rule.label)
				&& foundTitles.allow(rule.id, config.foundTitleEnabled, entity.isInvisible(),
				() -> player.hasLineOfSight(entity))) {
			pendingTitle = Component.literal("You found " + rule.label)
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rule.color)));
		}
	}

	/** Shows an allowed title once no screen is open, so it is not used up behind a menu. */
	private void showPendingTitle(Minecraft client) {
		if (pendingTitle != null && client.gui.screen() == null) {
			client.gui.hud.resetTitleTimes();
			client.gui.hud.setTitle(pendingTitle);
			pendingTitle = null;
		}
	}

	/** For gametests: replaces the rules without saving them. */
	void useRulesForTest(List<NpcRule> rules) {
		config.rules = new ArrayList<>(rules);
		rebuildDerived();
	}

	/** For gametests: the module's rules. */
	List<NpcRule> rulesForTest() {
		return List.copyOf(config.rules);
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
		rebuildDerived();
	}

	/** Splits config.rules into the unfixed subset (fed to HighlightManager as ordinary
	 *  HighlightRules) and the fixed subset (fed to NpcWaypointMarkers directly). Waypoints are
	 *  suppressed entirely (empty list) while the module is disabled, since the renderer itself
	 *  has no idea whether the owning module is on. */
	private void rebuildDerived() {
		List<HighlightRule> searchRules = new ArrayList<>();
		List<NpcRule> waypointRules = new ArrayList<>();
		for (NpcRule rule : config.rules) {
			if (rule.fixed) {
				waypointRules.add(rule);
			} else {
				searchRules.add(toHighlightRule(rule));
			}
		}
		highlightManager.rebuild(searchRules);
		NpcWaypointMarkers.update(config.enabled ? waypointRules : List.of(), waypointSettings(config));
	}

	static NpcWaypointMarkers.Settings waypointSettings(NpcSearchConfig config) {
		return new NpcWaypointMarkers.Settings(config.whiteWaypointLabels, config.showBeams, config.showDistance, config.islandBeamColors);
	}

	/** A moving NPC's outline rule; its colour is the rule's own, never a beam colour (REQ-NPCWP-07). */
	static HighlightRule toHighlightRule(NpcRule rule) {
		HighlightRule highlightRule = new HighlightRule();
		highlightRule.id = rule.id;
		highlightRule.label = rule.label;
		highlightRule.enabled = rule.enabled;
		highlightRule.nameMatchMode = rule.nameMatchMode;
		highlightRule.namePattern = rule.namePattern;
		highlightRule.color = rule.color;
		highlightRule.island = rule.island;
		highlightRule.sourceId = rule.sourceId;
		return highlightRule;
	}

	@Override
	public void buildConfigScreen(ConfigCategory category, ConfigEntryBuilder entryBuilder) {
		// AMBER feature (PLAN §3, P6): the tooltip states its restriction.
		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Enabled"), config.enabled)
				.setDefaultValue(true)
				.setTooltip(Component.literal("Waypoints mark only the fixed NPC positions from your list: their name and "
						+ "distance show through blocks, and terrain hides their beams like a beacon's. Moving NPCs are only "
						+ "outlined while you can see them, never through walls."))
				.setSaveConsumer(this::setEnabled)
				.build());
		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("\"You found\" title"), config.foundTitleEnabled)
				.setDefaultValue(true)
				.setTooltip(Component.literal("Shows \"You found <NPC>\" for Trinity, Tomioka, Duncan, Xalx and Pete the first time you have a clear line of sight to them, once per run."))
				.setSaveConsumer(value -> config.foundTitleEnabled = value)
				.build());
		// Waypoint look (REQ-NPCWP-08); onConfigScreenSaved rebuilds the waypoints after these are set.
		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show beacon beams"), config.showBeams)
				.setDefaultValue(true)
				.setTooltip(Component.literal("A beacon beam rises from each waypoint, in its island's colour (green for "
						+ "waypoints shown on every island). Off: only the label."))
				.setSaveConsumer(value -> config.showBeams = value)
				.build());
		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show distance"), config.showDistance)
				.setDefaultValue(true)
				.setTooltip(Component.literal("A second line under each waypoint label with your distance to it in metres."))
				.setSaveConsumer(value -> config.showDistance = value)
				.build());
		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("White waypoint labels"), config.whiteWaypointLabels)
				.setDefaultValue(true)
				.setTooltip(Component.literal("On: a white label with a yellow distance line. Off: both in the NPC's own colour."))
				.setSaveConsumer(value -> config.whiteWaypointLabels = value)
				.build());

		category.addEntry(new ButtonEntry(Component.literal("NPC Database"), Component.literal("Open"), () -> {
			Minecraft client = Minecraft.getInstance();
			client.gui.setScreen(buildNpcPickerScreen(client.gui.screen()));
		}));

		workingRules = new ArrayList<>(config.rules);
		category.addEntry(new DirtyMarkerEntry(() -> !workingRules.equals(config.rules)));
		for (NpcRule rule : workingRules) {
			category.addEntry(buildRuleSubCategory(rule, entryBuilder));
		}
	}

	@Override
	public void onConfigScreenSaved() {
		config.rules = new ArrayList<>(workingRules);
		ConfigManager.putModuleSection(ID, config);
		rebuildDerived();
	}

	/** Mirrors MobHighlighterModule's picker screen (same search/filter/expand technique,
	 *  same live-patch add/return handling) — see its buildMobPickerScreen for the reasoning
	 *  behind stripping Cloth Config's own search box and rebuilding the folder tree per
	 *  keystroke instead of just expanding/collapsing it. */
	private Screen buildNpcPickerScreen(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("NPC Database"))
				.setSavingRunnable(() -> {
				})
				.setAfterInitConsumer(screenObj -> {
					stripBuiltInSearchBox(screenObj);
					relabelSaveButton(screenObj);
				});

		ConfigEntryBuilder entryBuilder = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.literal("NPC Database"));

		// Cloth Config disables "Save & Done" unless it thinks something's edited, which nothing
		// on this screen ever reports (picking an NPC doesn't touch any Cloth-tracked field) — an
		// always-true marker keeps it permanently clickable so it works as the Return button
		// (its saving runnable is a no-op, so clicking it just navigates back to parent).
		category.addEntry(new DirtyMarkerEntry(() -> true));

		Map<String, List<NpcDatabaseEntry>> byIsland = NpcDatabase.byIsland();
		if (byIsland.isEmpty()) {
			category.addEntry(entryBuilder.startTextDescription(Component.literal(
					"NPC database hasn't finished loading yet — close and reopen this screen in a moment.")).build());
			return builder.build();
		}

		List<AbstractConfigListEntry<?>> currentFolderEntries = new ArrayList<>();
		String[] lastQuery = {""};
		// Remembers which island folders were manually expanded, keyed by folder name, so a
		// rebuild (triggered by typing a search or adding an NPC) doesn't snap every folder back
		// to collapsed — captured from the live entries right before each rebuild throws them away.
		Map<String, Boolean> expandedState = new HashMap<>();
		// Shared by the search field and every "Add" button: re-derives the folder tree from
		// scratch against the *current* workingRules and the *last typed* query, and live-patches
		// the picker screen with it. Adding an NPC needs this same rebuild (to make it disappear
		// from the list immediately) as much as typing a new search query does. Declared as a
		// one-slot holder first since the Runnable's own body needs to refer to itself.
		Runnable[] refreshPickerRef = new Runnable[1];
		refreshPickerRef[0] = () -> {
			Screen active = Minecraft.getInstance().gui.screen();
			if (active instanceof ClothConfigScreen clothScreen) {
				captureExpandedState(currentFolderEntries, expandedState);
				replaceFolderEntries(clothScreen, currentFolderEntries,
						buildFolderList(byIsland, entryBuilder, parent, lastQuery[0], refreshPickerRef[0], expandedState));
			}
		};

		category.addEntry(new LiveTextFieldEntry(Component.literal("Search"), "Search NPCs...", query -> {
			lastQuery[0] = query.trim().toLowerCase(Locale.ROOT);
			refreshPickerRef[0].run();
		}));

		currentFolderEntries.addAll(buildFolderList(byIsland, entryBuilder, parent, "", refreshPickerRef[0], expandedState));
		for (AbstractConfigListEntry<?> entry : currentFolderEntries) {
			category.addEntry(entry);
		}

		return builder.build();
	}

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

	/** Cloth Config's "Save & Done" button (labeled "Save & Quit" by its own translation) is left
	 *  permanently clickable on this screen (via the always-true DirtyMarkerEntry above) so it
	 *  works as a Return button — its own saving runnable is a no-op, so clicking it just
	 *  navigates back to the parent screen. Renaming it here to make that purpose obvious. Unlike
	 *  the Cancel button (whose label AbstractConfigScreen.tick() overwrites every tick based on
	 *  isEdited()), the save button's label is only ever set once at init(), so this sticks. */
	private static void relabelSaveButton(Screen screenObj) {
		if (!(screenObj instanceof ClothConfigScreen clothScreen)) {
			return;
		}
		for (net.minecraft.client.gui.components.events.GuiEventListener child : clothScreen.children()) {
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
					&& "Save & Quit".equals(widget.getMessage().getString())) {
				widget.setMessage(Component.literal("Return"));
			}
		}
	}

	private List<AbstractConfigListEntry<?>> buildFolderList(Map<String, List<NpcDatabaseEntry>> byIsland,
			ConfigEntryBuilder entryBuilder, Screen parentScreen, String normalizedQuery, Runnable refreshPicker,
			Map<String, Boolean> expandedState) {
		Set<String> addedSourceIds = new HashSet<>();
		for (NpcRule rule : workingRules) {
			if (rule.sourceId != null) {
				addedSourceIds.add(rule.sourceId);
			}
		}

		List<AbstractConfigListEntry<?>> result = new ArrayList<>();
		for (Map.Entry<String, List<NpcDatabaseEntry>> island : byIsland.entrySet()) {
			AbstractConfigListEntry<?> entry = buildIslandSubCategory(
					island.getKey(), island.getValue(), entryBuilder, parentScreen, normalizedQuery, addedSourceIds,
					refreshPicker, expandedState);
			if (entry != null) {
				result.add(entry);
			}
		}
		return result;
	}

	/** Walks the currently-shown folder entries and records each SubCategory's current
	 *  expanded/collapsed state, so the next rebuild can restore it instead of resetting every
	 *  folder back to collapsed. */
	private void captureExpandedState(List<AbstractConfigListEntry<?>> entries, Map<String, Boolean> out) {
		for (AbstractConfigListEntry<?> entry : entries) {
			if (entry instanceof SubCategoryListEntry sub) {
				out.put(sub.getCategoryName().getString(), sub.isExpanded());
			}
		}
	}

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

	private AbstractConfigListEntry<?> buildIslandSubCategory(String island, List<NpcDatabaseEntry> npcs,
			ConfigEntryBuilder entryBuilder, Screen parentScreen, String normalizedQuery, Set<String> addedSourceIds,
			Runnable refreshPicker, Map<String, Boolean> expandedState) {
		boolean filtering = !normalizedQuery.isEmpty();

		List<NpcDatabaseEntry> matching = new ArrayList<>();
		for (NpcDatabaseEntry npc : npcs) {
			if (addedSourceIds.contains(npc.id)) {
				continue;
			}
			if (!filtering || npc.displayName.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
				matching.add(npc);
			}
		}
		if (matching.isEmpty()) {
			return null;
		}

		SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.literal(island))
				.setExpanded(filtering || expandedState.getOrDefault(island, false));
		for (NpcDatabaseEntry npc : matching) {
			sub.add(buildNpcButton(npc, parentScreen, refreshPicker));
		}
		return sub.build();
	}

	private ButtonEntry buildNpcButton(NpcDatabaseEntry npc, Screen parentScreen, Runnable refreshPicker) {
		return new ButtonEntry(Component.literal(npc.displayName), Component.literal("Add"), () -> {
			NpcRule newRule = createRuleForNpc(npc);
			workingRules.add(newRule);
			liveAddRuleEntry(parentScreen, newRule);
			refreshPicker.run();
		});
	}

	/** The database groups an NPC we don't actually know the island for under a real, non-null
	 *  "Unknown" folder (byIsland()'s TreeMap grouping can't take a null key) — but the rule it
	 *  creates should be genuinely unrestricted, not restricted to a fake island that will never
	 *  match, so that placeholder is translated to null here specifically. */
	private static final String UNKNOWN_ISLAND = "Unknown";

	private NpcRule createRuleForNpc(NpcDatabaseEntry npc) {
		NpcRule rule = new NpcRule();
		rule.label = npc.displayName;
		rule.island = UNKNOWN_ISLAND.equals(npc.island) ? null : npc.island;
		rule.fixed = npc.fixed;
		rule.sourceId = npc.id;
		if (npc.fixed) {
			rule.x = npc.x;
			rule.y = npc.y;
			rule.z = npc.z;
		} else {
			rule.namePattern = npc.matchText;
			rule.nameMatchMode = NameMatchMode.CONTAINS;
		}
		return rule;
	}

	private AbstractConfigListEntry<?> buildRuleSubCategory(NpcRule rule, ConfigEntryBuilder entryBuilder) {
		// Fixed NPCs are waypoints, never matched, so only moving-NPC rules can be inert.
		String inertReason = rule.fixed ? null : highlightManager.inertRules().get(rule.id);
		SubCategoryBuilder sub = entryBuilder.startSubCategory(RuleWarning.title(rule.label, inertReason)).setExpanded(false);
		if (inertReason != null) {
			sub.add(RuleWarning.explanation(entryBuilder, inertReason));
		}
		AbstractConfigListEntry<?>[] selfRef = new AbstractConfigListEntry<?>[1];

		sub.add(entryBuilder.startStrField(Component.literal("Label"), rule.label)
				.setSaveConsumer(value -> rule.label = value)
				.build());
		sub.add(entryBuilder.startBooleanToggle(Component.literal("Enabled"), rule.enabled)
				.setSaveConsumer(value -> rule.enabled = value)
				.build());
		sub.add(entryBuilder.startStrField(Component.literal("Restrict to Island (blank = any)"), rule.island == null ? "" : rule.island)
				.setSaveConsumer(value -> rule.island = value.isBlank() ? null : value)
				.build());

		if (rule.fixed) {
			// The block the waypoint is drawn at: the NPC list's current position when the rule came from it.
			Vec3 at =NpcWaypointMarkers.position(rule, NpcDatabase::byId);
			sub.add(entryBuilder.startTextDescription(Component.literal(
					String.format("Fixed waypoint at %.0f, %.0f, %.0f", Math.floor(at.x), Math.floor(at.y), Math.floor(at.z)))).build());
		} else {
			sub.add(entryBuilder.startEnumSelector(Component.literal("Name Match Mode"), NameMatchMode.class, rule.nameMatchMode)
					.setSaveConsumer(value -> rule.nameMatchMode = value)
					.build());
			sub.add(entryBuilder.startStrField(Component.literal("Name Pattern"), rule.namePattern)
					.setSaveConsumer(value -> rule.namePattern = value)
					.build());
		}

		String initialHex = String.format("%06X", rule.color & 0xFFFFFF);
		sub.add(new ColorWheelFieldEntry(Component.literal("Color"), rule.color, value -> {
			if (isValidHexColor(value) && !value.equalsIgnoreCase(initialHex)) {
				rule.color = Integer.parseInt(value, 16);
			}
		}));

		sub.add(new ButtonEntry(Component.literal("Delete"), Component.literal("Delete this NPC"), () -> {
			workingRules.remove(rule);
			liveRemoveRuleEntry(Minecraft.getInstance().gui.screen(), selfRef[0]);
		}));

		AbstractConfigListEntry<?> built = sub.build();
		selfRef[0] = built;
		return built;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void liveAddRuleEntry(Screen screenObj, NpcRule rule) {
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
