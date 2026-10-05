package com.k8bas.skyblockutility.module.npcsearch

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.highlight.HighlightManager
import com.k8bas.skyblockutility.highlight.HighlightRule
import com.k8bas.skyblockutility.highlight.NameMatchMode
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.settings.ListDatabase
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.RuleDatabase
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.entity.Entity
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

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
class NpcSearchModule : Module {
	private val highlightManager = HighlightManager()
	private val foundTitles = FoundTitleGate()

	/** A title allowed while a screen was open; shown once the screen closes (EC-GLOW-08). */
	private var pendingTitle: Component? = null

	/** Null until onRegister, as before: reading it earlier throws NullPointerException. */
	private var config: NpcSearchConfig? = null
	private val cfg: NpcSearchConfig
		get() = config!!

	/** The rule cards of the settings screen, for the config they were made from. */
	private var ruleCards: NpcRuleCards? = null

	/** "Add from database" of the settings screen. */
	private var database: RuleDatabase? = null
	private var ruleCardsConfig: NpcSearchConfig? = null

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, NpcSearchConfig::class.java) { NpcSearchConfig() }
		highlightManager.setEnabled(cfg.enabled)
		highlightManager.setOnMatchListener { rule, entity -> onNpcMatched(rule, entity) }
		// Every location change is a new server or mode, so each run shows the titles again.
		IslandTracker.onChange(
			Consumer {
				foundTitles.reset()
				pendingTitle = null
			},
		)
		ClientTickEvents.END_CLIENT_TICK.register { showPendingTitle(it) }
		rebuildDerived()
		// Fetched at startup rather than lazily on first picker-open, so opening the picker for
		// the first time doesn't show the "still loading" message / a moment of an empty list.
		NpcDatabase.fetchIfNeeded()
		NpcWaypointMarkers.register()
		ModKeybinds.register(this)
	}

	/**
	 * Called on the client tick, from HighlightManager, whenever an unfixed NPC's rule matches a
	 * nearby entity. For the special NPCs only (R20), the first time the player can see the NPC on
	 * this server, a short vanilla title in the rule's own colour says so (FoundTitleGate).
	 */
	private fun onNpcMatched(rule: HighlightRule, entity: Entity) {
		val player = Minecraft.getInstance().player
		if (player != null && FoundTitleGate.isSpecial(rule.sourceId, rule.label) &&
			foundTitles.allow(rule.id, cfg.foundTitleEnabled, entity.isInvisible, BooleanSupplier { player.hasLineOfSight(entity) })
		) {
			pendingTitle = Component.literal("You found " + rule.label).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rule.color)))
		}
	}

	/** Shows an allowed title once no screen is open, so it is not used up behind a menu. */
	private fun showPendingTitle(client: Minecraft) {
		val title = pendingTitle
		if (title != null && client.gui.screen() == null) {
			client.gui.hud.resetTitleTimes()
			client.gui.hud.setTitle(title)
			pendingTitle = null
		}
	}

	/** For gametests: replaces the rules without saving them. */
	fun useRulesForTest(rules: List<NpcRule>) {
		cfg.rules = ArrayList(rules)
		rebuildDerived()
	}

	/** For gametests: the module's rules. */
	fun rulesForTest(): List<NpcRule> = java.util.List.copyOf(cfg.rules)

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		cfg.enabled = enabled
		highlightManager.setEnabled(enabled)
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
		rebuildDerived()
	}

	/**
	 * Splits config.rules into the unfixed subset (fed to HighlightManager as ordinary
	 * HighlightRules) and the fixed subset (fed to NpcWaypointMarkers directly). Waypoints are
	 * suppressed entirely (empty list) while the module is disabled, since the renderer itself
	 * has no idea whether the owning module is on.
	 */
	private fun rebuildDerived() {
		val searchRules = ArrayList<HighlightRule>()
		val waypointRules = ArrayList<NpcRule>()
		for (rule in cfg.rules) {
			if (rule.fixed) {
				waypointRules.add(rule)
			} else {
				searchRules.add(toHighlightRule(rule))
			}
		}
		highlightManager.rebuild(searchRules)
		NpcWaypointMarkers.update(if (cfg.enabled) waypointRules else java.util.List.of(), waypointSettings(cfg))
	}

	override fun cards(): List<Card> {
		if (ruleCards == null || ruleCardsConfig !== config) {
			ruleCards = NpcRuleCards(
				cfg, Function { rule -> HighlightManager.inertReason(toHighlightRule(rule)) }, Function { positionText(it) },
				Runnable { rulesChanged() },
			)
			ruleCardsConfig = config
		}
		if (database == null) {
			database = ListDatabase<NpcDatabaseEntry>(
				"NPC Database",
				Supplier {
					NpcDatabase.fetchIfNeeded()
					NpcDatabase.state()
				},
				Supplier { NpcDatabase.entries() },
				Function { npc -> RuleDatabase.Entry(npc.id, npc.displayName, npc.island, null, npc.matchText) },
				Supplier { usedSources() },
				Consumer { npc ->
					cfg.rules.add(createRuleForNpc(npc))
					rulesChanged()
				},
			)
		}
		// Bound now, as Java's ruleCards::groups was: a card keeps the rule cards it was made with.
		val cards = ruleCards!!
		return NpcSearchOptions.cards(cfg, Consumer { setEnabled(it) }, Runnable { rebuildDerived() }, Supplier { cards.groups() }, database)
	}

	/** The database entries the rules already come from (the picker hides them). */
	private fun usedSources(): Set<String> {
		val ids = HashSet<String>()
		for (rule in cfg.rules) {
			val source = rule.sourceId
			if (source != null) {
				ids.add(source)
			}
		}
		return ids
	}

	/** A rule edited, added or deleted in the settings screen: stored in the section and applied at once (REQ-UI-15). */
	private fun rulesChanged() {
		ConfigManager.putModuleSection(ID, cfg)
		rebuildDerived()
	}

	override fun onSettingsClosed() {
		// The config file holds a copy of this section; refresh it before the screen's single write.
		ConfigManager.putModuleSection(ID, cfg)
		rebuildDerived()
	}

	private fun createRuleForNpc(npc: NpcDatabaseEntry): NpcRule {
		val rule = NpcRule()
		rule.label = npc.displayName
		rule.island = if (UNKNOWN_ISLAND == npc.island) null else npc.island
		rule.fixed = npc.fixed
		rule.sourceId = npc.id
		if (npc.fixed) {
			rule.x = npc.x
			rule.y = npc.y
			rule.z = npc.z
		} else {
			rule.namePattern = npc.matchText
			rule.nameMatchMode = NameMatchMode.CONTAINS
		}
		return rule
	}

	companion object {
		const val ID: String = "npc_search"

		/**
		 * The NPC list files an NPC whose island is not known under an "Unknown" folder; the rule made from it is
		 * unrestricted rather than tied to an island that never matches.
		 */
		private const val UNKNOWN_ISLAND = "Unknown"

		@JvmStatic
		fun waypointSettings(config: NpcSearchConfig): NpcWaypointMarkers.Settings =
			NpcWaypointMarkers.Settings(config.whiteWaypointLabels, config.showBeams, config.showDistance, config.islandBeamColors)

		/** A moving NPC's outline rule; its colour is the rule's own, never a beam colour (REQ-NPCWP-07). */
		@JvmStatic
		fun toHighlightRule(rule: NpcRule): HighlightRule {
			val highlightRule = HighlightRule()
			highlightRule.id = rule.id
			highlightRule.label = rule.label
			highlightRule.enabled = rule.enabled
			highlightRule.nameMatchMode = rule.nameMatchMode
			highlightRule.namePattern = rule.namePattern
			highlightRule.color = rule.color
			highlightRule.island = rule.island
			highlightRule.sourceId = rule.sourceId
			return highlightRule
		}

		/** A fixed NPC's waypoint position, as its card shows it (the NPC data's, or the rule's own). */
		@JvmStatic
		fun positionText(rule: NpcRule): String {
			val position = NpcWaypointMarkers.position(rule) { NpcDatabase.byId(it) }
			// Java's (int) cast: NaN to 0, out of range to the nearest bound, as Kotlin's toInt().
			return Math.floor(position.x).toInt().toString() + ", " + Math.floor(position.y).toInt() + ", " + Math.floor(position.z).toInt()
		}
	}
}
