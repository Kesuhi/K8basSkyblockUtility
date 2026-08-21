package com.k8bas.skyblockutility.module.npcsearch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.net.SharedHttpClient;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetched fresh from a public Gist — deliberately not cached to disk, mirroring MobDatabase's
 * approach, so entries added to the Gist show up next launch without a mod update.
 *
 * Fetched lazily (on first fetchIfNeeded() call, not at module registration) since most sessions
 * never open the picker at all — see MobDatabase's javadoc for the full reasoning.
 */
public final class NpcDatabase {
	private static final String RAW_URL =
			"https://gist.githubusercontent.com/Kesuhi/7234bbe0e2f587abd2ada774bb88104f/raw/npc_database.json";
	private static final Gson GSON = new Gson();
	private static final AtomicBoolean fetchStarted = new AtomicBoolean(false);

	private static volatile List<NpcDatabaseEntry> entries = List.of();

	private NpcDatabase() {
	}

	/** Safe to call every time the picker is opened — only actually starts the fetch once. */
	public static void fetchIfNeeded() {
		if (!fetchStarted.compareAndSet(false, true)) {
			return;
		}
		Thread.ofVirtual().name("k8bas-npc-database-fetch").start(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(RAW_URL))
						.header("User-Agent", "Kesuhi/k8bas-skyblock-utility (https://github.com/Kesuhi/K8basSkyblockUtility)")
						.GET()
						.build();
				HttpResponse<String> response = SharedHttpClient.get().send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) {
					K8basSkyblockUtilityClient.LOGGER.warn("NPC database fetch got HTTP {}", response.statusCode());
					return;
				}
				List<NpcDatabaseEntry> parsed = GSON.fromJson(response.body(), new TypeToken<List<NpcDatabaseEntry>>() {
				}.getType());
				entries = parsed != null ? List.copyOf(parsed) : List.of();
				K8basSkyblockUtilityClient.LOGGER.info("Loaded {} NPC database entries", entries.size());
			} catch (Exception e) {
				K8basSkyblockUtilityClient.LOGGER.warn("NPC database fetch failed", e);
			}
		});
	}

	/** Grouped by island, entries within each island sorted by display name, islands sorted alphabetically. */
	public static Map<String, List<NpcDatabaseEntry>> byIsland() {
		Map<String, List<NpcDatabaseEntry>> grouped = new TreeMap<>();
		for (NpcDatabaseEntry entry : entries) {
			grouped.computeIfAbsent(entry.island, key -> new ArrayList<>()).add(entry);
		}
		for (List<NpcDatabaseEntry> group : grouped.values()) {
			group.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
		}
		return Collections.unmodifiableMap(grouped);
	}
}
