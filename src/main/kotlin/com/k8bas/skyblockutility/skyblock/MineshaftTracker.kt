package com.k8bas.skyblockutility.skyblock

import java.util.function.Consumer
import java.util.function.Function

/** One Frozen Corpses entry: its type and whether it was looted. */
data class Corpse(val type: String, val looted: Boolean)

/** The Frozen Corpses widget (REQ-GS-17): whether it is shown, and its corpses in listed order. */
data class CorpseList(val presence: WidgetPresence, val corpses: List<Corpse>) {
	companion object {
		@JvmField
		val ABSENT = CorpseList(WidgetPresence.ABSENT, listOf())
	}
}

/**
 * The current Glacite Mineshaft (REQ-GS-17), immutable: whether the player is in one, its code and display
 * name, whether the code search still runs, and the corpse list. [name] is null while searching, the code
 * itself for a code not in the table, and "Unknown shaft" when no code came within 15 s.
 */
data class MineshaftSnapshot(val inShaft: Boolean, val code: String?, val name: String?, val searching: Boolean, val corpses: CorpseList) {
	companion object {
		@JvmField
		val NONE = MineshaftSnapshot(false, null, null, false, CorpseList.ABSENT)
	}
}

/**
 * The mineshaft rules without any game dependency (T3.0m, REQ-GS-17): on entry, the shaft code
 * `[A-Z]{4}_[12CL]` is searched in the sidebar for up to 15 s; the corpse list comes from the "Frozen Corpses"
 * tab widget (`<Type>: NOT LOOTED|UNLOOTED|LOOTED`), never from entities. An unknown corpse line is reported
 * through [unknownLine] and makes the list PARTIAL, so no wrong count is shown (REQ-GS-10). Leaving clears it
 * all (REQ-GS-15). Not thread-safe: the game side drives it on the client thread.
 *
 * @param names the display name of a known code, or null
 */
class MineshaftTracker(private val names: Function<String, String?>, private val unknownLine: Consumer<String>) {
	private var inShaft = false
	private var enteredAt = 0L
	private var code: String? = null
	private var searchOver = false
	private var corpses = CorpseList.ABSENT
	private var simulated = false

	/** The player entered a mineshaft at [now] (ms): a new search starts. */
	fun enter(now: Long) {
		inShaft = true
		enteredAt = now
		code = null
		searchOver = false
		corpses = CorpseList.ABSENT
		simulated = false
	}

	/** Left the shaft, switched server or disconnected. */
	fun leave() {
		inShaft = false
		code = null
		searchOver = false
		corpses = CorpseList.ABSENT
		simulated = false
	}

	/** The sidebar lines at [now]; the first whole code found while searching is kept. */
	fun sidebar(lines: List<String>, now: Long) {
		tick(now)
		if (!inShaft || simulated || code != null || searchOver) {
			return
		}
		code = lines.firstNotNullOfOrNull { CODE.find(it)?.value }
	}

	/** Ends the search once 15 s have passed without a code. */
	fun tick(now: Long) {
		if (inShaft && code == null && !searchOver && now - enteredAt >= SEARCH_MS) {
			searchOver = true
		}
	}

	/** The tab widgets: the Frozen Corpses list. */
	fun tab(tab: TabSnapshot) {
		if (!inShaft || simulated) {
			return
		}
		corpses = corpseList(tab)
	}

	/** Dev only (`/ksu debug shaft`): a shaft with [code] and corpses written `Type:NOT_LOOTED|LOOTED`. */
	fun simulate(code: String, corpses: List<String>, now: Long) {
		enter(now)
		simulated = true
		this.code = code
		this.corpses = CorpseList(WidgetPresence.PRESENT, java.util.List.copyOf(corpses.map { entry ->
			val (type, state) = entry.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
			Corpse(type.trim(), state.trim().equals("LOOTED", ignoreCase = true))
		}))
	}

	/** True while a dev simulation (`/ksu debug shaft`) stands in for a real shaft. */
	fun isSimulated(): Boolean = simulated

	fun snapshot(): MineshaftSnapshot {
		if (!inShaft) {
			return MineshaftSnapshot.NONE
		}
		val found = code
		val name = when {
			found != null -> names.apply(found) ?: found
			searchOver -> UNKNOWN
			else -> null
		}
		return MineshaftSnapshot(true, found, name, found == null && !searchOver, corpses)
	}

	private fun corpseList(tab: TabSnapshot): CorpseList {
		val widget = tab.widget(WIDGET) ?: return CorpseList.ABSENT
		val presence = tab.presence(WIDGET)
		if (presence == WidgetPresence.EMPTY) {
			return CorpseList(WidgetPresence.EMPTY, listOf())
		}
		var complete = presence == WidgetPresence.PRESENT
		val list = ArrayList<Corpse>()
		for (line in widget.lines) {
			val match = LINE.matchEntire(line.trim())
			if (match == null) {
				unknownLine.accept(line)
				complete = false
				continue
			}
			list.add(Corpse(match.groupValues[1], match.groupValues[2] == "LOOTED"))
		}
		return CorpseList(if (complete) WidgetPresence.PRESENT else WidgetPresence.PARTIAL, java.util.List.copyOf(list))
	}

	companion object {
		/** The tab widget's header. */
		const val WIDGET = "Frozen Corpses"

		/** What a shaft with no code within 15 s is called. */
		const val UNKNOWN = "Unknown shaft"

		private const val SEARCH_MS = 15_000L
		private val CODE = Regex("(?<![A-Za-z0-9_])[A-Z]{4}_[12CL](?![A-Za-z0-9_])")
		private val LINE = Regex("^(.+?): (NOT LOOTED|UNLOOTED|LOOTED)$")
	}
}
