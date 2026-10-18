package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.location.Islands
import com.k8bas.skyblockutility.skyblock.GameText
import java.util.Locale
import java.util.function.Function
import kotlin.math.roundToLong

/** The HUD's line types (REQ-BEST-08); [perFamily] lines repeat for each shown family, the others show once. */
enum class BestiaryLine(val label: String, val defaultShown: Boolean, val perFamily: Boolean) {
	FAMILY("Family and tier", true, true),
	KILLS("Kills", true, true),
	TO_NEXT("To next tier", true, true),
	TO_MAX("To max", false, true),
	MILESTONE("Milestone", true, false),
	RATE("Kills per hour", true, true),
	ETA("Time to next tier", false, true),
	SESSION_KILLS("Session kills", true, true),
	SESSION_AVERAGE("All families", false, false),
	SOURCE_AGE("Source and age", false, true),
}

/** The preset line orders the card offers (R59); the layout takes any order. */
enum class LineOrder(val label: String, val lines: List<BestiaryLine>) {
	PROGRESS_FIRST("Progress first", BestiaryLine.entries.toList()),
	SESSION_FIRST("Session first", listOf(
		BestiaryLine.FAMILY, BestiaryLine.SESSION_KILLS, BestiaryLine.RATE, BestiaryLine.ETA, BestiaryLine.SESSION_AVERAGE,
		BestiaryLine.KILLS, BestiaryLine.TO_NEXT, BestiaryLine.TO_MAX, BestiaryLine.MILESTONE, BestiaryLine.SOURCE_AGE,
	)),
	MILESTONE_FIRST("Milestone first", listOf(BestiaryLine.MILESTONE) + BestiaryLine.entries.filter { it != BestiaryLine.MILESTONE }),
}

/** How tiers are written: "15" or "XV" (REQ-BEST-08; Arabic by default, R59). */
enum class TierNumerals(val label: String) { ARABIC("Arabic (15)"), ROMAN("Roman (XV)") }

/** What the HUD draws: the lines shown, in [order], how tiers are written, and whether they are joined into one ([compact]). */
@JvmRecord
data class BestiaryLayout @JvmOverloads constructor(
	val shown: Set<BestiaryLine>,
	val order: List<BestiaryLine>,
	val numerals: TierNumerals,
	val compact: Boolean = false,
) {
	companion object {
		/** A fresh config: the default lines (§12.H), progress first, Arabic tiers (R59). */
		@JvmField
		val DEFAULT = BestiaryLayout(
			java.util.Set.copyOf(BestiaryLine.entries.filter { it.defaultShown }), LineOrder.PROGRESS_FIRST.lines, TierNumerals.ARABIC,
		)
	}
}

/** Number and time texts of the Bestiary HUD: written out here, as SbNumbers only reads them. */
object BestiaryFormat {
	private val ROMAN = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX",
		5 to "V", 4 to "IV", 1 to "I")

	/** [value] as a Roman numeral; 0 (a tier not reached) as "0". */
	@JvmStatic
	fun roman(value: Int): String {
		if (value <= 0) {
			return value.toString()
		}
		var left = value
		val out = StringBuilder()
		for ((step, numeral) in ROMAN) {
			while (left >= step) {
				out.append(numeral)
				left -= step
			}
		}
		return out.toString()
	}

	/** 12,449. */
	@JvmStatic
	fun grouped(value: Long): String = String.format(Locale.ROOT, "%,d", value)

	/** 12.4k, 1.5M, 950: how the widget abbreviates; a value that rounds up to the next unit takes it (1.0M, not 1000.0k). */
	@JvmStatic
	fun compact(value: Double): String = when {
		value >= 999_950_000 -> String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000)
		value >= 999_950 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000)
		value >= 1_000 -> String.format(Locale.ROOT, "%.1fk", value / 1_000)
		else -> grouped(value.roundToLong())
	}

	/** 45s, 2m33s, 1h02m. */
	@JvmStatic
	fun duration(ms: Long): String {
		val seconds = Math.round(ms / 1000.0)
		return when {
			seconds < 60 -> "${seconds}s"
			seconds < 3600 -> String.format(Locale.ROOT, "%dm%02ds", seconds / 60, seconds % 60)
			else -> {
				val minutes = Math.round(ms / 60_000.0)
				String.format(Locale.ROOT, "%dh%02dm", minutes / 60, minutes % 60)
			}
		}
	}

	/** How long ago: just now, 12 min ago, 3 h ago, 4 days ago. */
	@JvmStatic
	fun age(ms: Long): String {
		val minutes = ms / 60_000
		return when {
			minutes < 1 -> "just now"
			minutes < 60 -> "$minutes min ago"
			minutes < 48 * 60 -> "${minutes / 60} h ago"
			else -> "${minutes / (24 * 60)} days ago"
		}
	}
}

