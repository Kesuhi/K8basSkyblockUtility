package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.skyblock.SbNumber
import com.k8bas.skyblockutility.skyblock.SbNumbers
import com.k8bas.skyblockutility.skyblock.ServerMessage
import com.k8bas.skyblockutility.skyblock.TabWidget

/**
 * One family line of the `Bestiary:` tab widget: ` Glacite Walker 9: 2,346/3,000`, or ` Ghost 25: MAX`. [kills] is
 * the cumulative count and [next] the next tier's cumulative threshold; both are null on a MAX line, and either is
 * [SbNumber.approximate] when the widget abbreviates it (`12.4k`, REQ-BEST-17).
 */
@JvmRecord
data class WidgetLine(val name: String, val tier: Int, val kills: SbNumber?, val next: SbNumber?, val max: Boolean)

/**
 * The `Bestiary:` tab widget (REQ-BEST-10, source 1): the live source, since the server updates it as kills are credited.
 * The tier is the word just before the colon, because family names may end in a numeral ("Barbarian Duke X"). A MAX
 * line shows no counts, so both stay unknown rather than zero. The G1 captures confirm the Arabic, exact form; MAX, a
 * Roman tier and abbreviated counts are read as the research describes them and stay UNVERIFIED (REQ-GS-14).
 */
object BestiaryWidget {
	/** The widget's header in the tab list. */
	const val HEADER = "Bestiary"

	/** `<family> <tier>: <kills>/<next>` or `<family> <tier>: MAX`; the tier is the word before the colon. */
	private val LINE = Regex("^(.+) (\\S+): (?:(MAX)|(\\S+)/(\\S+))$")

	/** The family line [line] (an entry without its leading space), or null if it is not one. */
	@JvmStatic
	fun parseLine(line: String): WidgetLine? {
		val match = LINE.matchEntire(line.trim()) ?: return null
		val name = match.groupValues[1].trim()
		val tier = SbNumbers.parseLevel(match.groupValues[2]) ?: return null
		if (match.groups[3] != null) {
			return WidgetLine(name, tier, null, null, true)
		}
		val kills = SbNumbers.parse(match.groupValues[4]) ?: return null
		val next = SbNumbers.parse(match.groupValues[5]) ?: return null
		return WidgetLine(name, tier, kills, next, false)
	}

	/** Every family line of [widget], in order; other lines are left out. */
	@JvmStatic
	fun parse(widget: TabWidget): List<WidgetLine> = widget.lines.mapNotNull { parseLine(it) }
}

/** What a BESTIARY chat block says (REQ-BEST-05, REQ-BEST-10 source 3). */
sealed class BestiaryChatEvent {
	/** A family went from tier [from] to [to]; one that reaches tier I goes from 0 to 1. */
	data class TierUp(val name: String, val from: Int, val to: Int) : BestiaryChatEvent()

	/**
	 * A Bestiary Milestone message: [to] is the milestone. Inside a tier-up block it was just reached ([claimed] false,
	 * [from] the one before); in a block of its own it was claimed ([claimed] true, [from] the last one claimed, so a claim
	 * of several pending milestones jumps). A reached milestone is reported again when it is claimed.
	 */
	data class Milestone(val from: Int, val to: Int, val claimed: Boolean) : BestiaryChatEvent()

	/** The line after a BESTIARY or BESTIARY MILESTONE header could not be read; [line] is server text, so log its shape. */
	data class Unreadable(val header: String, val line: String) : BestiaryChatEvent()
}

/**
 * Reads BESTIARY tier-up and milestone blocks from the server's chat lines, one line at a time (the server sends each
 * line of a block as its own message). Only the line after a `BESTIARY` or `BESTIARY MILESTONE` header is read, one blank
 * line between them allowed, so the rewards and lines outside a block are never taken for tiers. The reader passes only
 * server lines (REQ-GS-05), also those another mod hides (EC-BEST-13); the action bar is ignored.
 * Shapes, from the maintainer's logs (fixtures/bestiary): `Flare X ➡ XI`, `Littlefoot I` for a family that reached
 * tier I (the earlier line `BESTIARY FAMILY UNLOCKED <family>` is not read), `Barbarian Duke X VIII ➡ IX` (a name
 * ending in a numeral), and the milestone `CCLXI➡CCLXII` or `CCLXI ➡ CCLXII`. A milestone inside a tier-up block was
 * just reached; one in a block of its own is a claim, told apart by the border lines between blocks. Numerals are Roman
 * or Arabic (REQ-BEST-17, through [SbNumbers.parseLevel]). Client thread only; [reset] on a world change.
 */
class BestiaryChatParser {
	private var header: String? = null
	private var blanks = 0

	/** A tier-up was read since the last border line: a milestone now is one just reached, not a claim. */
	private var inTierBlock = false

	/** Forgets a half-read block; called on a world change or disconnect, so a header left open takes no later line. */
	fun reset() {
		header = null
		blanks = 0
		inTierBlock = false
	}

	/** The event [message] completes, or null. */
	fun accept(message: ServerMessage): BestiaryChatEvent? {
		if (message.overlay) {
			return null
		}
		val line = message.text.trim()
		if (header == null && BORDER.matches(line)) {
			inTierBlock = false
			return null
		}
		if (line == TIER_HEADER || line == MILESTONE_HEADER) {
			header = line
			blanks = 0
			return null
		}
		val expected = header ?: return null
		if (line.isEmpty() && ++blanks <= 1) {
			return null
		}
		header = null
		val event = if (expected == TIER_HEADER) tierUp(line) else milestone(line, !inTierBlock)
		if (event is BestiaryChatEvent.TierUp) {
			inTierBlock = true
		}
		return event ?: BestiaryChatEvent.Unreadable(expected, line)
	}

	private companion object {
		const val TIER_HEADER = "BESTIARY"
		const val MILESTONE_HEADER = "BESTIARY MILESTONE"

		/** A block's border: a run of `▬` (64 in the logs). */
		val BORDER = Regex("^▬{20,}$")

		/** `<family> <from> ➡ <to>`; the spaces around the arrow are optional. */
		val TIER_UP = Regex("^(.+?) +(\\S+?) *➡ *(\\S+)$")

		/** `<family> I`: a family that reached tier I. */
		val FIRST_TIER = Regex("^(.+) +(\\S+)$")
		val MILESTONE = Regex("^(\\S+?) *➡ *(\\S+)$")

		fun tierUp(line: String): BestiaryChatEvent? {
			TIER_UP.matchEntire(line)?.let { match ->
				val from = SbNumbers.parseLevel(match.groupValues[2]) ?: return null
				val to = SbNumbers.parseLevel(match.groupValues[3]) ?: return null
				return BestiaryChatEvent.TierUp(match.groupValues[1].trim(), from, to)
			}
			val first = FIRST_TIER.matchEntire(line) ?: return null
			return if (SbNumbers.parseLevel(first.groupValues[2]) == 1) BestiaryChatEvent.TierUp(first.groupValues[1].trim(), 0, 1) else null
		}

		fun milestone(line: String, claimed: Boolean): BestiaryChatEvent? {
			val match = MILESTONE.matchEntire(line) ?: return null
			val from = SbNumbers.parseLevel(match.groupValues[1]) ?: return null
			val to = SbNumbers.parseLevel(match.groupValues[2]) ?: return null
			return BestiaryChatEvent.Milestone(from, to, claimed)
		}
	}
}
