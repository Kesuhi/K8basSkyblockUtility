package com.k8bas.skyblockutility.ui.option;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A card in one category: a feature with its own toggle and its sub-options (REQ-UI-05), or a plain
 * section of General without a toggle (e.g. Keybinds). A feature with a rule list also has its rules,
 * read each time they are shown, as rules come and go (REQ-UI-11).
 *
 * @param toggle      the feature's own toggle, or null for a section
 * @param ruleSource  the feature's rules now, in list order; none for most cards
 */
public record Card(String id, Category category, String title, Toggle toggle, List<Option> options, Supplier<List<RuleGroup>> ruleSource) {
	public Card {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(title, "title");
		options = List.copyOf(options);
		Objects.requireNonNull(ruleSource, "ruleSource");
	}

	/** A card without a rule list. */
	public Card(String id, Category category, String title, Toggle toggle, List<Option> options) {
		this(id, category, title, toggle, options, List::of);
	}

	/** The toggle first, then the sub-options; the rules' fields are not among them. */
	public List<Option> all() {
		List<Option> all = new ArrayList<>(options.size() + 1);
		if (toggle != null) {
			all.add(toggle);
		}
		all.addAll(options);
		return all;
	}

	/** The rules now. */
	public List<RuleGroup> rules() {
		return ruleSource.get();
	}
}
