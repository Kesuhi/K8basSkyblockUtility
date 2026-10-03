package com.k8bas.skyblockutility.ui.option;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A card in one category: a feature with its own toggle and its sub-options (REQ-UI-05), or a plain
 * section of General without a toggle (e.g. Keybinds).
 *
 * @param toggle the feature's own toggle, or null for a section
 */
public record Card(String id, Category category, String title, Toggle toggle, List<Option> options) {
	public Card {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(title, "title");
		options = List.copyOf(options);
	}

	/** The toggle first, then the sub-options. */
	public List<Option> all() {
		List<Option> all = new ArrayList<>(options.size() + 1);
		if (toggle != null) {
			all.add(toggle);
		}
		all.addAll(options);
		return all;
	}
}