/**
 * The Bestiary HUD's lines (T5.3, REQ-BEST-08): each shown line type in the layout's order; one family's lines in that
 * order, several families' lines family by family and the shared lines (the milestone, the session average) after them.
 * The milestone shows only while known (REQ-BEST-05); "MAX" hides "to next" (EC-BEST-02); an abbreviated count and what
 * follows from it get "≈" (REQ-BEST-17); a rate or ETA not known yet is "—" (REQ-BEST-06). A pinned family not seen
 * yet shows its name and that it was not seen. Plain strings with colour codes, built on the tick, never per frame.
 */
object BestiaryHudLines {
	private const val UNKNOWN = "—"
	private const val APPROX = "≈"
	private val SPACES = Regex("\\s+")
	private const val SEPARATOR = " §8· "
	private val SEPARATOR_WIDTH = GameText.strip(SEPARATOR).length

	/** A compact line's visible characters at most: about 330 GUI px, so it fits a 1920×1080 screen at GUI scale 4. */
	private const val COMPACT_WIDTH = 60

	@JvmStatic
	fun lines(
		families: List<FamilyView>, rate: Function<String, RateView>, session: SessionView, milestone: MilestoneView?,
		layout: BestiaryLayout, now: Long,
	): List<String> {
		val order = layout.order.filter { it in layout.shown }
		if (families.isEmpty()) {
			return joined(listOf(order.filter { !it.perFamily }.mapNotNull { shared(it, session, milestone, layout, known = true) }), layout)
		}
		if (families.size == 1 && !unseen(families[0])) {
			val family = families[0]
			val familyRate = rate.apply(family.key)
			return joined(listOf(order.mapNotNull { line ->
				if (line.perFamily) perFamily(line, family, familyRate, layout, now) else shared(line, session, milestone, layout, known = false)
			}), layout)
		}
		// Family by family; the shared lines that come before the first family line in the order go above them, the rest below.
		val first = order.indexOfFirst { it.perFamily }.let { if (it < 0) order.size else it }
		val groups = ArrayList<List<String>>()
		groups += order.take(first).filter { !it.perFamily }.mapNotNull { shared(it, session, milestone, layout, known = false) }
		for (family in families) {
			if (unseen(family)) {
				groups += unseenLines(family, layout)
				continue
			}
			val familyRate = rate.apply(family.key)
			groups += order.filter { it.perFamily }.mapNotNull { perFamily(it, family, familyRate, layout, now) }
		}
		groups += order.drop(first).filter { !it.perFamily }.mapNotNull { shared(it, session, milestone, layout, known = false) }
		return joined(groups, layout)
	}

	/**
	 * The groups' lines one under another, or with [BestiaryLayout.compact] each group joined at the separators (T5.3b),
	 * into lines of at most [COMPACT_WIDTH] visible characters: the renderer moves an element back on screen but never
	 * shrinks it, so one long line would run off a small screen.
	 */
	private fun joined(groups: List<List<String>>, layout: BestiaryLayout): List<String> {
		if (!layout.compact) {
			return groups.flatten()
		}
		val out = ArrayList<String>()
		for (group in groups) {
			val line = StringBuilder()
			var visible = 0
			for (item in group) {
				val width = GameText.strip(item).length
				if (line.isNotEmpty() && visible + SEPARATOR_WIDTH + width > COMPACT_WIDTH) {
					out += line.toString()
					line.clear()
					visible = 0
				}
				if (line.isNotEmpty()) {
					line.append(SEPARATOR)
					visible += SEPARATOR_WIDTH
				}
				line.append(item)
				visible += width
			}
			if (line.isNotEmpty()) {
				out += line.toString()
			}
		}
		return out
	}

	/** [text] in lines of at most [max] characters, broken between words (a longer word keeps a line of its own). */
	@JvmStatic
	fun wrap(text: String, max: Int): List<String> {
		val out = ArrayList<String>()
		val line = StringBuilder()
		for (word in text.trim().split(SPACES)) {
			if (line.isNotEmpty() && line.length + 1 + word.length > max) {
				out += line.toString()
				line.clear()
			}
			if (line.isNotEmpty()) {
				line.append(' ')
			}
			line.append(word)
		}
		if (line.isNotEmpty()) {
			out += line.toString()
		}
		return out
	}

	private fun unseen(family: FamilyView): Boolean = family.updatedAt == 0L && family.kills == null

	private fun unseenLines(family: FamilyView, layout: BestiaryLayout): List<String> =
		listOfNotNull(perFamily(BestiaryLine.FAMILY, family, null, layout, 0), "§8not seen yet")

