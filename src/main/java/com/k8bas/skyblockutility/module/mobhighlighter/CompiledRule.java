package com.k8bas.skyblockutility.module.mobhighlighter;

import java.util.function.Supplier;
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

	/** Takes a supplier rather than a plain String so a NONE-mode (entity-type-only) rule never
	 *  triggers the nearby-nametag lookup at all — that lookup scans nearby entities and is far
	 *  more expensive than a string compare, so it's only worth paying for when a rule actually
	 *  needs the name. */
	public boolean matchesName(Supplier<String> normalizedName) {
		return switch (rule.nameMatchMode) {
			case NONE -> true;
			case CONTAINS -> rule.namePattern != null && normalizedName.get().contains(rule.namePattern);
			case EXACT -> rule.namePattern != null && normalizedName.get().equals(rule.namePattern);
			case REGEX -> pattern != null && pattern.matcher(normalizedName.get()).find();
		};
	}
}
