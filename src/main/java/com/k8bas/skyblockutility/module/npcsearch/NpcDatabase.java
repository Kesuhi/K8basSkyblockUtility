package com.k8bas.skyblockutility.module.npcsearch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fetched fresh from a public Gist on every startup — deliberately not cached to disk, mirroring
 * MobDatabase's approach, so entries added to the Gist show up next launch without a mod update.
 */
public final class NpcDatabase {
	private static final String RAW_URL =
			"https://gist.githubusercontent.com/Kesuhi/7234bbe0e2f587abd2ada774bb88104f/raw/npc_database.json";
	private static final Gson GSON = new Gson();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	private static volatile List<NpcDatabaseEntry> entries = List.of();

	private NpcDatabase() {
	}

	public static void fetchInBackground() {
		Thread.ofVirtual().name("k8bas-npc-database-fetch").start(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(RAW_URL))
						.header("User-Agent", "Kesuhi/k8bas-skyblock-utility (https://github.com/Kesuhi/K8basSkyblockUtility)")
						.GET()
						.build();
				HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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

	public static List<NpcDatabaseEntry> entries() {
		return entries;
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