	private fun perFamily(line: BestiaryLine, family: FamilyView, rate: RateView?, layout: BestiaryLayout, now: Long): String? {
		val approx = if (family.kills?.approximate == true) APPROX else ""
		return when (line) {
			BestiaryLine.FAMILY -> {
				val tier = family.tier ?: return "§6${family.name}"
				when {
					family.max -> "§6${family.name} §e${numeral(tier, layout)} §aMAX"
					// The widget may show tiers past the table's last one (EC-BEST-09): then no "9/8".
					family.family != null && tier <= family.family.maxTier() ->
						"§6${family.name} §e${numeral(tier, layout)}§7/${numeral(family.family.maxTier(), layout)}"
					else -> "§6${family.name} §e${numeral(tier, layout)}"
				}
			}
			BestiaryLine.KILLS -> family.kills?.let { kills ->
				"§7Kills: §f" + if (kills.approximate) APPROX + BestiaryFormat.compact(kills.value) else BestiaryFormat.grouped(kills.value.roundToLong())
			}
			BestiaryLine.TO_NEXT -> {
				val left = family.toNext ?: return null
				val tier = family.tier ?: return null
				if (family.max) null else "§7To tier ${numeral(tier + 1, layout)}: §f$approx${BestiaryFormat.grouped(left)}"
			}
			BestiaryLine.TO_MAX -> when {
				family.max -> "§7To max: §aMAX"
				else -> family.toMax?.let { "§7To max: §f$approx${BestiaryFormat.grouped(it)}" }
			}
			BestiaryLine.RATE -> "§7Rate: §f" + (rate?.perHour?.let { BestiaryFormat.grouped(it.roundToLong()) + "/h" } ?: UNKNOWN)
			BestiaryLine.ETA -> {
				val next = rate?.etaNextMs ?: return "§7ETA: §f$UNKNOWN"
				val max = rate.etaMaxMs?.let { " §8(max $approx${BestiaryFormat.duration(it)})" } ?: ""
				"§7ETA: §f$approx${BestiaryFormat.duration(next)}$max"
			}
			BestiaryLine.SESSION_KILLS -> "§7Session: §f+${BestiaryFormat.grouped(family.sessionKills)}"
			BestiaryLine.SOURCE_AGE -> "§8${family.source.name.lowercase(Locale.ROOT)}, ${BestiaryFormat.age(now - family.updatedAt)}"
			BestiaryLine.MILESTONE, BestiaryLine.SESSION_AVERAGE -> null
		}
	}

	/** A shared line; with [known], only one whose value is known (no family shown: nothing to say "—" about). */
	private fun shared(line: BestiaryLine, session: SessionView, milestone: MilestoneView?, layout: BestiaryLayout, known: Boolean): String? = when (line) {
		BestiaryLine.MILESTONE -> milestone?.let { reached ->
			val toNext = reached.tiersToNext?.let { tiers -> " §8($tiers ${if (tiers == 1) "tier" else "tiers"} to next)" } ?: ""
			"§7Milestone: §f${numeral(reached.milestone, layout)}$toNext"
		}
		// The session over all families (REQ-BEST-07): its kills, and once a minute is known the average and the time.
		BestiaryLine.SESSION_AVERAGE -> if (known && session.kills == 0L) null else "§7All families: §f+${BestiaryFormat.grouped(session.kills)}" +
			(session.averagePerHour?.let { ", ${BestiaryFormat.grouped(it.roundToLong())}/h §8(${BestiaryFormat.duration(session.durationMs)})" } ?: "")
		else -> null
	}

	private fun numeral(value: Int, layout: BestiaryLayout): String =
		if (layout.numerals == TierNumerals.ROMAN) BestiaryFormat.roman(value) else value.toString()
}

/**
 * When the HUD shows the widget hint instead of live lines (REQ-BEST-12, T5.3): after 10 s without a `Bestiary:` section,
 * on any island but the Dungeon Hub (R24), never while the island is unknown. A new world or the tracker switched on
 * starts a fresh 10 s. Client thread only.
 */
class BestiaryWidgetWatch {
	private var lastSeen: Long? = null

	/** A tab list with a `Bestiary:` section at [now]. */
	fun seen(now: Long) {
		lastSeen = now
	}

	/** A new world, or the tracker switched on, at [now]. */
	fun restart(now: Long) {
		lastSeen = now
	}

	fun hint(island: String?, now: Long): Boolean {
		val since = lastSeen ?: return false
		return island != null && island != Islands.DUNGEON_HUB && now - since >= HINT_AFTER_MS
	}

	private companion object {
		const val HINT_AFTER_MS = 10_000L
	}
}
