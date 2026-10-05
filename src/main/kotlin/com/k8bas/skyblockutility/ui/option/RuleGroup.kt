package com.k8bas.skyblockutility.ui.option

import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * One rule of a feature's list, shown as a collapsible card (REQ-UI-11): its header has the label and a
 * colour dot, and expanded it shows its fields. A rule that cannot be used has a [problem]: a
 * warning sign in the header and the reason under it (REQ-GLOW-10, REQ-UI-12). Its label and name
 * pattern are searchable (REQ-UI-09).
 *
 * A class rather than a data class: the fields are copied on construction. Java sees the record-style
 * accessors `id()`, `label()`, `colour()`, `problem()`, `searchFields()`, `fields()`.
 *
 * @param id           unique across every list, e.g. "mob_highlighter.rule.mob-1"; also its search entry's id
 * @param problem      why the rule does nothing, or null when it works
 * @param searchFields the texts the search matches (label and pattern)
 */
class RuleGroup(
	@get:JvmName("id") val id: String,
	@get:JvmName("label") val label: Supplier<String>,
	@get:JvmName("colour") val colour: IntSupplier,
	@get:JvmName("problem") val problem: Supplier<String?>,
	@get:JvmName("searchFields") val searchFields: Supplier<List<String>>,
	fields: List<Option>,
) {
	@get:JvmName("fields")
	val fields: List<Option> = java.util.List.copyOf(fields)

	override fun equals(other: Any?): Boolean = other is RuleGroup && id == other.id && label == other.label && colour == other.colour &&
		problem == other.problem && searchFields == other.searchFields && fields == other.fields

	override fun hashCode(): Int = listOf(id, label, colour, problem, searchFields, fields).hashCode()

	override fun toString(): String =
		"RuleGroup[id=$id, label=$label, colour=$colour, problem=$problem, searchFields=$searchFields, fields=$fields]"
}
