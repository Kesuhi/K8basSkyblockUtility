package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.SearchIndex;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the settings screen shows for a query (REQ-UI-08), worked out from the search index: the
 * categories with a hit (all of them for an empty query), each one's badge (its hit count), the
 * category to show (the current one while it has a hit, else the first that has one), and which
 * options show. With no hit anywhere the sidebar is empty and the content says so.
 */
record SearchView(SearchIndex.Result result, String typed, List<Category> categories, Category selected, Set<String> hits) {
	static SearchView of(SearchIndex index, List<Category> all, String raw, Category current) {
		SearchIndex.Result result = index.search(raw);
		boolean filtering = !result.query().isEmpty();
		List<Category> categories = filtering ? result.categoriesWithHits().stream().filter(all::contains).toList() : List.copyOf(all);
		Category selected = categories.isEmpty() || categories.contains(current) ? current : categories.get(0);
		Set<String> hits = new HashSet<>();
		result.hits().forEach(entry -> hits.add(entry.id()));
		// For the message: what was typed (its first 35 characters), trimmed but in its own case.
		String typed = (raw.length() > SearchIndex.MAX_QUERY_LENGTH ? raw.substring(0, SearchIndex.MAX_QUERY_LENGTH) : raw).strip();
		return new SearchView(result, typed, categories, selected, Set.copyOf(hits));
	}

	boolean filtering() {
		return !result.query().isEmpty();
	}

	boolean nothingFound() {
		return filtering() && categories.isEmpty();
	}

	int badge(Category category) {
		return result.count(category);
	}

	boolean shows(Option option) {
		return !filtering() || hits.contains(option.id());
	}

	/** The content's text when nothing matches; the query as typed, trimmed. */
	String message() {
		return "No settings found for \"" + typed + "\"";
	}
}
