package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.skyblock.GameText
import com.k8bas.skyblockutility.skyblock.SbNumber
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.roundToLong

/** Where a shown value came from (REQ-BEST-10). */
enum class BestiarySource { WIDGET, MENU, CHAT, STORED }

/**
 * One family as the HUD shows it: the newest server values, what follows from them, and the live kills of the session.
 * [family] is null for a name the table does not know (REQ-BEST-16): then only the server's values show. [toNext] and
 * [toMax] are approximate when [kills] is (REQ-BEST-17); [toNext] is null once maxed. [source] and [updatedAt] are those
 * of the family's newest value; the kills keep their own ([killsSource], [killsUpdatedAt]), null while no count is known.
 */
@JvmRecord
data class FamilyView(
	val key: String,
	val name: String,
	val family: BestiaryFamily?,
	val kills: SbNumber?,
	val tier: Int?,
	val nextThreshold: Int?,
	val max: Boolean,
	val toNext: Long?,
	val toMax: Long?,
	val source: BestiarySource,
	val updatedAt: Long,
	val sessionKills: Long,
	val killsSource: BestiarySource?,
	val killsUpdatedAt: Long?,
)

/**
 * A family's kills per hour over the rolling window (REQ-BEST-06): null ("—") with under 60 s of data or while only an
 * abbreviated count is known; the ETAs are null while the rate is null or 0, or nothing is left.
 */
@JvmRecord
data class RateView(val perHour: Double?, val etaNextMs: Long?, val etaMaxMs: Long?)

/** The session (REQ-BEST-07): its live kills, the time spent on SkyBlock, and the average per hour (null under 60 s). */
@JvmRecord
data class SessionView(val kills: Long, val durationMs: Long, val averagePerHour: Double?)

/** The global Bestiary Milestone, with the family tiers still needed for the next one when known (REQ-BEST-05). */
@JvmRecord
data class MilestoneView(val milestone: Int, val tiersToNext: Int?, val source: BestiarySource, val updatedAt: Long)

/**
 * The live bestiary state of one profile and session (T5.2). It merges the three sources per family (REQ-BEST-10): each
 * value replaces the older one and keeps its source and time, so a menu snapshot read now wins over the widget's last
 * value and the widget's next change wins again (EC-BEST-16). The tab list is re-read whenever anything in it changes, so
 * a widget line that did not change is no new value. A chat tier-up sets the tier only.
 *
 * Live kills come only from the widget (REQ-BEST-11): a family's first exact count is its baseline, a later rise of 1 to
 * 50 counts, a bigger rise or a fall moves the baseline without counting (EC-BEST-04, EC-BEST-05), and an abbreviated
 * count neither counts nor moves it (REQ-BEST-17); a family listed twice in one update counts by its first line. The
 * widget's next-tier threshold wins over the table's, also past the table's last tier, and a difference is logged once
 * per family per session (REQ-BEST-03, EC-BEST-09). A shared name the island cannot place keeps the family it was seen as
 * before. The milestone comes only from the menu's milestone item and from chat: one just reached leaves 10 tiers to the
 * next, and each tier-up after it counts one down (REQ-BEST-05).
 *
 * The rate is the kills counted in the last W minutes over min(W, the time since the family's first exact count), "—" under a
 * minute of that (REQ-BEST-06); the session's time grows only on ticks while on SkyBlock, and the session ends on a manual
 * reset, another profile, or with the option another island (REQ-BEST-07).
 *
 * Pure: the module feeds it on the client thread and passes the time (wall-clock ms, so ages survive a restart).
 */
