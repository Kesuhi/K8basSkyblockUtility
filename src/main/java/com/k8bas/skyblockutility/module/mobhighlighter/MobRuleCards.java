package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.settings.RuleFields;
import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.option.TextOption;
import com.k8bas.skyblockutility.ui.option.Toggle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Mob Highlighter's rule cards (REQ-UI-11): one per rule, with label, enabled, entity type, island,
 * name match, name pattern, colour and a destructive Delete. Every edit is stored at once and
 * {@code changed} applies it (the highlights follow live); Delete is written at once by the screen.
 * Kept per rule object, so a card keeps its widgets while the rule lives.
 */
public final class MobRuleCards {
	static final String RULE_ID = "mob_highlighter.rule.";
	private static final String KEY = "modules.mob_highlighter.rules[].";

	private final MobHighlighterConfig config;
	private final Function<HighlightRule, String> problem;
	private final Runnable changed;
	private final Map<HighlightRule, RuleGroup> groups = new IdentityHashMap<>();

	/**
	 * @param problem why a rule does nothing, or null (HighlightManager.inertReason)
	 * @param changed stores the section and rebuilds the highlights after an edit
	 */
	public MobRuleCards(MobHighlighterConfig config, Function<HighlightRule, String> problem, Runnable changed) {
		this.config = config;
		this.problem = problem;
		this.changed = changed;
	}

	/** The rules now, in list order. */
	public List<RuleGroup> groups() {
		List<RuleGroup> shown = new ArrayList<>(config.rules.size());
		for (HighlightRule rule : config.rules) {
			shown.add(groups.computeIfAbsent(rule, this::group));
		}
		// Forget the cards of rules that are gone (deleted elsewhere, a reload).
		Set<HighlightRule> live = Collections.newSetFromMap(new IdentityHashMap<>());
		live.addAll(config.rules);
		groups.keySet().retainAll(live);
		return shown;
	}

	private RuleGroup group(HighlightRule rule) {
		String id = RULE_ID + rule.id;
		ProblemMemo memo = new ProblemMemo(rule);
		List<Option> fields = List.of(
				Toggle.of(id + ".enabled", KEY + "enabled", new OptionText("Enabled", "Off: the rule is kept but outlines nothing.", "", List.of()),
						true, Binding.of(() -> rule.enabled, value -> edit(() -> rule.enabled = value))),
				TextOption.of(id + ".label", KEY + "label", new OptionText("Label", "The rule's name in this list.", "", List.of()), "New Rule", 100,
						"(unnamed)", Binding.of(() -> rule.label == null ? "" : rule.label, value -> edit(() -> rule.label = value))),
				TextOption.of(id + ".type", KEY + "entityTypeId",
						new OptionText("Entity type", "Only this kind of entity, e.g. minecraft:zombie; empty for any.", "", List.of()), "", 64,
						"any type", Binding.of(() -> rule.entityTypeId == null ? "" : rule.entityTypeId,
								value -> edit(() -> rule.entityTypeId = value.isBlank() ? null : value.strip())))
						.invalidWhen(() -> RuleFields.aboutType(memo.get())),
				RuleFields.island(id + ".island", KEY + "island", () -> rule.island, value -> edit(() -> rule.island = value)),
				RuleFields.matchMode(id + ".mode", KEY + "nameMatchMode", () -> rule.nameMatchMode, value -> edit(() -> rule.nameMatchMode = value)),
				TextOption.of(id + ".pattern", KEY + "namePattern",
						new OptionText("Name pattern", "The text the entity's name is compared with.", "", List.of()), "", 256, "",
						Binding.of(() -> rule.namePattern == null ? "" : rule.namePattern, value -> edit(() -> rule.namePattern = value)))
						.invalidWhen(() -> RuleFields.aboutPattern(memo.get())),
				ColorOption.of(id + ".colour", KEY + "color", new OptionText("Colour", "The outline's colour.", "", List.of()), 0xFF0000, false,
						Binding.of(() -> rule.color & 0xFFFFFF, value -> edit(() -> rule.color = value & 0xFFFFFF))),
				new ActionOption(id + ".delete", new OptionText("Delete", "Removes this rule.", "", List.of()), "Delete rule", true, () -> delete(rule)));
		return new RuleGroup(id, () -> rule.label, () -> rule.color, memo::get, () -> List.of(nullToEmpty(rule.label), nullToEmpty(rule.namePattern)),
				fields);
	}

	private void edit(Runnable store) {
		store.run();
		changed.run();
	}

	private void delete(HighlightRule rule) {
		config.rules.remove(rule);
		groups.remove(rule);
		changed.run();
	}

	private static String nullToEmpty(String text) {
		return text == null ? "" : text;
	}

	/** The rule's problem, worked out again only when a field it depends on changed (not every frame). */
	private final class ProblemMemo {
		private final HighlightRule rule;
		private List<Object> key;
		private String value;

		ProblemMemo(HighlightRule rule) {
			this.rule = rule;
		}

		String get() {
			List<Object> now = Arrays.asList(rule.nameMatchMode, rule.namePattern, rule.entityTypeId);
			if (!Objects.equals(now, key)) {
				key = now;
				value = problem.apply(rule);
			}
			return value;
		}
	}
}
