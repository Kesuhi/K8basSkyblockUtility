package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.highlight.HighlightManager;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.settings.ListDatabase;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
	/** The rule cards of the settings screen, for the config they were made from. */
	private NpcRuleCards ruleCards;
	/** "Add from database" of the settings screen. */
	private RuleDatabase database;
	private NpcSearchConfig ruleCardsConfig;

	@Override
	public String id() {
		return ID;
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
	public List<Card> cards() {
		if (ruleCards == null || ruleCardsConfig != config) {
			ruleCards = new NpcRuleCards(config, rule -> HighlightManager.inertReason(toHighlightRule(rule)), NpcSearchModule::positionText,
					this::rulesChanged);
			ruleCardsConfig = config;
		}
		if (database == null) {
			database = new ListDatabase<>("NPC Database", () -> {
				NpcDatabase.fetchIfNeeded();
				return NpcDatabase.state();
			}, NpcDatabase::entries, npc -> new RuleDatabase.Entry(npc.id, npc.displayName, npc.island, null, npc.matchText),
					this::usedSources, npc -> {
						config.rules.add(createRuleForNpc(npc));
						rulesChanged();
					});
		}
		return NpcSearchOptions.cards(config, this::setEnabled, this::rebuildDerived, ruleCards::groups, database);
	}

	/** The database entries the rules already come from (the picker hides them). */
	private Set<String> usedSources() {
		Set<String> ids = new HashSet<>();
		for (NpcRule rule : config.rules) {
			if (rule.sourceId != null) {
				ids.add(rule.sourceId);
			}
		}
		return ids;
	}

	/** A rule edited, added or deleted in the settings screen: stored in the section and applied at once (REQ-UI-15). */
	private void rulesChanged() {
		ConfigManager.putModuleSection(ID, config);
		rebuildDerived();
	}

	/** A fixed NPC's waypoint position, as its card shows it (the NPC data's, or the rule's own). */
	static String positionText(NpcRule rule) {
		Vec3 position = NpcWaypointMarkers.position(rule, NpcDatabase::byId);
		return (int) Math.floor(position.x) + ", " + (int) Math.floor(position.y) + ", " + (int) Math.floor(position.z);
	}

	@Override
	public void onSettingsClosed() {
		// The config file holds a copy of this section; refresh it before the screen's single write.
		ConfigManager.putModuleSection(ID, config);
		rebuildDerived();
	}

	/** The NPC list files an NPC whose island is not known under an "Unknown" folder; the rule made from it is
	 *  unrestricted rather than tied to an island that never matches. */
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
}
