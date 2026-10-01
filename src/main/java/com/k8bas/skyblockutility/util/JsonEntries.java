package com.k8bas.skyblockutility.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reads a JSON array of entries one element at a time, so one malformed entry is skipped instead of
 * failing the whole list (REQ-NPCDB-05).
 */
public final class JsonEntries {
	private static final Gson GSON = new Gson();

	private JsonEntries() {
	}

	/** @param entries the valid entries, in document order
	 *  @param skipped elements that were null, not an object of the type, or not valid */
	public record Parsed<T>(List<T> entries, int skipped) {
	}

	/** An empty or null document gives no entries; a document that is not an array throws
	 *  JsonSyntaxException. */
	public static <T> Parsed<T> parse(String json, Class<T> type, Predicate<T> valid) {
		JsonElement root = json == null ? null : JsonParser.parseString(json);
		if (root == null || root.isJsonNull()) {
			return new Parsed<>(List.of(), 0);
		}
		if (!root.isJsonArray()) {
			throw new JsonSyntaxException("expected a JSON array of entries");
		}
		JsonArray array = root.getAsJsonArray();
		List<T> entries = new ArrayList<>();
		int skipped = 0;
		for (JsonElement element : array) {
			T entry;
			try {
				entry = element.isJsonObject() ? GSON.fromJson(element, type) : null;
			} catch (JsonParseException | IllegalStateException | NumberFormatException e) {
				entry = null;
			}
			if (entry != null && valid.test(entry)) {
				entries.add(entry);
			} else {
				skipped++;
			}
		}
		return new Parsed<>(List.copyOf(entries), skipped);
	}

	public static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
