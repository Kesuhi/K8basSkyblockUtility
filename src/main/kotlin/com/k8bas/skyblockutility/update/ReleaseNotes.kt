package com.k8bas.skyblockutility.update

/**
 * A release's notes as the confirmation screen shows them (REQ-UPD-11, EC-UPD-10): plain text, with Minecraft's `§`
 * formatting codes and Markdown syntax removed, links reduced to their text, and capped at 4,000 characters. Nothing in
 * them is clickable; only the checked release-page link is, on its own button.
 */
object ReleaseNotes {
	const val MAX_CHARS: Int = 4_000
	private const val CUT = "…"

	private val FORMATTING = Regex("§.?")
	private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
	private val LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
	private val AUTOLINK = Regex("<(https?://[^>\\s]+)>")
	private val HTML_TAG = Regex("</?[A-Za-z][^>]*>")
	private val HEADING = Regex("^\\s{0,3}#{1,6}\\s*")
	private val QUOTE = Regex("^\\s{0,3}>\\s?")
	private val BULLET = Regex("^(\\s*)[*+-]\\s+")
	private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
	private val FENCE = Regex("^\\s{0,3}(```|~~~).*$")
	private val STRONG = Regex("(\\*\\*|__)(.+?)\\1")
	private val EMPHASIS = Regex("(?<![\\w*])([*_])(?!\\s)(.+?)(?<!\\s)\\1(?![\\w*])")
	private val CODE = Regex("`+([^`]*)`+")
	private val STRIKE = Regex("~~(.+?)~~")
	private val TRAILING_BREAK = Regex("(\\s{2,}|\\\\)$")

	/** [markdown] as plain text, at most [MAX_CHARS] characters (an ellipsis marks a cut). */
	@JvmStatic
	fun plain(markdown: String?): String {
		if (markdown.isNullOrBlank()) {
			return ""
		}
		val out = ArrayList<String>()
		for (raw in FORMATTING.replace(markdown.replace("\r\n", "\n").replace('\r', '\n'), "").split('\n')) {
			if (FENCE.matches(raw) || RULE.matches(raw)) {
				continue
			}
			var line = raw
			line = HEADING.replace(line, "")
			line = QUOTE.replace(line, "")
			line = BULLET.replace(line) { it.groupValues[1] + "• " }
			line = IMAGE.replace(line) { it.groupValues[1] }
			line = LINK.replace(line) { it.groupValues[1] }
			line = AUTOLINK.replace(line) { it.groupValues[1] }
			line = HTML_TAG.replace(line, "")
			line = CODE.replace(line) { it.groupValues[1] }
			line = STRONG.replace(line) { it.groupValues[2] }
			line = STRIKE.replace(line) { it.groupValues[1] }
			line = EMPHASIS.replace(line) { it.groupValues[2] }
			line = TRAILING_BREAK.replace(line, "")
			out.add(line.trimEnd())
		}
		// At most one blank line in a row, none at either end.
		val text = out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim('\n')
		if (text.length <= MAX_CHARS) {
			return text
		}
		var end = MAX_CHARS - CUT.length
		// Never half of a character that takes two chars.
		if (Character.isHighSurrogate(text[end - 1])) {
			end--
		}
		return text.substring(0, end).trimEnd() + CUT
	}
}
