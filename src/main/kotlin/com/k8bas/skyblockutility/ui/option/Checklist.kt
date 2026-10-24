package com.k8bas.skyblockutility.ui.option

import java.util.Locale

/**
 * On/off items of one card under one option (REQ-UI-61, R60): its button shows how many are on and opens one check box
 * per item under the card. Each item is the [Toggle] it would be on its own card, with its id, config field and default,
 * so the checklist itself stores nothing and a card can move its toggles into one without a migration.
 *
 * A class rather than a data class: the items are copied on construction. Java sees `items()`.
 */
class Checklist(private val id: String, private val text: OptionText, items: List<Toggle>) : Option {
	@get:JvmName("items")
	val items: List<Toggle> = java.util.List.copyOf(items)

	override fun id(): String = id

	/** Nothing is stored under it; its items keep their own keys. */
	override fun storageKey(): String = "checklist:$id"

	override fun text(): OptionText = text

	override fun amber(): Boolean = false

	override fun status(): OptionStatus = OptionStatus.OK

	/** The ids of the items on in a fresh config. */
	override fun defaultValue(): List<String> = items.filter { it.defaultValue() }.map { it.id() }

	/** The item labels, searched as a dropdown's entry labels are (REQ-UI-08). */
	override fun searchLabels(): List<String> = items.map { it.text().title }

	/** The button's text: the items on out of all, "6/10". */
	fun summary(): String = "${items.count { it.binding.get() }}/${items.size}"

	/** Whether the search's [query] (as [SearchIndex.normalize] gives it) is in an item's label, which opens the list. */
	fun matches(query: String): Boolean = query.isNotEmpty() && items.any { it.text().title.lowercase(Locale.ROOT).contains(query) }

	override fun equals(other: Any?): Boolean = other is Checklist && id == other.id && text == other.text && items == other.items

	override fun hashCode(): Int = listOf(id, text, items).hashCode()

	override fun toString(): String = "Checklist[id=$id, text=$text, items=$items]"
}
