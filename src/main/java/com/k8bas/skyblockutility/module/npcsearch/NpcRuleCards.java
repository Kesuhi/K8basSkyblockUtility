package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.settings.RuleFields;
import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.InfoOption;
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
 * NPC Search's rule cards (REQ-UI-11): label, enabled, island, name match and pattern for a moving NPC,
 * the position read-only for a fixed one, colour and a destructive Delete. Every edit is stored at once
 * and {@code changed} applies it (outlines and waypoints follow live); Delete is written at once by
 * the screen. Kept per rule object, so a card keeps its widgets while the rule lives.
 */
public final class NpcRuleCards {
	static final String RULE_ID = "npc_search.rule.";
	private static final String KEY = "modules.npc_search.rules[].";

	private final NpcSearchConfig config;
	private final Function<NpcRule, String> problem;
	private final Function<NpcRule, String> position;
	private final Runnable changed;
	private final Map<NpcRule, RuleGroup> groups = new IdentityHashMap<>();

	/**
	 * @param problem  why a moving NPC's rule does nothing, or null (a fixed one always works)
	 * @param position a fixed NPC's position as shown, e.g. "12, 70, -34"
	 * @param changed  stores the section and rebuilds the outlines and waypoints after an edit
	 */
	public NpcRuleCards(NpcSearchConfig config, Function<NpcRule, String> problem, Function<NpcRule, String> position, Runnable changed) {
		this.config = config;
		this.problem = problem;
		this.position = position;
		this.changed = changed;
	}

	/** The rules now, in list order. */
	public List<RuleGroup> groups() {
		List<RuleGroup> shown = new ArrayList<>(config.rules.size());
		for (NpcRule rule : config.rules) {
			shown.add(groups.computeIfAbsent(rule, this::group));
		}
		// Forget the cards of rules that are gone (deleted elsewhere, a reload).
		Set<NpcRule> live = Collections.newSetFromMap(new IdentityHashMap<>());
		live.addAll(config.rules);
		groups.keySet().retainAll(live);
		return shown;
	}

	private RuleGroup group(NpcRule rule) {
		String id = RULE_ID + rule.id;
		ProblemMemo memo = new ProblemMemo(rule);
		List<Option> fields = new ArrayList<>();
		fields.add(Toggle.of(id + ".enabled", KEY + "enabled", new OptionText("Enabled", "Off: the rule is kept but finds nothing.", "", List.of()),
				true, Binding.of(() -> rule.enabled, value -> edit(() -> rule.enabled = value))));
		fields.add(TextOption.of(id + ".label", KEY + "label", new OptionText("Label", "The NPC's name in this list and on its waypoint.", "", List.of()),
				"New NPC", 100, "(unnamed)", Binding.of(() -> rule.label == null ? "" : rule.label, value -> edit(() -> rule.label = value))));
		fields.add(RuleFields.island(id + ".island", KEY + "island", () -> rule.island, value -> edit(() -> rule.island = value)));
		if (rule.fixed) {
			fields.add(new InfoOption(id + ".position",
					new OptionText("Position", "A fixed NPC: its waypoint stands here. Not editable.", "", List.of()), () -> position.apply(rule)));
		} else {
			fields.add(RuleFields.matchMode(id + ".mode", KEY + "nameMatchMode", () -> rule.nameMatchMode,
					value -> edit(() -> rule.nameMatchMode = value), false));
			fields.add(TextOption.of(id + ".pattern", KEY + "namePattern",
					new OptionText("Name pattern", "The text the NPC's name is compared with.", "", List.of()), "", 256, "",
					Binding.of(() -> rule.namePattern == null ? "" : rule.namePattern, value -> edit(() -> rule.namePattern = value)))
					.invalidWhen(() -> RuleFields.aboutPattern(memo.get())));
		}
		fields.add(ColorOption.of(id + ".colour", KEY + "color",
				new OptionText("Colour", rule.fixed ? "The waypoint label's colour, when labels are not white." : "The outline's colour.", "",
						List.of()), 0x0AA351, false, Binding.of(() -> rule.color & 0xFFFFFF, value -> edit(() -> rule.color = value & 0xFFFFFF))));
		fields.add(new ActionOption(id + ".delete", new OptionText("Delete", "Removes this NPC from the list.", "", List.of()), "Delete NPC", true,
				() -> delete(rule)));
		return new RuleGroup(id, () -> rule.label, () -> rule.color, memo::get, () -> List.of(nullToEmpty(rule.label), nullToEmpty(rule.namePattern)),
				fields);
	}

	private void edit(Runnable store) {
		store.run();
		changed.run();
	}

	private void delete(NpcRule rule) {
		config.rules.remove(rule);
		groups.remove(rule);
		changed.run();
	}

	private static String nullToEmpty(String text) {
		return text == null ? "" : text;
	}

	/** The rule's problem, worked out again only when a field it depends on changed (not every frame). */
	private final class ProblemMemo {
		private final NpcRule rule;
		private List<Object> key;
		private String value;

		ProblemMemo(NpcRule rule) {
			this.rule = rule;
		}

		String get() {
			if (rule.fixed) {
				return null;
			}
			List<Object> now = Arrays.asList(rule.nameMatchMode, rule.namePattern);
			if (!Objects.equals(now, key)) {
				key = now;
				value = problem.apply(rule);
			}
			return value;
		}
	}
}
