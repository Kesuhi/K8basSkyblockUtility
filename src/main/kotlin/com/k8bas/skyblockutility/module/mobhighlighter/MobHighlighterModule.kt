package com.k8bas.skyblockutility.module.mobhighlighter

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.highlight.HighlightManager
import com.k8bas.skyblockutility.highlight.HighlightRule
import com.k8bas.skyblockutility.highlight.NameMatchMode
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.settings.ListDatabase
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.RuleDatabase
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * Outlines mobs by rule. Its settings are cards on the settings screen (MobHighlighterOptions): the
 * rules are edited in place (MobRuleCards) and added from the Mob Database through the picker; every
 * change applies at once.
 */
class MobHighlighterModule : Module {
	private val highlightManager = HighlightManager()

	/** Null until onRegister, as before: reading it earlier throws NullPointerException. */
	private var config: MobHighlighterConfig? = null
	private val cfg: MobHighlighterConfig
		get() = config!!

	/** The rule cards of the settings screen, for the config they were made from. */
	private var ruleCards: MobRuleCards? = null

	/** "Add from database" of the settings screen. */
	private var database: RuleDatabase? = null
	private var ruleCardsConfig: MobHighlighterConfig? = null

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, MobHighlighterConfig::class.java) { MobHighlighterConfig() }
		highlightManager.setEnabled(cfg.enabled)
		highlightManager.rebuild(cfg.rules)
		// Fetched at startup rather than lazily on first picker-open, so opening the picker for
		// the first time doesn't show the "still loading" message / a moment of an empty list.
		MobDatabase.fetchIfNeeded()
		ModKeybinds.register(this)
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		cfg.enabled = enabled
		highlightManager.setEnabled(enabled)
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> {
		if (ruleCards == null || ruleCardsConfig !== config) {
			ruleCards = MobRuleCards(cfg, Function { HighlightManager.inertReason(it) }, Runnable { rulesChanged() })
			ruleCardsConfig = config
		}
		if (database == null) {
			database = ListDatabase<MobDatabaseEntry>(
				"Mob Database",
				Supplier {
					MobDatabase.fetchIfNeeded()
					MobDatabase.state()
				},
				Supplier { MobDatabase.entries() },
				Function { mob -> RuleDatabase.Entry(mob.id, mob.displayName, mob.island, mob.subfolder, mob.matchText) },
				Supplier { usedSources() },
				Consumer { mob ->
					cfg.rules.add(createRuleForMob(mob))
					rulesChanged()
				},
			)
		}
		// Bound now, as Java's ruleCards::groups was: a card keeps the rule cards it was made with.
		val cards = ruleCards!!
		return MobHighlighterOptions.cards(cfg, ConfigManager.general(), Consumer { setEnabled(it) }, Supplier { cards.groups() }, database)
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
		highlightManager.rebuild(cfg.rules)
	}

	/** For gametests: replaces the rules without saving them. */
	fun useRulesForTest(rules: List<HighlightRule>) {
		cfg.rules = ArrayList(rules)
		highlightManager.rebuild(cfg.rules)
	}

	/** For gametests: the module's rules. */
	fun rulesForTest(): List<HighlightRule> = java.util.List.copyOf(cfg.rules)

	override fun onSettingsClosed() {
		// The config file holds a copy of this section; refresh it before the screen's single write.
		ConfigManager.putModuleSection(ID, cfg)
		highlightManager.rebuild(cfg.rules)
	}

	private fun createRuleForMob(mob: MobDatabaseEntry): HighlightRule {
		val rule = HighlightRule()
		rule.label = mob.displayName
		rule.namePattern = mob.matchText
		rule.nameMatchMode = NameMatchMode.CONTAINS
		rule.color = 0xFF0000
		rule.sourceId = mob.id
		rule.island = if (UNGATED_ISLANDS.contains(mob.island)) null else mob.island
		return rule
	}

	companion object {
		const val ID: String = "mob_highlighter"

		/**
		 * Islands the mob database groups mobs under that aren't a single physical location — Jerry's
		 * Workshop can start on whatever island you're already on, and fishing happens all over the
		 * place, so a rule sourced from one of these is left ungated (active everywhere) rather than
		 * restricted to an island it isn't really tied to.
		 */
		private val UNGATED_ISLANDS: Set<String> = java.util.Set.of("Jerry", "Fishing", "Spooky Festival", "Mythological Creatures")
	}
}
