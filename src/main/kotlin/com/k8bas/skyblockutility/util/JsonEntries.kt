package com.k8bas.skyblockutility.util

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.util.function.Predicate

/**
 * Reads a JSON array of entries one element at a time, so one malformed entry is skipped instead of
 * failing the whole list (REQ-NPCDB-05).
 */
object JsonEntries {
	private val GSON = Gson()

	/**
	 * @param entries the valid entries, in document order
	 * @param skipped elements that were null, not an object of the type, or not valid
	 */
	@JvmRecord
	data class Parsed<T>(val entries: List<T>, val skipped: Int) {
		override fun toString(): String = "Parsed[entries=$entries, skipped=$skipped]"
	}

	/** An empty or null document gives no entries; a document that is not an array throws
	 *  JsonSyntaxException. */
	@JvmStatic
	fun <T : Any> parse(json: String?, type: Class<T>, valid: Predicate<T>): Parsed<T> {
		val root = json?.let(JsonParser::parseString)
		if (root == null || root.isJsonNull) {
			return Parsed(java.util.List.of(), 0)
		}
		if (!root.isJsonArray) {
			throw JsonSyntaxException("expected a JSON array of entries")
		}
		val elements = root.asJsonArray.toList()
		val entries = elements.mapNotNull { read(it, type)?.takeIf(valid::test) }
		return Parsed(java.util.List.copyOf(entries), elements.size - entries.size)
	}

	/** The element as an entry, or null when it is not an object of the type. */
	private fun <T : Any> read(element: JsonElement, type: Class<T>): T? = try {
		if (element.isJsonObject) GSON.fromJson(element, type) else null
	} catch (e: JsonParseException) {
		null
	} catch (e: IllegalStateException) {
		null
	} catch (e: NumberFormatException) {
		null
	}

	/** Java's String.isBlank: Character.isWhitespace only (a no-break space is not blank). */
	@JvmStatic
	fun isBlank(value: String?): Boolean = value == null || value.all(Character::isWhitespace)
}
