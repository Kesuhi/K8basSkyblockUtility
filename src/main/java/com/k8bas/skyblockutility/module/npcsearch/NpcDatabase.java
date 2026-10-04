package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import com.k8bas.skyblockutility.location.Islands;
import com.k8bas.skyblockutility.net.SharedHttpClient;
import com.k8bas.skyblockutility.util.JsonEntries;

import java.net.URI;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetched fresh from a public Gist — deliberately not cached to disk, mirroring MobDatabase's
 * approach, so entries added to the Gist show up next launch without a mod update.
 *
 * Fetched at module registration (mod startup), not lazily on first picker-open — see
 * MobDatabase's javadoc for the full reasoning.
 */
public final class NpcDatabase {
	private static final String RAW_URL =
			"https://gist.githubusercontent.com/Kesuhi/7234bbe0e2f587abd2ada774bb88104f/raw/npc_database.json";
	private static final AtomicBoolean fetchStarted = new AtomicBoolean(false);

	private static volatile List<NpcDatabaseEntry> entries = List.of();
	private static volatile Map<String, NpcDatabaseEntry> byId = Map.of();
	/** Loading until the fetch ends; then ready, or unavailable if it failed (the picker says so, EC-UI-08). */
	private static volatile RuleDatabase.State state = RuleDatabase.State.LOADING;
	private static final List<Runnable> LOADED_LISTENERS = new CopyOnWriteArrayList<>();

	private NpcDatabase() {
	}

	/** Idempotent — only actually starts the fetch the first time this is called. */
	public static void fetchIfNeeded() {
		if (!fetchStarted.compareAndSet(false, true)) {
			return;
		}
		Thread.ofVirtual().name("k8bas-npc-database-fetch").start(() -> {
			String body = SharedHttpClient.fetchText(URI.create(RAW_URL), "NPC database fetch", K8basSkyblockUtilityClient.LOGGER::warn);
			if (body == null) {
				state = RuleDatabase.State.UNAVAILABLE;
				return;
			}
			List<NpcDatabaseEntry> parsed;
			try {
				parsed = parse(body);
			} catch (RuntimeException e) {
				K8basSkyblockUtilityClient.LOGGER.warn("NPC database is not a list of entries: {}", e.toString());
				state = RuleDatabase.State.UNAVAILABLE;
				return;
			}
			byId = index(parsed);
			entries = parsed;
			state = RuleDatabase.State.READY;
			K8basSkyblockUtilityClient.LOGGER.info("Loaded {} NPC database entries", parsed.size());
			Minecraft.getInstance().execute(() -> LOADED_LISTENERS.forEach(Runnable::run));
		});
	}

	/** The gist's JSON array as an immutable list; an empty document gives an empty list, and
	 *  malformed entries are skipped with one warning (REQ-NPCDB-05). Fixed NPCs listed on
	 *  "Catacombs" stand in the dungeon lobby, so they are normalised to "Dungeon Hub"
	 *  (REQ-LOC-03); moving NPCs keep "Catacombs". */
	static List<NpcDatabaseEntry> parse(String json) {
		JsonEntries.Parsed<NpcDatabaseEntry> parsed = JsonEntries.parse(json, NpcDatabaseEntry.class, NpcDatabase::valid);
		if (parsed.skipped() > 0) {
			K8basSkyblockUtilityClient.LOGGER.warn("Skipped {} malformed NPC database entries", parsed.skipped());
		}
		for (NpcDatabaseEntry entry : parsed.entries()) {
			if (entry.fixed && Islands.CATACOMBS.equals(entry.island)) {
				entry.island = Islands.DUNGEON_HUB;
			}
		}
		return parsed.entries();
	}

	/** An id, a name and an island; a fixed NPC needs its coordinates, a moving one a match text. */
	static boolean valid(NpcDatabaseEntry entry) {
		if (entry.id == null || entry.displayName == null || entry.island == null) {
			return false;
		}
		if (entry.fixed) {
			return isFinite(entry.x) && isFinite(entry.y) && isFinite(entry.z);
		}
		return !JsonEntries.isBlank(entry.matchText);
	}

	private static boolean isFinite(Double value) {
		return value != null && Double.isFinite(value);
	}

	/** Entries by id; the first wins if an id repeats. */
	static Map<String, NpcDatabaseEntry> index(List<NpcDatabaseEntry> entries) {
		Map<String, NpcDatabaseEntry> index = new HashMap<>();
		for (NpcDatabaseEntry entry : entries) {
			index.putIfAbsent(entry.id, entry);
		}
		return Map.copyOf(index);
	}

	public static RuleDatabase.State state() {
		return state;
	}

	/** Every entry, in the gist's order (empty until loaded). */
	public static List<NpcDatabaseEntry> entries() {
		return entries;
	}

	/** For gametests, which have no network: the list as if it had been fetched (or had failed). */
	static void useForTest(List<NpcDatabaseEntry> list, RuleDatabase.State newState) {
		fetchStarted.set(true);
		byId = index(list);
		entries = List.copyOf(list);
		state = newState;
	}

	/** The entry with this id, or null if there is none (also while the list is still loading). */
	public static NpcDatabaseEntry byId(String id) {
		return id == null ? null : byId.get(id);
	}

	/** Runs the listener on the client thread each time the list has loaded. */
	public static void onLoaded(Runnable listener) {
		LOADED_LISTENERS.add(listener);
	}

}