class BestiaryTracker(families: BestiaryFamilies?, private val warn: Consumer<String>) {
	private class State(val key: String, val name: String, val family: BestiaryFamily?) {
		var kills: SbNumber? = null
		var killsSource: BestiarySource? = null
		var killsAt: Long? = null
		var tier: Int? = null
		var max: Boolean = false
		/** The widget's next threshold and the tier it was shown for. */
		var widgetNext: Int? = null
		var widgetTier: Int? = null
		/** The widget line last applied, so an unchanged one is not taken for a new value. */
		var widgetLine: WidgetLine? = null
		var source: BestiarySource = BestiarySource.WIDGET
		var updatedAt: Long = 0
		var baseline: Long? = null
		var session: Long = 0
		/** The session's counted rises (time, kills), for the rolling rate, and when its first exact count came. */
		val counted = ArrayDeque<LongArray>()
		var dataSince: Long? = null
	}

	private class Milestone(var milestone: Int, var tiersToNext: Int?, var source: BestiarySource, var updatedAt: Long)

	private class ProfileRef(val account: Any, val key: String, val id: String?)

	private val resolver: FamilyResolver? = families?.let { FamilyResolver(it, warn) }
	private val states = LinkedHashMap<String, State>()

	/** The key each widget name was last placed under, for a shared name the island cannot place. */
	private val keyByName = HashMap<String, String>()
	private val mismatchLogged = HashSet<String>()
	private var milestone: Milestone? = null
	private var profile: ProfileRef? = null
	private var windowMs = DEFAULT_WINDOW_MINUTES * MINUTE_MS
	private var durationMs = 0L
	private var lastTick: Long? = null
	private var lastIsland: String? = null

	/** The family lines of the `Bestiary:` widget, read on [island]. */
	fun widget(lines: List<WidgetLine>, island: String?, now: Long) {
		val seen = HashSet<String>()
		for (line in lines) {
			val name = normalise(line.name)
			val family = resolver?.onIsland(line.name, island, line.next?.exact()?.toInt())
			val state = if (family != null) state(family, line.name) else keyByName[name]?.let { states[it] } ?: state(null, line.name)
			keyByName[name] = state.key
			if (!seen.add(state.key)) {
				continue
			}
			count(state, line.kills, now)
			if (line == state.widgetLine) {
				continue
			}
			state.widgetLine = line
			line.kills?.let { setKills(state, it, BestiarySource.WIDGET, now) }
			state.tier = line.tier
			state.max = line.max
			state.widgetTier = line.tier
			state.widgetNext = line.next?.exact()?.toInt()
			touch(state, BestiarySource.WIDGET, now)
			checkThreshold(state)
		}
	}

	/** A BESTIARY chat event, seen on [island]. */
	fun chat(event: BestiaryChatEvent, island: String?, now: Long) {
		when (event) {
			is BestiaryChatEvent.TierUp -> {
				val family = resolver?.onIsland(event.name, island)
				val state = if (family != null) state(family, event.name) else keyByName[normalise(event.name)]?.let { states[it] } ?: state(null, event.name)
				state.tier = event.to
				state.max = maxed(state.family, event.to)
				touch(state, BestiarySource.CHAT, now)
				milestone?.let { known ->
					known.tiersToNext = known.tiersToNext?.let { (it - (event.to - event.from)).coerceAtLeast(0) }
					known.source = BestiarySource.CHAT
					known.updatedAt = now
				}
			}
			is BestiaryChatEvent.Milestone -> {
				val known = milestone
				when {
					!event.claimed -> milestone = Milestone(event.to, TIERS_PER_MILESTONE, BestiarySource.CHAT, now)
					// A claim's "to" is a milestone reached earlier: it adds nothing unless the tracker has not seen it.
					known == null || event.to > known.milestone -> milestone = Milestone(event.to, null, BestiarySource.CHAT, now)
				}
			}
			is BestiaryChatEvent.Unreadable -> {}
		}
	}

	/** A Bestiary menu page the player opened. */
	fun menu(page: BestiaryPage, now: Long) {
		for (shown in page.families) {
			val family = resolver?.inMenu(shown.name, page.parent, page.area)
			val state = state(family, shown.name)
			shown.kills?.let { setKills(state, it, BestiarySource.MENU, now) }
			state.tier = shown.tier
			state.max = shown.max ?: maxed(family, shown.tier)
			touch(state, BestiarySource.MENU, now)
		}
		page.milestone?.let { milestone = Milestone(it.milestone, it.tiersToNext, BestiarySource.MENU, now) }
	}

