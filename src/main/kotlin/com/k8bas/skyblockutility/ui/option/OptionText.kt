package com.k8bas.skyblockutility.ui.option

/**
 * What an option says, declared once for its card and the search index (REQ-UI-07).
 *
 * A class rather than a data class: the keywords are copied on construction. Java sees the
 * record-style accessors `title()`, `description()`, `tooltip()`, `keywords()`.
 *
 * @param title       the card or row title; searched
 * @param description at most two card lines; searched
 * @param tooltip     the full text on hover, "\n" for a line break, empty for none; not searched
 * @param keywords    hidden search words (e.g. "gambling" for Rare Drop Odds, REQ-XC-RULES-07)
 */
class OptionText(
	@get:JvmName("title") val title: String,
	@get:JvmName("description") val description: String,
	@get:JvmName("tooltip") val tooltip: String,
	keywords: List<String>,
) {
	@get:JvmName("keywords")
	val keywords: List<String> = java.util.List.copyOf(keywords)

	init {
		// Java's String.isBlank: Character.isWhitespace only (a title of no-break spaces is not blank).
		require(!title.all(Character::isWhitespace)) { "an option needs a title" }
	}

	override fun equals(other: Any?): Boolean = other is OptionText && title == other.title && description == other.description &&
		tooltip == other.tooltip && keywords == other.keywords

	override fun hashCode(): Int = listOf(title, description, tooltip, keywords).hashCode()

	override fun toString(): String = "OptionText[title=$title, description=$description, tooltip=$tooltip, keywords=$keywords]"
}
