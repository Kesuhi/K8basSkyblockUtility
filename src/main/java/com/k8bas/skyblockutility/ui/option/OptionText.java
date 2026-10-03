package com.k8bas.skyblockutility.ui.option;

import java.util.List;
import java.util.Objects;

/**
 * What an option says, declared once for its card and the search index (REQ-UI-07).
 *
 * @param title       the card or row title; searched
 * @param description at most two card lines; searched
 * @param tooltip     the full text on hover, "\n" for a line break, empty for none; not searched
 * @param keywords    hidden search words (e.g. "gambling" for Rare Drop Odds, REQ-XC-RULES-07)
 */
public record OptionText(String title, String description, String tooltip, List<String> keywords) {
	public OptionText {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(tooltip, "tooltip");
		keywords = List.copyOf(keywords);
		if (title.isBlank()) {
			throw new IllegalArgumentException("an option needs a title");
		}
	}
}
