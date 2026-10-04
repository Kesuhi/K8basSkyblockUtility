package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.highlight.HighlightManager;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.settings.ListDatabase;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Outlines mobs by rule. Its settings are cards on the settings screen (MobHighlighterOptions): the
 * rules are edited in place (MobRuleCards) and added from the Mob Database through the picker; every
 * change applies at once.
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
	/** The rule cards of the settings screen, for the config they were made from. */
	private MobRuleCards ruleCards;
	/** "Add from database" of the settings screen. */
	private RuleDatabase database;
	private MobHighlighterConfig ruleCardsConfig;

	@Override
	public String id() {
		return ID;
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
		ConfigManager.save();
	}

	@Override
	public List<Card> cards() {
		if (ruleCards == null || ruleCardsConfig != config) {
			ruleCards = new MobRuleCards(config, HighlightManager::inertReason, this::rulesChanged);
			ruleCardsConfig = config;
		}
		if (database == null) {
			database = new ListDatabase<>("Mob Database", () -> {
				MobDatabase.fetchIfNeeded();
				return MobDatabase.state();
			}, MobDatabase::entries, mob -> new RuleDatabase.Entry(mob.id, mob.displayName, mob.island, mob.subfolder, mob.matchText),
					this::usedSources, mob -> {
						config.rules.add(createRuleForMob(mob));
						rulesChanged();
					});
		}
		return MobHighlighterOptions.cards(config, ConfigManager.general(), this::setEnabled, ruleCards::groups, database);
	}

	/** The database entries the rules already come from (the picker hides them). */
	private Set<String> usedSources() {
		Set<String> ids = new HashSet<>();
		for (HighlightRule rule : config.rules) {
			if (rule.sourceId != null) {
				ids.add(rule.sourceId);
			}
		}
		return ids;
	}

	/** A rule edited, added or deleted in the settings screen: stored in the section and applied at once (REQ-UI-15). */
	private void rulesChanged() {
		ConfigManager.putModuleSection(ID, config);
		highlightManager.rebuild(config.rules);
	}

	/** For gametests: replaces the rules without saving them. */
	void useRulesForTest(List<HighlightRule> rules) {
		config.rules = new ArrayList<>(rules);
		highlightManager.rebuild(config.rules);
	}

	/** For gametests: the module's rules. */
	List<HighlightRule> rulesForTest() {
		return List.copyOf(config.rules);
	}

	@Override
	public void onSettingsClosed() {
		// The config file holds a copy of this section; refresh it before the screen's single write.
		ConfigManager.putModuleSection(ID, config);
		highlightManager.rebuild(config.rules);
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
}
