package com.k8bas.skyblockutility.ui.screen

import com.k8bas.skyblockutility.ui.option.SearchIndex
import com.k8bas.skyblockutility.ui.render.Ellipsis
import java.util.Locale
import java.util.function.ToIntFunction

/**
 * Where a search query shows in the text a card draws (REQ-UI-51, T3.44), so the card can put a tinted box behind it. A
 * match is found as [SearchIndex] finds it (the query normalised, the text lower-cased with [Locale.ROOT]) and given in
 * the text's own characters, also where lower-casing changes a letter's length (EC-UI-29). Pure, so it is tested
 * without a game; the cards draw the boxes.
 */
object SearchMarks {
	/** A formatting code: the section sign and the character after it. */
	private const val SECTION = '§'

	/**
	 * The ranges `[start, end)` of every occurrence of [query] (normalised, [SearchIndex.normalize]) in [text], in order,
	 * overlapping or touching ones merged into one; none for an empty query.
	 */
	@JvmStatic
	fun ranges(text: String, query: String): List<IntArray> {
		if (query.isEmpty()) {
			return emptyList()
		}
		// The text lower-cased one character at a time, and for each lower-case character the original it came from.
		val lower = StringBuilder()
		val origin = ArrayList<Int>()
		var i = 0
		while (i < text.length) {
			val next = i + Character.charCount(text.codePointAt(i))
			val lowered = text.substring(i, next).lowercase(Locale.ROOT)
			lower.append(lowered)
			repeat(lowered.length) { origin.add(i) }
			i = next
		}
		// The search lower-cases the whole text, which differs only where a letter depends on its neighbours (a word-final
		// sigma); with the same length it lines up character for character, so its letters are the ones matched.
		val whole = text.lowercase(Locale.ROOT)
		val searched = if (whole.length == lower.length) whole else lower.toString()
		val found = ArrayList<IntArray>()
		var from = 0
		while (true) {
			val at = searched.indexOf(query, from)
			if (at < 0) {
				break
			}
			val start = origin[at]
			val last = origin[at + query.length - 1]
			val end = last + Character.charCount(text.codePointAt(last))
			val previous = found.lastOrNull()
			if (previous != null && start <= previous[1]) {
				previous[1] = maxOf(previous[1], end)
			} else {
				found.add(intArrayOf(start, end))
			}
			from = at + 1
		}
		return found
	}

	/**
	 * The ranges within each drawn line of [full]: a description wrapped into lines, or one title, the last line ending in
	 * [Ellipsis.DOTS] if [cut]. Each line must stand in [full] where the one before ended (after the spaces or line break the
	 * wrap took out, and after any formatting code it carried over), so a match a wrap splits is marked on both lines, one the
	 * ellipsis cuts only where it shows, and the dots never. From a line that does not stand there on (the wrap changed the
	 * text, as when it closed up a double space), no line is marked, as no match of the search could be placed.
	 */
	@JvmStatic
	fun inLines(full: String, lines: List<String>, query: String, cut: Boolean): List<List<IntArray>> {
		val marks = ranges(full, query)
		var cursor = 0
		var lost = false
		return lines.mapIndexed { index, line ->
			val drawn = if (cut && index == lines.size - 1) line.removeSuffix(Ellipsis.DOTS) else line
			var at = cursor
			while (at < full.length && Character.isWhitespace(full[at])) {
				at++
			}
			// The line as it is, or without the formatting codes the wrap carried to its start.
			val codes = listOf(0, leadingCodes(drawn)).distinct().firstOrNull { skip ->
				full.regionMatches(at, drawn, skip, drawn.length - skip)
			}
			if (lost || codes == null) {
				lost = true
				return@mapIndexed emptyList()
			}
			val visible = drawn.length - codes
			cursor = at + visible
			marks.mapNotNull { range ->
				val start = maxOf(range[0], at)
				val end = minOf(range[1], cursor)
				if (start < end) intArrayOf(start - at + codes, end - at + codes) else null
			}
		}
	}

	/** How many characters of formatting codes the line starts with (carried over a wrap, not in the text there). */
	private fun leadingCodes(line: String): Int {
		var i = 0
		while (i + 1 < line.length && line[i] == SECTION) {
			i += 2
		}
		return i
	}

	/**
	 * The boxes `{x, y, w, h}` in GUI px behind the [ranges] of [line] drawn at ([x], [y]): from the advance of the text
	 * before each range to the advance up to its end, from one row above the glyphs, [lineHeight] high.
	 */
	@JvmStatic
	fun boxes(line: String, ranges: List<IntArray>, x: Int, y: Int, lineHeight: Int, width: ToIntFunction<String>): List<IntArray> =
		ranges.map { range ->
			val left = width.applyAsInt(line.substring(0, range[0]))
			val right = width.applyAsInt(line.substring(0, range[1]))
			intArrayOf(x + left, y - 1, right - left, lineHeight)
		}
}
