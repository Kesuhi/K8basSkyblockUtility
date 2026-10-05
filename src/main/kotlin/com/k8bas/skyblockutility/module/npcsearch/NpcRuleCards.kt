package com.k8bas.skyblockutility.module.npcsearch

import com.k8bas.skyblockutility.settings.RuleFields
import com.k8bas.skyblockutility.ui.option.ActionOption
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.ColorOption
import com.k8bas.skyblockutility.ui.option.InfoOption
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
 * NPC Search's rule cards (REQ-UI-11): label, enabled, island, name match and pattern for a moving NPC,
 * the position read-only for a fixed one, colour and a destructive Delete. Every edit is stored at once
 * and `changed` applies it (outlines and waypoints follow live); Delete is written at once by
 * the screen. Kept per rule object, so a card keeps its widgets while the rule lives.
 *
 * @param problem  why a moving NPC's rule does nothing, or null (a fixed one always works)
 * @param position a fixed NPC's position as shown, e.g. "12, 70, -34"
 * @param changed  stores the section and rebuilds the outlines and waypoints after an edit
 */
class NpcRuleCards(
	private val config: NpcSearchConfig,
	private val problem: Function<NpcRule, String?>,
	private val position: Function<NpcRule, String>,
	private val changed: Runnable,
) {
	private val groups = IdentityHashMap<NpcRule, RuleGroup>()

	/** The rules now, in list order. */
	fun groups(): List<RuleGroup> {
		val shown = ArrayList<RuleGroup>(config.rules.size)
		for (rule in config.rules) {
			shown.add(groups.computeIfAbsent(rule) { group(it) })
		}
		// Forget the cards of rules that are gone (deleted elsewhere, a reload).
		val live: MutableSet<NpcRule> = Collections.newSetFromMap(IdentityHashMap())
		live.addAll(config.rules)
		groups.keys.retainAll(live)
		return shown
	}

	private fun group(rule: NpcRule): RuleGroup {
		val id = RULE_ID + rule.id
		val memo = ProblemMemo(rule)
		val fields = ArrayList<Option>()
		fields.add(
			Toggle.of(
				"$id.enabled", KEY + "enabled", OptionText("Enabled", "Off: the rule is kept but finds nothing.", "", java.util.List.of()),
				true, Binding.of({ rule.enabled }, { value -> edit { rule.enabled = value } }),
			),
		)
		fields.add(
			TextOption.of(
				"$id.label", KEY + "label", OptionText("Label", "The NPC's name in this list and on its waypoint.", "", java.util.List.of()),
				"New NPC", 100, "(unnamed)", Binding.of({ rule.label ?: "" }, { value -> edit { rule.label = value } }),
			),
		)
		fields.add(RuleFields.island("$id.island", KEY + "island", Supplier { rule.island }, Consumer { value -> edit { rule.island = value } }))
		if (rule.fixed) {
			fields.add(
				InfoOption(
					"$id.position",
					OptionText("Position", "A fixed NPC: its waypoint stands here. Not editable.", "", java.util.List.of()),
					Supplier { position.apply(rule) },
				),
			)
		} else {
			fields.add(
				RuleFields.matchMode(
					"$id.mode", KEY + "nameMatchMode", Supplier { rule.nameMatchMode },
					Consumer { value -> edit { rule.nameMatchMode = value } }, false,
				),
			)
			fields.add(
				TextOption.of(
					"$id.pattern", KEY + "namePattern",
					OptionText("Name pattern", "The text the NPC's name is compared with.", "", java.util.List.of()), "", 256, "",
					Binding.of({ rule.namePattern ?: "" }, { value -> edit { rule.namePattern = value } }),
				).invalidWhen { RuleFields.aboutPattern(memo.get()) },
			)
		}
		fields.add(
			ColorOption.of(
				"$id.colour", KEY + "color",
				OptionText(
					"Colour", if (rule.fixed) "The waypoint label's colour, when labels are not white." else "The outline's colour.", "",
					java.util.List.of(),
				),
				0x0AA351, false, Binding.of({ rule.color and 0xFFFFFF }, { value -> edit { rule.color = value and 0xFFFFFF } }),
			),
		)
		fields.add(
			ActionOption("$id.delete", OptionText("Delete", "Removes this NPC from the list.", "", java.util.List.of()), "Delete NPC", true, Runnable { delete(rule) }),
		)
		return RuleGroup(
			id, Supplier { rule.label }, IntSupplier { rule.color }, Supplier { memo.get() },
			Supplier { java.util.List.of(nullToEmpty(rule.label), nullToEmpty(rule.namePattern)) }, fields,
		)
	}

	private fun edit(store: () -> Unit) {
		store()
		changed.run()
	}

	private fun delete(rule: NpcRule) {
		config.rules.remove(rule)
		groups.remove(rule)
		changed.run()
	}

	/** The rule's problem, worked out again only when a field it depends on changed (not every frame). */
	private inner class ProblemMemo(private val rule: NpcRule) {
		private var key: List<Any?>? = null
		private var value: String? = null

		fun get(): String? {
			if (rule.fixed) {
				return null
			}
			val now: List<Any?> = java.util.Arrays.asList(rule.nameMatchMode, rule.namePattern)
			if (now != key) {
				key = now
				value = problem.apply(rule)
			}
			return value
		}
	}

	companion object {
		const val RULE_ID: String = "npc_search.rule."
		private const val KEY = "modules.npc_search.rules[]."

		private fun nullToEmpty(text: String?): String = text ?: ""
	}
}
