package com.k8bas.skyblockutility.ui.option

import java.util.EnumMap
import java.util.Locale

/**
 * The settings search (REQ-UI-08): a case-insensitive substring match over each option's title,
 * description, keywords and dropdown labels (not its tooltip), with hit counts per category for the
 * sidebar badges. Built from the same declarations as the cards (REQ-UI-07), plus each rule's label and
 * name pattern (REQ-UI-09).
 */
class SearchIndex private constructor(entries: List<Entry>) {
	private val entries: List<Entry> = java.util.List.copyOf(entries)

	/**
	 * One searchable thing: an option, or a rule. Its fields are stored lower-case. A class rather than a
	 * data class, as the fields are lower-cased on construction; Java sees `category()`, `cardId()`,
	 * `id()`, `fields()`.
	 */
	class Entry(
		@get:JvmName("category") val category: Category,
		@get:JvmName("cardId") val cardId: String,
		@get:JvmName("id") val id: String,
		fields: List<String>,
	) {
		@get:JvmName("fields")
		val fields: List<String> = fields.stream().map { it.lowercase(Locale.ROOT) }.toList()

		override fun equals(other: Any?): Boolean =
			other is Entry && category == other.category && cardId == other.cardId && id == other.id && fields == other.fields

		override fun hashCode(): Int = listOf(category, cardId, id, fields).hashCode()

		override fun toString(): String = "Entry[category=$category, cardId=$cardId, id=$id, fields=$fields]"
	}

	/** The hits for a query, in index order, and their count per category. */
	@JvmRecord
	data class Result(val query: String, val hits: List<Entry>, val counts: Map<Category, Int>) {
		fun count(category: Category): Int = counts.getOrDefault(category, 0)

		/** The categories with at least one hit, in screen order. */
		fun categoriesWithHits(): List<Category> = Category.entries.filter { count(it) > 0 }

		override fun toString(): String = "Result[query=$query, hits=$hits, counts=$counts]"
	}

	/** An empty query matches everything. */
	fun search(raw: String): Result {
		val query = normalize(raw)
		val hits = ArrayList<Entry>()
		val counts = EnumMap<Category, Int>(Category::class.java)
		for (entry in entries) {
			if (query.isEmpty() || entry.fields.any { it.contains(query) }) {
				hits.add(entry)
				counts.merge(entry.category, 1, Int::plus)
			}
		}
		return Result(query, java.util.List.copyOf(hits), counts)
	}

	companion object {
		const val MAX_QUERY_LENGTH: Int = 35

		@JvmStatic
		fun of(cards: List<Card>): SearchIndex = SearchIndex(
			cards.flatMap { card ->
				val options = card.all().map { option ->
					val text = option.text()
					Entry(card.category, card.id, option.id(), listOf(text.title, text.description) + text.keywords + option.searchLabels())
				}
				// Rules by their label and name pattern (REQ-UI-09), each its own entry, so two with one label are both found.
				val rules = card.rules().map { rule -> Entry(card.category, card.id, rule.id, rule.searchFields.get()) }
				options + rules
			},
		)

		/** The box keeps at most 35 typed characters; then surrounding spaces go (Java's strip: Character.isWhitespace) and case is ignored. */
		@JvmStatic
		fun normalize(raw: String): String = raw.take(MAX_QUERY_LENGTH).trim(Character::isWhitespace).lowercase(Locale.ROOT)
	}
}
