package com.k8bas.skyblockutility.module.mobhighlighter;

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
 * Fetched fresh from a public Gist on every startup — deliberately not cached to disk, so
 * entries added to the Gist show up next launch without shipping a mod update. If the fetch
 * hasn't completed yet (or failed), entries()/byIsland() just return empty until it has; the
 * picker section in the settings screen simply shows nothing until the next time it's opened.
 */
public final class MobDatabase {
	private static final String RAW_URL =
			"https://gist.githubusercontent.com/Kesuhi/f68f11d96e15342f36c2402fde8d5ac1/raw/mob_database.json";
	private static final Gson GSON = new Gson();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	private static volatile List<MobDatabaseEntry> entries = List.of();

	private MobDatabase() {
	}

	public static void fetchInBackground() {
		Thread.ofVirtual().name("k8bas-mob-database-fetch").start(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(RAW_URL))
						.header("User-Agent", "Kesuhi/k8bas-skyblock-utility (https://github.com/Kesuhi/K8basSkyblockUtility)")
						.GET()
						.build();
				HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) {
					K8basSkyblockUtilityClient.LOGGER.warn("Mob database fetch got HTTP {}", response.statusCode());
					return;
				}
				List<MobDatabaseEntry> parsed = GSON.fromJson(response.body(), new TypeToken<List<MobDatabaseEntry>>() {
				}.getType());
				entries = parsed != null ? List.copyOf(parsed) : List.of();
				K8basSkyblockUtilityClient.LOGGER.info("Loaded {} mob database entries", entries.size());
			} catch (Exception e) {
				K8basSkyblockUtilityClient.LOGGER.warn("Mob database fetch failed", e);
			}
		});
	}

	public static List<MobDatabaseEntry> entries() {
		return entries;
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
