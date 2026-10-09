package com.k8bas.skyblockutility.skyblock

/** One listed tab entry as the client has it: the profile name sorts it, the text is what is shown. */
@JvmRecord
data class TabRow(val name: String, val order: Int, val text: String)

/**
 * Whether a widget is in the tab list (REQ-GS-02): not there, there without data, there with data, or there
 * but cut off where the tab list or its column ends, so lines may be missing (EC-GS-01): a consumer that
 * needs the whole widget treats PARTIAL as unknown.
 */
enum class WidgetPresence { ABSENT, EMPTY, PRESENT, PARTIAL }

/**
 * One tab widget: its header (`Frozen Corpses`), the value on the header line if any (`Profile: Raspberry`
 * gives `Raspberry`) and its entry lines in order, without the leading space (`Lapis: NOT LOOTED`).
 * [cutOff] is true when the widget ran into the end of the tab list or of its column with no blank row
 * after it, so more lines may follow out of sight.
 */
data class TabWidget(val name: String, val inline: String, val lines: List<String>, val cutOff: Boolean) {
	/** The value of the first `<key>: <value>` line, e.g. `value("SB Level")`; null if there is none. */
	fun value(key: String): String? {
		val prefix = "$key:"
		return lines.firstOrNull { it.startsWith(prefix) }?.substring(prefix.length)?.trim()
	}
}

/**
 * The tab widgets at one moment (REQ-GS-02), immutable. [unknownLines] holds the lines that are neither a
 * header nor an entry, so a consumer can tell "not recognised" from "absent".
 */
data class TabSnapshot(val widgets: List<TabWidget>, val unknownLines: List<String>) {
	/** The first widget with this header, or null if it is absent. */
	fun widget(name: String): TabWidget? = widgets.firstOrNull { it.name == name }

	fun presence(name: String): WidgetPresence {
		val widget = widget(name) ?: return WidgetPresence.ABSENT
		return when {
			widget.cutOff -> WidgetPresence.PARTIAL
			widget.inline.isEmpty() && widget.lines.isEmpty() -> WidgetPresence.EMPTY
			else -> WidgetPresence.PRESENT
		}
	}

	companion object {
		/** No tab list, or one without widgets (e.g. off Hypixel). */
		@JvmField
		val EMPTY = TabSnapshot(listOf(), listOf())
	}
}
