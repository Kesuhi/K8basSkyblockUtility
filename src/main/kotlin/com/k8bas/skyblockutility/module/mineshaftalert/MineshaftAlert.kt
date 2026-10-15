package com.k8bas.skyblockutility.module.mineshaftalert

import com.k8bas.skyblockutility.skyblock.Corpse
import com.k8bas.skyblockutility.skyblock.MineshaftSnapshot
import com.k8bas.skyblockutility.skyblock.WidgetPresence
import java.util.function.Consumer

/**
 * When the mineshaft entry alert posts (T3.2, REQ-MSA-01, -04, -07, -08, -12), on a clock it is given, so it is
 * tested without a game. An entry is a server id the location service reports as a Glacite Mineshaft; the same id
 * again gives no second alert once one was posted. Only a whole corpse list counts: a cut-off (PARTIAL) widget is
 * treated as no list, so no wrong count is shown (REQ-GS-10). The first alert waits until the list has been
 * non-empty and unchanged for 1.5 s and the shaft has a name. With no list after 8 s (and once the shaft has a
 * name) it posts "corpse list unavailable", with the widget hint once per launch, or "no corpses listed" when the
 * widget is shown but empty (EC-MSA-06). A list that appears, or grows, within 30 s of entry gives one follow-up
 * each. Leaving cancels what is pending; a disconnect forgets everything.
 */
class MineshaftAlert(private val post: Consumer<Event>) {
	sealed class Event {
		data class Alert(val name: String, val corpses: List<Corpse>, val followUp: Boolean) : Event()

		/** No corpse list: [empty] when the widget is shown with no lines, else it is missing or cut off. */
		data class Unavailable(val name: String, val hint: Boolean, val empty: Boolean) : Event()
	}

	private var server: String? = null
	private var active = false
	private var entered = 0L
	private var list: List<Corpse> = listOf()
	private var stableSince = 0L
	private var firstPosted = false
	private var unavailablePosted = false
	private var followUpPosted = false
	private var postedSize = 0

	/** When the list that is waiting to be posted first appeared, or first grew past the posted size. */
	private var pendingSince: Long? = null
	private var hintShown = false
	private var lastPostedAt = -1L

	/** The location service reports a Glacite Mineshaft on [serverId]. */
	fun enter(serverId: String?, now: Long) {
		active = true
		if (serverId != null && serverId == server && (firstPosted || unavailablePosted)) {
			// The same shaft reported again after its alert: at most one first alert per server id.
			return
		}
		server = serverId
		entered = now
		list = listOf()
		stableSince = now
		firstPosted = false
		unavailablePosted = false
		followUpPosted = false
		postedSize = 0
		pendingSince = null
	}

	/** Left the shaft (another island or a world change): what is pending is cancelled. */
	fun leave() {
		active = false
	}

	/** Disconnected: everything about the last shaft is forgotten; the once-per-launch hint stays shown. */
	fun reset() {
		leave()
		server = null
	}

	/** When the last line was posted, for tests. */
	fun lastPostedAt(): Long = lastPostedAt

	fun tick(snapshot: MineshaftSnapshot, now: Long) {
		if (!active || !snapshot.inShaft) {
			return
		}
		val presence = snapshot.corpses.presence
		val current = if (presence == WidgetPresence.PRESENT) snapshot.corpses.corpses else listOf()
		if (current != list) {
			list = current
			stableSince = now
		}
		if (presence == WidgetPresence.PRESENT) {
			// A tick without a whole list (the widget redrawn or cut off) keeps when the change was first seen.
			val waiting = if (firstPosted) list.size > postedSize else list.isNotEmpty()
			pendingSince = if (waiting) pendingSince ?: now else null
		}
		val name = snapshot.name
		val stable = list.isNotEmpty() && now - stableSince >= SETTLE_MS && name != null
		val withinWindow = pendingSince.let { it != null && it - entered <= FOLLOW_UP_WINDOW_MS }
		when {
			!firstPosted && stable && (!unavailablePosted || withinWindow) -> {
				// After "corpse list unavailable", a list that comes later is the follow-up (REQ-MSA-08); one
				// Dead Man's Chest follow-up may still come after it (REQ-MSA-07).
				emit(Event.Alert(name!!, list, unavailablePosted), now)
				firstPosted = true
				postedSize = list.size
				pendingSince = null
			}
			!firstPosted && !unavailablePosted && list.isEmpty() && name != null && now - entered >= UNAVAILABLE_MS -> {
				// Waits for the shaft name, so "Unknown shaft" only comes once the 15 s code search is over.
				if (presence == WidgetPresence.EMPTY) {
					emit(Event.Unavailable(name, hint = false, empty = true), now)
				} else {
					emit(Event.Unavailable(name, !hintShown, empty = false), now)
					hintShown = true
				}
				unavailablePosted = true
			}
			firstPosted && !followUpPosted && stable && list.size > postedSize && withinWindow -> {
				// A Dead Man's Chest added a corpse (REQ-MSA-07); a corpse looted changes nothing.
				emit(Event.Alert(name!!, list, true), now)
				followUpPosted = true
				postedSize = list.size
				pendingSince = null
			}
		}
	}

	private fun emit(event: Event, now: Long) {
		lastPostedAt = now
		post.accept(event)
	}

	companion object {
		const val SETTLE_MS = 1_500L
		const val UNAVAILABLE_MS = 8_000L
		const val FOLLOW_UP_WINDOW_MS = 30_000L
	}
}