	/**
	 * A world or server change, a disconnect, or the tracker switched off and on: the next widget value of each family is a
	 * baseline again (EC-BEST-06), and no kills made meanwhile count.
	 */
	fun worldChanged() {
		states.values.forEach { it.baseline = null }
	}

	/**
	 * The profile now known: [account], [key] (its name) and, once seen, its [id]. Another name, or another id for the
	 * same name (a profile made again), is another profile; an id becoming known is not.
	 */
	fun profile(account: Any, key: String, id: String?) {
		val last = profile
		val same = last != null && last.account == account && last.key == key
		if (last != null && (!same || last.id != null && id != null && last.id != id)) {
			profileChanged()
		}
		profile = ProfileRef(account, key, id ?: last?.id?.takeIf { same })
	}

	/** Another profile: nothing of the last one shows or counts, and the session's once-a-session logs start over. */
	fun profileChanged() {
		resetSession()
		states.clear()
		keyByName.clear()
		mismatchLogged.clear()
		milestone = null
	}

	/** The rolling window of the rate, 1 to 60 minutes (REQ-BEST-06). */
	fun setWindowMinutes(minutes: Int) {
		windowMs = minutes.coerceIn(1, MAX_WINDOW_MINUTES) * MINUTE_MS
	}

	/**
	 * A client tick while the tracker is ON: the session's time grows while [onSkyBlock]. A gap between ticks (the game
	 * paused or the tracker off) counts as at most a second.
	 */
	fun tick(onSkyBlock: Boolean, now: Long) {
		val last = lastTick
		if (last != null && onSkyBlock) {
			durationMs += (now - last).coerceIn(0, MAX_TICK_GAP_MS)
		}
		lastTick = now
	}

	/**
	 * Where the player now is: every location change resyncs the baselines (REQ-BEST-11); with [resetOnIslandChange] an
	 * island other than the last one also ends the session (REQ-BEST-07). An unknown island (a transfer) is no change.
	 */
	fun locationChanged(island: String?, resetOnIslandChange: Boolean) {
		worldChanged()
		if (island == null) {
			return
		}
		if (resetOnIslandChange && lastIsland != null && island != lastIsland) {
			resetSession()
		}
		lastIsland = island
	}

	/** A manual reset: the session's kills, rates and time start over; the shown values stay. */
	fun resetSession() {
		for (state in states.values) {
			state.session = 0
			state.counted.clear()
			state.dataSince = null
		}
		mismatchLogged.clear()
		durationMs = 0
	}

	/** The rate and ETAs of the family with [key] at [now]. */
	fun rate(key: String, now: Long): RateView {
		val state = states[key] ?: return RateView(null, null, null)
		val since = state.dataSince?.let { now - it } ?: return RateView(null, null, null)
		if (since < MIN_DATA_MS) {
			return RateView(null, null, null)
		}
		val kills = state.counted.sumOf { if (it[0] > now - windowMs) it[1] else 0L }
		val perHour = kills * HOUR_MS.toDouble() / minOf(windowMs, since)
		val view = view(state)
		return RateView(perHour, eta(view.toNext, perHour), eta(view.toMax, perHour))
	}

	/** The session as it stands. */
	fun session(): SessionView {
		val kills = sessionTotal()
		val average = if (durationMs < MIN_DATA_MS) null else kills * HOUR_MS.toDouble() / durationMs
		return SessionView(kills, durationMs, average)
	}

	/** Every family seen, in the order first seen. */
	fun families(): List<FamilyView> = states.values.map { view(it) }

	/** The family with [key] (its table id, or `name:<name>` for a name the table does not know), or null. */
	fun family(key: String): FamilyView? = states[key]?.let { view(it) }

	fun milestone(): MilestoneView? = milestone?.let { MilestoneView(it.milestone, it.tiersToNext, it.source, it.updatedAt) }

