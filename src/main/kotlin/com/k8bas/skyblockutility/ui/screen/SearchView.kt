package com.k8bas.skyblockutility.ui.screen

import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.RuleGroup
import com.k8bas.skyblockutility.ui.option.SearchIndex

/**
 * What the settings screen shows for a query (REQ-UI-08), worked out from the search index: the
 * categories with a hit (all of them for an empty query), each one's badge (its hit count), the
 * category to show (the current one while it has a hit, else the first that has one), and which
 * options show. With no hit anywhere the sidebar is empty and the content says so.
 */
@JvmRecord
internal data class SearchView(
	val result: SearchIndex.Result,
	val typed: String,
	val categories: List<Category>,
	val selected: Category,
	val hits: Set<String>,
) {
	fun filtering(): Boolean = result.query.isNotEmpty()

	fun nothingFound(): Boolean = filtering() && categories.isEmpty()

	fun badge(category: Category): Int = result.count(category)

	fun shows(option: Option): Boolean = !filtering() || hits.contains(option.id())

	/** A rule shows when its label or pattern matches (REQ-UI-09), or when there is no query. */
	fun shows(rule: RuleGroup): Boolean = !filtering() || hits.contains(rule.id)

	/** The content's text when nothing matches; the query as typed, trimmed. */
	fun message(): String = "No settings found for \"$typed\""

	override fun toString(): String = "SearchView[result=$result, typed=$typed, categories=$categories, selected=$selected, hits=$hits]"

	companion object {
		@JvmStatic
		fun of(index: SearchIndex, all: List<Category>, raw: String, current: Category): SearchView {
			val result = index.search(raw)
			val categories = if (result.query.isNotEmpty()) result.categoriesWithHits().filter(all::contains) else java.util.List.copyOf(all)
			val selected = if (categories.isEmpty() || categories.contains(current)) current else categories[0]
			val hits = java.util.Set.copyOf(result.hits.map { it.id })
			// For the message: what was typed (its first 35 characters), trimmed (Java's strip) but in its own case.
			val typed = raw.take(SearchIndex.MAX_QUERY_LENGTH).trim(Character::isWhitespace)
			return SearchView(result, typed, categories, selected, hits)
		}
	}
}
