package com.k8bas.skyblockutility.highlight;

import net.minecraft.world.entity.Entity;

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

	/** Takes the entity rather than its resolved name so a NONE-mode (entity-type-only) rule
	 *  never triggers HighlightManager.resolveNameTag at all — that lookup scans nearby entities
	 *  and is far more expensive than a string compare, so it's only worth paying for when a rule
	 *  actually needs the name. resolveNameTag has its own per-tick cache, so calling it from more
	 *  than one CompiledRule for the same entity in the same tick doesn't repeat the expensive
	 *  part — no need for this class to also cache/pass the resolved name around itself. */
	public boolean matchesName(Entity entity) {
		return switch (rule.nameMatchMode) {
			case NONE -> true;
			case CONTAINS -> rule.namePattern != null && HighlightManager.resolveNameTag(entity).contains(rule.namePattern);
			case EXACT -> rule.namePattern != null && HighlightManager.resolveNameTag(entity).equals(rule.namePattern);
			case REGEX -> pattern != null && pattern.matcher(HighlightManager.resolveNameTag(entity)).find();
		};
	}
}
