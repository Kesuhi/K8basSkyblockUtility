package com.k8bas.skyblockutility.skyblock

/** One sidebar line as the server sends it: the holder's team prefix, the entry name and the team suffix. */
@JvmRecord
data class SidebarLine(val prefix: String, val entry: String, val suffix: String)

/** The sidebar objective's title and its shown lines, top to bottom. */
@JvmRecord
data class SidebarRaw(val title: String, val lines: List<SidebarLine>)

/** The sidebar as plain text (REQ-GS-03), immutable: the title and each line, top to bottom. */
data class SidebarSnapshot(val title: String, val lines: List<String>) {
	companion object {
		/** No sidebar is shown. */
		@JvmField
		val EMPTY = SidebarSnapshot("", listOf())
	}
}

/**
 * Composes each sidebar line from its team prefix, entry and suffix, then removes formatting and Hypixel's
 * filler codes (REQ-GS-03). Hypixel splits a line across the three parts and puts a filler code where they
 * meet, often mid-word (`§bGlacite M` + `§q` + `§bineshafts`), so the parts are joined before stripping.
 * Lines are trimmed; a filler-only line reads as empty.
 */
object SidebarParser {
	@JvmStatic
	fun parse(raw: SidebarRaw): SidebarSnapshot {
		val title = GameText.strip(raw.title).trim()
		val lines = raw.lines.map { GameText.strip(it.prefix + it.entry + it.suffix).trim() }
		if (title.isEmpty() && lines.isEmpty()) {
			return SidebarSnapshot.EMPTY
		}
		return SidebarSnapshot(title, java.util.List.copyOf(lines))
	}
}
