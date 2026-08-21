package com.k8bas.skyblockutility.module.mobhighlighter;

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
 * Fetched fresh from a public Gist — deliberately not cached to disk, so entries added to the
 * Gist show up next launch without shipping a mod update. If the fetch hasn't completed yet (or
 * failed), byIsland() just returns empty until it has; the picker section in the settings screen
 * simply shows nothing until the next time it's opened.
 *
 * Fetched lazily (on first fetchIfNeeded() call, not at module registration) since most sessions
 * never open the picker at all — no reason to spend a request and a Gson parse of 300+ entries on
 * every single launch for data most of the time nobody looks at.
 */
public final class MobDatabase {
	private static final String RAW_URL =
			"https://gist.githubusercontent.com/Kesuhi/f68f11d96e15342f36c2402fde8d5ac1/raw/mob_database.json";
	private static final Gson GSON = new Gson();
	private static final AtomicBoolean fetchStarted = new AtomicBoolean(false);

	private static volatile List<MobDatabaseEntry> entries = List.of();

	private MobDatabase() {
	}

	/** Safe to call every time the picker is opened — only actually starts the fetch once. */
	public static void fetchIfNeeded() {
		if (!fetchStarted.compareAndSet(false, true)) {
			return;
		}
		Thread.ofVirtual().name("k8bas-mob-database-fetch").start(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(RAW_URL))
						.header("User-Agent", "Kesuhi/k8bas-skyblock-utility (https://github.com/Kesuhi/K8basSkyblockUtility)")
						.GET()
						.build();
				HttpResponse<String> response = SharedHttpClient.get().send(request, HttpResponse.BodyHandlers.ofString());
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
