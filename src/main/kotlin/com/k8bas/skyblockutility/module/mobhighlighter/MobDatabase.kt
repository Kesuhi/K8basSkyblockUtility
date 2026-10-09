package com.k8bas.skyblockutility.module.mobhighlighter

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.net.SharedHttpClient
import com.k8bas.skyblockutility.ui.option.RuleDatabase
import com.k8bas.skyblockutility.util.JsonEntries
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * Fetched fresh from a public Gist — deliberately not cached to disk, so entries added to the
 * Gist show up next launch without shipping a mod update. If the fetch hasn't completed yet (or
 * failed), the picker says so (state()).
 *
 * Fetched at module registration (mod startup), not lazily on first picker-open — opening the
 * picker for the first time in a session should never show the "still loading" message if it can
 * be helped, so the fetch gets a head start before the player is likely to have opened it.
 */
object MobDatabase {
	private const val RAW_URL = "https://gist.githubusercontent.com/Kesuhi/f68f11d96e15342f36c2402fde8d5ac1/raw/mob_database.json"
	private val fetchStarted = AtomicBoolean(false)

	@Volatile
	private var entries: List<MobDatabaseEntry> = java.util.List.of()

	/** Loading until the fetch ends; then ready, or unavailable if it failed (the picker says so, EC-UI-08). */
	@Volatile
	private var state: RuleDatabase.State = RuleDatabase.State.LOADING

	/** Idempotent — only actually starts the fetch the first time this is called. */
	@JvmStatic
	fun fetchIfNeeded() {
		if (!fetchStarted.compareAndSet(false, true)) {
			return
		}
		Thread.ofVirtual().name("k8bas-mob-database-fetch").start {
			val body = SharedHttpClient.fetchText(URI.create(RAW_URL), "Mob database fetch", Consumer { K8basSkyblockUtilityClient.LOGGER.warn(it) })
			if (body == null) {
				state = RuleDatabase.State.UNAVAILABLE
				return@start
			}
			try {
				entries = parse(body)
				state = RuleDatabase.State.READY
				K8basSkyblockUtilityClient.LOGGER.info("Loaded {} mob database entries", entries.size)
			} catch (e: RuntimeException) {
				state = RuleDatabase.State.UNAVAILABLE
				K8basSkyblockUtilityClient.LOGGER.warn("Mob database is not a list of entries: {}", e.toString())
			}
		}
	}

	/**
	 * The gist's JSON array as an immutable list; an empty document gives an empty list, and
	 * malformed entries are skipped with one warning (REQ-NPCDB-05).
	 */
	@JvmStatic
	fun parse(json: String?): List<MobDatabaseEntry> {
		val parsed = JsonEntries.parse(json, MobDatabaseEntry::class.java, Predicate { valid(it) })
		if (parsed.skipped > 0) {
			K8basSkyblockUtilityClient.LOGGER.warn("Skipped {} malformed mob database entries", parsed.skipped)
		}
		return parsed.entries
	}

	/** An id, a name, an island and a match text. */
	@JvmStatic
	fun valid(entry: MobDatabaseEntry): Boolean =
		entry.id != null && entry.displayName != null && entry.island != null && !JsonEntries.isBlank(entry.matchText)

	@JvmStatic
	fun state(): RuleDatabase.State = state

	/** Every entry, in the gist's order (empty until loaded). */
	@JvmStatic
	fun entries(): List<MobDatabaseEntry> = entries

	/** For gametests, which have no network: the list as if it had been fetched (or had failed). */
	@JvmStatic
	fun useForTest(list: List<MobDatabaseEntry>, newState: RuleDatabase.State) {
		fetchStarted.set(true)
		entries = java.util.List.copyOf(list)
		state = newState
	}
}
