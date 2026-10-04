package com.k8bas.skyblockutility.ui.option;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The settings search (REQ-UI-08): a case-insensitive substring match over each option's title,
 * description, keywords and dropdown labels (not its tooltip), with hit counts per category for the
 * sidebar badges. Built from the same declarations as the cards (REQ-UI-07), plus each rule's label and
 * name pattern (REQ-UI-09).
 */
public final class SearchIndex {
	public static final int MAX_QUERY_LENGTH = 35;

	/** One searchable thing: an option, or later a rule. Its fields are stored lower-case. */
	public record Entry(Category category, String cardId, String id, List<String> fields) {
		public Entry {
			Objects.requireNonNull(category, "category");
			Objects.requireNonNull(cardId, "cardId");
			Objects.requireNonNull(id, "id");
			fields = fields.stream().map(field -> field.toLowerCase(Locale.ROOT)).toList();
		}
	}

	/** The hits for a query, in index order, and their count per category. */
	public record Result(String query, List<Entry> hits, Map<Category, Integer> counts) {
		public int count(Category category) {
			return counts.getOrDefault(category, 0);
		}

		/** The categories with at least one hit, in screen order. */
		public List<Category> categoriesWithHits() {
			List<Category> categories = new ArrayList<>();
			for (Category category : Category.values()) {
				if (count(category) > 0) {
					categories.add(category);
				}
			}
			return categories;
		}
	}

	private final List<Entry> entries;

	private SearchIndex(List<Entry> entries) {
		this.entries = List.copyOf(entries);
	}

	public static SearchIndex of(List<Card> cards) {
		List<Entry> entries = new ArrayList<>();
		for (Card card : cards) {
			for (Option option : card.all()) {
				List<String> fields = new ArrayList<>();
				fields.add(option.text().title());
				fields.add(option.text().description());
				fields.addAll(option.text().keywords());
				fields.addAll(option.searchLabels());
				entries.add(new Entry(card.category(), card.id(), option.id(), fields));
			}
			// Rules by their label and name pattern (REQ-UI-09), each its own entry, so two with one label are both found.
			for (RuleGroup rule : card.rules()) {
				entries.add(new Entry(card.category(), card.id(), rule.id(), rule.searchFields().get()));
			}
		}
		return new SearchIndex(entries);
	}

	/** The box keeps at most 35 typed characters; then surrounding spaces go and case is ignored. */
	public static String normalize(String raw) {
		String typed = raw.length() > MAX_QUERY_LENGTH ? raw.substring(0, MAX_QUERY_LENGTH) : raw;
		return typed.strip().toLowerCase(Locale.ROOT);
	}

	/** An empty query matches everything. */
	public Result search(String raw) {
		String query = normalize(raw);
		List<Entry> hits = new ArrayList<>();
		Map<Category, Integer> counts = new EnumMap<>(Category.class);
		for (Entry entry : entries) {
			if (query.isEmpty() || entry.fields().stream().anyMatch(field -> field.contains(query))) {
				hits.add(entry);
				counts.merge(entry.category(), 1, Integer::sum);
			}
		}
		return new Result(query, List.copyOf(hits), counts);
	}
}
