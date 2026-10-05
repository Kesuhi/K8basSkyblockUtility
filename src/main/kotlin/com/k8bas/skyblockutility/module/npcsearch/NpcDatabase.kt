package com.k8bas.skyblockutility.module.npcsearch

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.location.Islands
import com.k8bas.skyblockutility.net.SharedHttpClient
import com.k8bas.skyblockutility.ui.option.RuleDatabase
import com.k8bas.skyblockutility.util.JsonEntries
import net.minecraft.client.Minecraft
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * Fetched fresh from a public Gist — deliberately not cached to disk, mirroring MobDatabase's
 * approach, so entries added to the Gist show up next launch without a mod update.
 *
 * Fetched at module registration (mod startup), not lazily on first picker-open — see
 * MobDatabase's doc for the full reasoning.
 */
object NpcDatabase {
	private const val RAW_URL = "https://gist.githubusercontent.com/Kesuhi/7234bbe0e2f587abd2ada774bb88104f/raw/npc_database.json"
	private val fetchStarted = AtomicBoolean(false)

	@Volatile
	private var entries: List<NpcDatabaseEntry> = java.util.List.of()

	@Volatile
	private var byId: Map<String, NpcDatabaseEntry> = java.util.Map.of()

	/** Loading until the fetch ends; then ready, or unavailable if it failed (the picker says so, EC-UI-08). */
	@Volatile
	private var state: RuleDatabase.State = RuleDatabase.State.LOADING
	private val LOADED_LISTENERS: MutableList<Runnable> = CopyOnWriteArrayList()

	/** Idempotent — only actually starts the fetch the first time this is called. */
	@JvmStatic
	fun fetchIfNeeded() {
		if (!fetchStarted.compareAndSet(false, true)) {
			return
		}
		Thread.ofVirtual().name("k8bas-npc-database-fetch").start {
			val body = SharedHttpClient.fetchText(URI.create(RAW_URL), "NPC database fetch", Consumer { K8basSkyblockUtilityClient.LOGGER.warn(it) })
			if (body == null) {
				state = RuleDatabase.State.UNAVAILABLE
				return@start
			}
			val parsed = try {
				parse(body)
			} catch (e: RuntimeException) {
				K8basSkyblockUtilityClient.LOGGER.warn("NPC database is not a list of entries: {}", e.toString())
				state = RuleDatabase.State.UNAVAILABLE
				return@start
			}
			byId = index(parsed)
			entries = parsed
			state = RuleDatabase.State.READY
			K8basSkyblockUtilityClient.LOGGER.info("Loaded {} NPC database entries", parsed.size)
			Minecraft.getInstance().execute { LOADED_LISTENERS.forEach(Runnable::run) }
		}
	}

	/**
	 * The gist's JSON array as an immutable list; an empty document gives an empty list, and
	 * malformed entries are skipped with one warning (REQ-NPCDB-05). Fixed NPCs listed on
	 * "Catacombs" stand in the dungeon lobby, so they are normalised to "Dungeon Hub"
	 * (REQ-LOC-03); moving NPCs keep "Catacombs".
	 */
	@JvmStatic
	fun parse(json: String?): List<NpcDatabaseEntry> {
		val parsed = JsonEntries.parse(json, NpcDatabaseEntry::class.java, Predicate { valid(it) })
		if (parsed.skipped > 0) {
			K8basSkyblockUtilityClient.LOGGER.warn("Skipped {} malformed NPC database entries", parsed.skipped)
		}
		for (entry in parsed.entries) {
			if (entry.fixed && Islands.CATACOMBS == entry.island) {
				entry.island = Islands.DUNGEON_HUB
			}
		}
		return parsed.entries
	}

	/** An id, a name and an island; a fixed NPC needs its coordinates, a moving one a match text. */
	@JvmStatic
	fun valid(entry: NpcDatabaseEntry): Boolean {
		if (entry.id == null || entry.displayName == null || entry.island == null) {
			return false
		}
		if (entry.fixed) {
			return isFinite(entry.x) && isFinite(entry.y) && isFinite(entry.z)
		}
		return !JsonEntries.isBlank(entry.matchText)
	}

	private fun isFinite(value: Double?): Boolean = value != null && value.isFinite()

	/** Entries by id; the first wins if an id repeats. */
	@JvmStatic
	fun index(entries: List<NpcDatabaseEntry>): Map<String, NpcDatabaseEntry> {
		val index = HashMap<String, NpcDatabaseEntry>()
		for (entry in entries) {
			index.putIfAbsent(entry.id, entry)
		}
		return java.util.Map.copyOf(index)
	}

	@JvmStatic
	fun state(): RuleDatabase.State = state

	/** Every entry, in the gist's order (empty until loaded). */
	@JvmStatic
	fun entries(): List<NpcDatabaseEntry> = entries

	/** For gametests, which have no network: the list as if it had been fetched (or had failed). */
	@JvmStatic
	fun useForTest(list: List<NpcDatabaseEntry>, newState: RuleDatabase.State) {
		fetchStarted.set(true)
		byId = index(list)
		entries = java.util.List.copyOf(list)
		state = newState
	}

	/** The entry with this id, or null if there is none (also while the list is still loading). */
	@JvmStatic
	fun byId(id: String?): NpcDatabaseEntry? = if (id == null) null else byId[id]

	/** Runs the listener on the client thread each time the list has loaded. */
	@JvmStatic
	fun onLoaded(listener: Runnable) {
		LOADED_LISTENERS.add(listener)
	}
}
