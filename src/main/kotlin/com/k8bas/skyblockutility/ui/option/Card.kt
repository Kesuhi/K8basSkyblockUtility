package com.k8bas.skyblockutility.ui.option

import java.util.function.Supplier

/**
 * A card in one category: a feature with its own toggle and its sub-options (REQ-UI-05), or a plain
 * section of General without a toggle (e.g. Keybinds). A feature with a rule list also has its rules,
 * read each time they are shown, as rules come and go (REQ-UI-11).
 *
 * A class rather than a data class: the options are copied on construction. Java sees the record-style
 * accessors `id()`, `category()`, `title()`, `toggle()`, `options()`, `ruleSource()`.
 *
 * @param toggle      the feature's own toggle, or null for a section
 * @param ruleSource  the feature's rules now, in list order; none for most cards
 */
class Card @JvmOverloads constructor(
	@get:JvmName("id") val id: String,
	@get:JvmName("category") val category: Category,
	@get:JvmName("title") val title: String,
	@get:JvmName("toggle") val toggle: Toggle?,
	options: List<Option>,
	@get:JvmName("ruleSource") val ruleSource: Supplier<List<RuleGroup>> = Supplier { java.util.List.of() },
) {
	@get:JvmName("options")
	val options: List<Option> = java.util.List.copyOf(options)

	/** The toggle first, then the sub-options; the rules' fields are not among them. */
	fun all(): List<Option> = ArrayList<Option>(options.size + 1).apply {
		toggle?.let(::add)
		addAll(options)
	}

	/** The rules now. */
	fun rules(): List<RuleGroup> = ruleSource.get()

	override fun equals(other: Any?): Boolean = other is Card && id == other.id && category == other.category && title == other.title &&
		toggle == other.toggle && options == other.options && ruleSource == other.ruleSource

	override fun hashCode(): Int = listOf(id, category, title, toggle, options, ruleSource).hashCode()

	override fun toString(): String =
		"Card[id=$id, category=$category, title=$title, toggle=$toggle, options=$options, ruleSource=$ruleSource]"
}
