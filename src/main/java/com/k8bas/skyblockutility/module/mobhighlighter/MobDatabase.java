package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.net.SharedHttpClient;
import com.k8bas.skyblockutility.util.JsonEntries;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetched fresh from a public Gist — deliberately not cached to disk, so entries added to the
 * Gist show up next launch without shipping a mod update. If the fetch hasn't completed yet (or
 * failed), byIsland() just returns empty until it has; the picker section in the settings screen
 * simply shows nothing until the next time it's opened.
 *
 * Fetched at module registration (mod startup), not lazily on first picker-open — opening the
 * picker for the first time in a session should never show the "still loading" message if it can
 * be helped, so the fetch gets a head start before the player is likely to have opened it.
 */
public final class MobDatabase {
	private static final String RAW_URL =
			"https://gist.githubusercontent.com/Kesuhi/f68f11d96e15342f36c2402fde8d5ac1/raw/mob_database.json";
	private static final AtomicBoolean fetchStarted = new AtomicBoolean(false);

	private static volatile List<MobDatabaseEntry> entries = List.of();

	private MobDatabase() {
	}

	/** Idempotent — only actually starts the fetch the first time this is called. */
	public static void fetchIfNeeded() {
		if (!fetchStarted.compareAndSet(false, true)) {
			return;
		}
		Thread.ofVirtual().name("k8bas-mob-database-fetch").start(() -> {
			String body = SharedHttpClient.fetchText(URI.create(RAW_URL), "Mob database fetch", K8basSkyblockUtilityClient.LOGGER::warn);
			if (body == null) {
				return;
			}
			try {
				entries = parse(body);
				K8basSkyblockUtilityClient.LOGGER.info("Loaded {} mob database entries", entries.size());
			} catch (RuntimeException e) {
				K8basSkyblockUtilityClient.LOGGER.warn("Mob database is not a list of entries: {}", e.toString());
			}
		});
	}

	/** The gist's JSON array as an immutable list; an empty document gives an empty list, and
	 *  malformed entries are skipped with one warning (REQ-NPCDB-05). */
	static List<MobDatabaseEntry> parse(String json) {
		JsonEntries.Parsed<MobDatabaseEntry> parsed = JsonEntries.parse(json, MobDatabaseEntry.class, MobDatabase::valid);
		if (parsed.skipped() > 0) {
			K8basSkyblockUtilityClient.LOGGER.warn("Skipped {} malformed mob database entries", parsed.skipped());
		}
		return parsed.entries();
	}

	/** An id, a name, an island and a match text. */
	static boolean valid(MobDatabaseEntry entry) {
		return entry.id != null && entry.displayName != null && entry.island != null && !JsonEntries.isBlank(entry.matchText);
	}

	/** Grouped by island, entries within each island sorted by display name, islands sorted alphabetically. */
	public static Map<String, List<MobDatabaseEntry>> byIsland() {
		Map<String, List<MobDatabaseEntry>> grouped = new TreeMap<>();
		for (MobDatabaseEntry entry : entries) {
			grouped.computeIfAbsent(entry.island, key -> new ArrayList<>()).add(entry);
		}
		for (List<MobDatabaseEntry> group : grouped.values()) {
			group.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
		}
		return Collections.unmodifiableMap(grouped);
	}
}
