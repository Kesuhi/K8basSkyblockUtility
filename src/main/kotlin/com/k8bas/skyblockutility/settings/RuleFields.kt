package com.k8bas.skyblockutility.settings

import com.k8bas.skyblockutility.highlight.NameMatchMode
import com.k8bas.skyblockutility.location.Islands
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Choice
import com.k8bas.skyblockutility.ui.option.OptionText
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * Fields both rule lists share (REQ-UI-11): the island picked from the location list (REQ-LOC-09)
 * and the name match mode. Pure, so the rule cards can be tested without a game.
 */
object RuleFields {
	/** The island choice for "every island": stored as no island (null). */
	const val ANY_ISLAND: String = "Any island"

	/**
	 * The island list, with "Any island" first. A stored name that is not in the list (a hand edit, an
	 * island added later) is offered too, so it is never lost (REQ-UI-16).
	 *
	 * @param get the stored island, null for every island
	 * @param set stores an island, null for every island
	 */
	@JvmStatic
	fun island(id: String, storageKey: String, get: Supplier<String?>, set: Consumer<String?>): Choice<String> {
		val values = ArrayList<String>()
		values.add(ANY_ISLAND)
		Islands.all().forEach { values.add(it.name()) }
		val stored = get.get()
		if (stored != null && !isBlank(stored) && !values.contains(stored)) {
			values.add(stored)
		}
		return Choice.of(
			id,
			storageKey,
			OptionText("Island", "Only on this island; \"Any island\" for all of them.", "", listOf("island", "location", "area")),
			ANY_ISLAND,
			values,
			Function { it },
			Binding.of({ get.get()?.takeUnless(::isBlank) ?: ANY_ISLAND }, { value -> set.accept(if (ANY_ISLAND == value) null else value) }),
		)
	}

	/**
	 * @param anyName whether "Any name" is offered: not for NPC rules, which have no entity type to fall
	 *                back on (it could never work); a rule that already has it keeps it (REQ-UI-16)
	 */
	@JvmStatic
	@JvmOverloads
	fun matchMode(
		id: String,
		storageKey: String,
		get: Supplier<NameMatchMode?>,
		set: Consumer<NameMatchMode>,
		anyName: Boolean = true,
	): Choice<NameMatchMode> {
		val modes = mutableListOf(NameMatchMode.CONTAINS, NameMatchMode.EXACT, NameMatchMode.REGEX)
		if (anyName || get.get() == NameMatchMode.NONE) {
			modes.add(NameMatchMode.NONE)
		}
		return Choice.of(
			id,
			storageKey,
			OptionText(
				"Name match",
				"How the name pattern is compared with an entity's name.",
				"Contains: the name includes the pattern. Exact: the whole name equals it. Regular expression: the pattern is a " +
					"Java regex. Any name: only the entity type counts.",
				listOf("match", "regex", "pattern", "name"),
			),
			NameMatchMode.CONTAINS,
			modes,
			Function(::label),
			Binding.of({ get.get() ?: NameMatchMode.CONTAINS }, set),
		)
	}

	@JvmStatic
	fun label(mode: NameMatchMode): String = when (mode) {
		NameMatchMode.CONTAINS -> "Contains"
		NameMatchMode.EXACT -> "Exact"
		NameMatchMode.REGEX -> "Regular expression"
		NameMatchMode.NONE -> "Any name"
	}

	/** Whether a rule's problem is about its name pattern (REQ-UI-12: the pattern field is marked). */
	@JvmStatic
	fun aboutPattern(problem: String?): Boolean = problem != null && (problem.contains("pattern") || problem.contains("regular expression"))

	/** Whether a rule's problem is about its entity type (the type field is marked). */
	@JvmStatic
	fun aboutType(problem: String?): Boolean = problem != null && problem.contains("entity type")

	/** Java's String.isBlank: Character.isWhitespace only. */
	private fun isBlank(text: String): Boolean = text.all(Character::isWhitespace)
}
