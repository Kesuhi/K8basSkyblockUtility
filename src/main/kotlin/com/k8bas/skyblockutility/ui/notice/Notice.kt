package com.k8bas.skyblockutility.ui.notice

/**
 * A notice (toast, REQ-UI-21): a title and up to 2 body lines. It only informs: it may name a screen
 * to open, but takes no input and never starts an action itself.
 *
 * A class rather than a data class: the body is copied on construction. Java sees `title()` and `body()`.
 */
class Notice(@get:JvmName("title") val title: String, body: List<String>) {
	@get:JvmName("body")
	val body: List<String>

	init {
		// Java's String.isBlank: Character.isWhitespace only.
		require(!title.all(Character::isWhitespace)) { "a notice needs a title" }
		this.body = java.util.List.copyOf(body)
		require(this.body.size <= MAX_BODY_LINES) { "a notice has at most $MAX_BODY_LINES body lines: ${this.body}" }
	}

	override fun equals(other: Any?): Boolean = other is Notice && title == other.title && body == other.body

	override fun hashCode(): Int = title.hashCode() * 31 + body.hashCode()

	override fun toString(): String = "Notice[title=$title, body=$body]"

	companion object {
		const val MAX_BODY_LINES: Int = 2
	}
}
