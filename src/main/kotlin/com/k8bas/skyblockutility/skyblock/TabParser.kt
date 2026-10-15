package com.k8bas.skyblockutility.skyblock

/**
 * Groups tab rows into widgets (REQ-GS-02), independent of the order the client keeps them in.
 *
 * The client's list is a hash set, so its order means nothing. The rows are sorted as vanilla draws them,
 * by list order and then profile name; Hypixel names its widget rows `!A-a` to `!D-t`, which gives the four
 * columns top to bottom (see the G1 capture README). Only the 80 rows vanilla shows are read (EC-GS-01).
 * Row shapes, from the G1 captures:
 * - a column title is indented by two or more spaces (`Info`, `Players (23)`); a title ending in a count
 *   starts a column of players, whose rows are skipped and never kept
 * - a header has no indent and ends its name with a colon, optionally followed by a value (`Area: Mineshaft`)
 * - an entry is indented by one space and belongs to the header above it
 * - an empty row ends a widget; a widget still open at the next column title or at the end of the rows read
 *   ran out of room, so it is marked cut off (EC-GS-01)
 * Anything else is an unknown line: kept apart and flagged, never guessed (REQ-GS-10).
 */
object TabParser {
	/** The rows vanilla's tab overlay draws at most. */
	const val MAX_ROWS = 80

	private val ORDER = compareByDescending<TabRow> { it.order }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
	private val HEADER = Regex("^([^:]+):(?:\\s+(.*))?$")
	private val MEMBER_COLUMN = Regex("^.*\\(\\d+\\)$")

	@JvmStatic
	fun parse(rows: List<TabRow>): TabSnapshot {
		val widgets = ArrayList<TabWidget>()
		val unknown = ArrayList<String>()
		var inMemberColumn = false
		var header: String? = null
		var inline = ""
		val lines = ArrayList<String>()

		fun close(cutOff: Boolean = false) {
			header?.let { widgets.add(TabWidget(it, inline, java.util.List.copyOf(lines), cutOff)) }
			header = null
			inline = ""
			lines.clear()
		}

		for (row in rows.sortedWith(ORDER).take(MAX_ROWS)) {
			val text = GameText.strip(row.text)
			if (text.isBlank()) {
				close()
				continue
			}
			val indent = text.length - text.trimStart(' ').length
			when {
				indent >= 2 -> {
					close(cutOff = true)
					inMemberColumn = MEMBER_COLUMN.matches(text.trim())
				}
				inMemberColumn -> Unit
				indent == 1 -> if (header != null) lines.add(text.substring(1)) else unknown.add(text)
				else -> {
					val match = HEADER.matchEntire(text)
					if (match != null) {
						close()
						header = match.groupValues[1].trim()
						inline = match.groupValues[2].trim()
					} else {
						unknown.add(text)
					}
				}
			}
		}
		close(cutOff = true)
		if (widgets.isEmpty() && unknown.isEmpty()) {
			return TabSnapshot.EMPTY
		}
		return TabSnapshot(java.util.List.copyOf(widgets), java.util.List.copyOf(unknown))
	}
}
