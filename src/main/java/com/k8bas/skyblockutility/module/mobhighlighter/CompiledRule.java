package com.k8bas.skyblockutility.module.mobhighlighter;

import java.util.regex.Pattern;

public final class CompiledRule {
	public final HighlightRule rule;
	private final Pattern pattern;

	public CompiledRule(HighlightRule rule) {
		this.rule = rule;
		if (rule.nameMatchMode == NameMatchMode.REGEX && rule.namePattern != null && !rule.namePattern.isEmpty()) {
			this.pattern = Pattern.compile(rule.namePattern);
		} else {
			this.pattern = null;
		}
	}

	public boolean matchesName(String normalizedName) {
		return switch (rule.nameMatchMode) {
			case NONE -> true;
			case CONTAINS -> rule.namePattern != null && normalizedName.contains(rule.namePattern);
			case EXACT -> rule.namePattern != null && normalizedName.equals(rule.namePattern);
			case REGEX -> pattern != null && pattern.matcher(normalizedName).find();
		};
	}
}