	/** The live kills of the session over all families. */
	fun sessionTotal(): Long = states.values.sumOf { it.session }

	private fun state(family: BestiaryFamily?, name: String): State {
		val key = family?.id ?: UNKNOWN + normalise(name)
		return states.getOrPut(key) { State(key, family?.name ?: GameText.strip(name).trim(), family) }
	}

	private fun setKills(state: State, kills: SbNumber, source: BestiarySource, now: Long) {
		state.kills = kills
		state.killsSource = source
		state.killsAt = now
	}

	private fun touch(state: State, source: BestiarySource, now: Long) {
		state.source = source
		state.updatedAt = now
	}

	private fun count(state: State, kills: SbNumber?, now: Long) {
		val exact = kills?.exact() ?: return
		if (state.dataSince == null) {
			state.dataSince = now
		}
		val baseline = state.baseline
		if (baseline != null) {
			val delta = exact - baseline
			if (delta in 1..MAX_DELTA) {
				state.session += delta
				state.counted.addLast(longArrayOf(now, delta))
				while (state.counted.first()[0] <= now - MAX_WINDOW_MINUTES * MINUTE_MS) {
					state.counted.removeFirst()
				}
			}
		}
		state.baseline = exact
	}

	private fun checkThreshold(state: State) {
		val family = state.family ?: return
		val shown = state.widgetNext ?: return
		val tier = state.widgetTier ?: return
		val table = family.thresholds.getOrNull(tier)
		if (table == shown || !mismatchLogged.add(family.id)) {
			return
		}
		val tableSays = if (table == null) "the table ends at tier ${family.maxTier()}" else "the table's at ${grouped(table)}"
		warn.accept("Bestiary family ${family.id} at tier $tier: the widget's next tier is at ${grouped(shown)} kills, $tableSays; the widget's is used")
	}

	private fun view(state: State): FamilyView {
		val family = state.family
		val tier = state.tier
		val max = state.max
		val next = when {
			max || tier == null -> null
			state.widgetTier == tier && state.widgetNext != null -> state.widgetNext
			else -> family?.thresholds?.getOrNull(tier)
		}
		val kills = state.kills?.value?.roundToLong()
		val toNext = if (next != null && kills != null) (next - kills).coerceAtLeast(0) else null
		val toMax = when {
			max -> 0L
			// Never less than to the next tier: the widget may show tiers past the table's cap (EC-BEST-09).
			family != null && kills != null -> (maxOf(family.cap, next ?: 0) - kills).coerceAtLeast(0)
			else -> null
		}
		return FamilyView(state.key, state.name, family, state.kills, tier, next, max, toNext, toMax, state.source, state.updatedAt,
			state.session, state.killsSource, state.killsAt)
	}

	private companion object {
		const val UNKNOWN = "name:"
		const val MAX_DELTA = 50L
		const val TIERS_PER_MILESTONE = 10
		const val DEFAULT_WINDOW_MINUTES = 5L
		const val MAX_WINDOW_MINUTES = 60
		const val MINUTE_MS = 60_000L
		const val HOUR_MS = 3_600_000L

		/** Under a minute of data the rate and the average are "—" (REQ-BEST-06). */
		const val MIN_DATA_MS = 60_000L
		const val MAX_TICK_GAP_MS = 1_000L

		/** [left] at [perHour], in ms; null when nothing is left or nothing comes. */
		fun eta(left: Long?, perHour: Double): Long? =
			if (left == null || left <= 0 || perHour <= 0) null else Math.round(left / perHour * HOUR_MS)
		val SPACES = Regex("\\s+")

		fun maxed(family: BestiaryFamily?, tier: Int): Boolean = family != null && tier >= family.maxTier()

		fun normalise(name: String): String = GameText.strip(name).trim().replace(SPACES, " ").lowercase(Locale.ROOT)

		fun grouped(value: Int): String = String.format(Locale.ROOT, "%,d", value)
	}
}
