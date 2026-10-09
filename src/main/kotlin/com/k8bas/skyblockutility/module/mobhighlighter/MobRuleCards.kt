package com.k8bas.skyblockutility.module.mobhighlighter

import com.k8bas.skyblockutility.highlight.HighlightRule
import com.k8bas.skyblockutility.settings.RuleFields
import com.k8bas.skyblockutility.ui.option.ActionOption
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.ColorOption
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.RuleGroup
import com.k8bas.skyblockutility.ui.option.TextOption
import com.k8bas.skyblockutility.ui.option.Toggle
import java.util.Collections
import java.util.IdentityHashMap
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Mob Highlighter's rule cards (REQ-UI-11): one per rule, with enabled, label, entity type, island,
 * name match, name pattern and colour, and a destructive Remove in the header. A rule from the Mob
 * Database (it has a `sourceId`) shows only enabled, entity type and colour: the database gave it the
 * rest (R31). Every edit is stored at once and `changed` applies it (the highlights follow live);
 * Remove is written at once by the screen. Kept per rule object, so a card keeps its widgets while
 * the rule lives.
 *
 * @param problem why a rule does nothing, or null (HighlightManager.inertReason)
 * @param changed stores the section and rebuilds the highlights after an edit
 */
class MobRuleCards(
	private val config: MobHighlighterConfig,
	private val problem: Function<HighlightRule, String?>,
	private val changed: Runnable,
) {
	private val groups = IdentityHashMap<HighlightRule, RuleGroup>()

	/** The rules now, in list order. */
	fun groups(): List<RuleGroup> {
		val shown = ArrayList<RuleGroup>(config.rules.size)
		for (rule in config.rules) {
			shown.add(groups.computeIfAbsent(rule) { group(it) })
		}
		// Forget the cards of rules that are gone (deleted elsewhere, a reload).
		val live: MutableSet<HighlightRule> = Collections.newSetFromMap(IdentityHashMap())
		live.addAll(config.rules)
		groups.keys.retainAll(live)
		return shown
	}

	private fun group(rule: HighlightRule): RuleGroup {
		val id = RULE_ID + rule.id
		val memo = ProblemMemo(rule)
		// A rule from the database: its label, island, name match and pattern are the database's (R31). One that
		// already does nothing (edited before 1.2.0, e.g. an invalid pattern) keeps its full card, so it can be fixed.
		val fromDatabase = rule.sourceId != null && memo.get() == null
		val fields = ArrayList<Option>()
		fields.add(
			Toggle.of(
				"$id.enabled", KEY + "enabled", OptionText("Enabled", "Off: the rule is kept but outlines nothing.", "", java.util.List.of()),
				true, Binding.of({ rule.enabled }, { value -> edit { rule.enabled = value } }),
			),
		)
		if (!fromDatabase) {
			fields.add(
				TextOption.of(
					"$id.label", KEY + "label", OptionText("Label", "The rule's name in this list.", "", java.util.List.of()), "New Rule", 100,
					"(unnamed)", Binding.of({ rule.label ?: "" }, { value -> edit { rule.label = value } }),
				),
			)
		}
		fields.add(
			TextOption.of(
				"$id.type", KEY + "entityTypeId",
				OptionText("Entity type", "Only this kind of entity, e.g. minecraft:zombie; empty for any.", "", java.util.List.of()), "", 64,
				"any type",
				Binding.of(
					{ rule.entityTypeId ?: "" },
					// Java's isBlank and strip: Character.isWhitespace only.
					{ value -> edit { rule.entityTypeId = if (value.all(Character::isWhitespace)) null else value.trim(Character::isWhitespace) } },
				),
			).invalidWhen { RuleFields.aboutType(memo.get()) },
		)
		if (!fromDatabase) {
			fields.add(RuleFields.island("$id.island", KEY + "island", Supplier { rule.island }, Consumer { value -> edit { rule.island = value } }))
			fields.add(
				RuleFields.matchMode(
					"$id.mode", KEY + "nameMatchMode", Supplier { rule.nameMatchMode }, Consumer { value -> edit { rule.nameMatchMode = value } },
				),
			)
			fields.add(
				TextOption.of(
					"$id.pattern", KEY + "namePattern",
					OptionText("Name pattern", "The text the entity's name is compared with.", "", java.util.List.of()), "", 256, "",
					Binding.of({ rule.namePattern ?: "" }, { value -> edit { rule.namePattern = value } }),
				).invalidWhen { RuleFields.aboutPattern(memo.get()) },
			)
		}
		fields.add(
			ColorOption.of(
				"$id.colour", KEY + "color", OptionText("Colour", "The outline's colour.", "", java.util.List.of()), 0xFF0000, false,
				Binding.of({ rule.color and 0xFFFFFF }, { value -> edit { rule.color = value and 0xFFFFFF } }),
			),
		)
		return RuleGroup(
			id, Supplier { rule.label }, IntSupplier { rule.color }, Supplier { memo.get() },
			Supplier { java.util.List.of(nullToEmpty(rule.label), nullToEmpty(rule.namePattern)) }, fields,
			ActionOption("$id.delete", OptionText("Remove", "Removes this rule from the list.", "", java.util.List.of()), "Remove", true, Runnable { delete(rule) }),
		)
	}

	private fun edit(store: () -> Unit) {
		store()
		changed.run()
	}

	private fun delete(rule: HighlightRule) {
		config.rules.remove(rule)
		groups.remove(rule)
		changed.run()
	}

	/** The rule's problem, worked out again only when a field it depends on changed (not every frame). */
	private inner class ProblemMemo(private val rule: HighlightRule) {
		private var key: List<Any?>? = null
		private var value: String? = null

		fun get(): String? {
			val now: List<Any?> = java.util.Arrays.asList(rule.nameMatchMode, rule.namePattern, rule.entityTypeId)
			if (now != key) {
				key = now
				value = problem.apply(rule)
			}
			return value
		}
	}

	companion object {
		const val RULE_ID: String = "mob_highlighter.rule."
		private const val KEY = "modules.mob_highlighter.rules[]."

		private fun nullToEmpty(text: String?): String = text ?: ""
	}
}
