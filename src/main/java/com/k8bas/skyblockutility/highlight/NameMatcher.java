package com.k8bas.skyblockutility.highlight;

import java.util.regex.Pattern;

/**
 * The name test of a highlight rule, on an already-resolved name — no entity, world or nametag
 * lookup, so it can be unit-tested without a running game. CompiledRule decides whether the
 * (expensive) nametag lookup is needed at all before calling this.
 */
public final class NameMatcher {
	private static final Pattern COLOR_CODES = Pattern.compile("§[0-9a-fk-or]");

	private NameMatcher() {
	}

	/** Whether the rule's result depends on the entity's name. NONE-mode rules and rules with no
	 *  pattern give the same answer for every name, so their callers skip the nametag lookup. */
	static boolean needsName(CompiledRule compiled) {
		return switch (compiled.rule.nameMatchMode) {
			case NONE -> false;
			case CONTAINS, EXACT -> compiled.rule.namePattern != null;
			case REGEX -> compiled.pattern != null;
		};
	}

	public static boolean matches(CompiledRule compiled, String name) {
		HighlightRule rule = compiled.rule;
		return switch (rule.nameMatchMode) {
			case NONE -> true;
			case CONTAINS -> rule.namePattern != null && name.contains(rule.namePattern);
			case EXACT -> rule.namePattern != null && name.equals(rule.namePattern);
			case REGEX -> compiled.pattern != null && compiled.pattern.matcher(name).find();
		};
	}

	// Hypixel bakes §-formatting codes directly into mob name text; strip them so user patterns
	// don't have to account for color/level-prefix/health-suffix noise. String#replaceAll
	// compiles its regex fresh on every call — COLOR_CODES is compiled once instead, and names
	// with no color codes at all (the common case for a plain nametag ArmorStand) skip the
	// matcher entirely.
	public static String stripColorCodes(String raw) {
		if (raw.indexOf('§') < 0) {
			return raw;
		}
		return COLOR_CODES.matcher(raw).replaceAll("");
	}
}
