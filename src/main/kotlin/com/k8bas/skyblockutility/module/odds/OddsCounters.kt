package com.k8bas.skyblockutility.module.odds

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.time.LocalDate

/**
 * The Rare Drop Odds counters (REQ-ODDS-12). Per SkyBlock profile and kept across restarts: Scatha kills counted,
 * Scatha kills since the last Scatha pet, and corpses looted per type, with the day counting started ("counted by
 * K8bas since <date>"). Per session: Scatha kills and corpses looted, reset only when another profile arrives, on a
 * restart or by "Reset odds counters" (not when the same profile is named again after a reconnect or gets its id).
 *
 * While the profile is unknown, or known but not yet storable (its id still pending), the per-profile values are null,
 * so a panel shows no number rather than a false zero. What is counted meanwhile is kept and added to that profile
 * once it can be stored; counts made while no profile was named at all are not kept.
 *
 * @param read  the saved state of the current profile, or null
 * @param write saves the state of the current profile
 */
class OddsCounters(private val read: java.util.function.Supplier<String?>, private val write: java.util.function.Consumer<String>,
	private val today: java.util.function.Supplier<LocalDate>) {
	/** What is saved per profile. Gson leaves missing fields at their defaults. */
	class Saved {
		@JvmField
		var since: String? = null

		@JvmField
		var scathaKills: Long = 0

		@JvmField
		var scathaSincePet: Long = 0

		@JvmField
		var corpses: MutableMap<String, Long>? = LinkedHashMap()
	}

	/** Counted while [profile] could not be stored yet. */
	private class Pending(val profile: String) {
		var kills = 0L
		var petSeen = false
		var killsAfterPet = 0L
		val corpses = LinkedHashMap<String, Long>()
	}

	private var saved: Saved? = null
	private var profile: String? = null
	private var lastNamed: String? = null
	private var pending: Pending? = null
	private var sessionScatha = 0L
	private val sessionCorpses = LinkedHashMap<String, Long>()

	/**
	 * The profile changed: [profile] identifies it (null while unknown), and [storable] says whether its state can be
	 * read and written now. The session starts over only when a profile other than the last named one arrives.
	 */
	fun load(profile: String?, storable: Boolean) {
		if (profile != null && profile != lastNamed) {
			resetSession()
			lastNamed = profile
		}
		this.profile = profile
		if (profile == null || !storable) {
			saved = null
			return
		}
		saved = parse(read.get())
		val kept = pending
		pending = null
		if (kept != null && kept.profile == profile) {
			merge(kept)
		}
	}

	/** "Reset odds counters": the session counters only. */
	fun resetSession() {
		sessionScatha = 0
		sessionCorpses.clear()
	}

	fun scathaKill() {
		sessionScatha++
		count({ it.scathaKills++; it.scathaSincePet++ }) { it.kills++; it.killsAfterPet++ }
	}

	/** Your own Scatha pet dropped: "since last pet" starts again (REQ-ODDS-16). */
	fun scathaPet() {
		count({ it.scathaSincePet = 0 }) { it.petSeen = true; it.killsAfterPet = 0 }
	}

	fun corpseLooted(type: String) {
		sessionCorpses.merge(type, 1L, Long::plus)
		count({ it.corpses!!.merge(type, 1L, Long::plus) }) { it.corpses.merge(type, 1L, Long::plus) }
	}

	/** Scatha kills counted on this profile, or null while it is unknown or not storable. */
	fun scathaKills(): Long? = saved?.scathaKills

	fun scathaSincePet(): Long? = saved?.scathaSincePet

	fun corpses(type: String): Long? = saved?.let { it.corpses!![type] ?: 0 }

	fun sessionScathaKills(): Long = sessionScatha

	fun sessionCorpses(type: String): Long = sessionCorpses[type] ?: 0

	/** The day counting started on this profile, or null before the first count or while unknown. */
	fun since(): String? = saved?.since

	private fun count(stored: (Saved) -> Unit, kept: (Pending) -> Unit) {
		val now = saved
		if (now != null) {
			start(now)
			stored(now)
			write.accept(GSON.toJson(now))
			return
		}
		val named = profile ?: return
		val waiting = pending?.takeIf { it.profile == named } ?: Pending(named).also { pending = it }
		kept(waiting)
	}

	private fun merge(kept: Pending) {
		val now = saved!!
		start(now)
		now.scathaKills += kept.kills
		now.scathaSincePet = if (kept.petSeen) kept.killsAfterPet else now.scathaSincePet + kept.kills
		kept.corpses.forEach { (type, n) -> now.corpses!!.merge(type, n, Long::plus) }
		write.accept(GSON.toJson(now))
	}

	private fun start(now: Saved) {
		if (now.since == null) {
			now.since = today.get().toString()
		}
	}

	private fun parse(text: String?): Saved {
		val parsed = text?.let {
			try {
				GSON.fromJson(it, Saved::class.java)
			} catch (e: JsonParseException) {
				null
			}
		} ?: Saved()
		if (parsed.corpses == null) {
			parsed.corpses = LinkedHashMap()
		}
		return parsed
	}

	companion object {
		private val GSON = Gson()
	}
}
