package com.kesuhi.skyblockhighlighter.highlight;

import com.kesuhi.skyblockhighlighter.config.ConfigManager;
import com.kesuhi.skyblockhighlighter.config.HighlightRule;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * Rules are indexed by entity type so per-frame matching (called from the render
 * thread via EntityRendererMixin) is a map lookup plus a handful of string checks,
 * not a linear scan. The index is only rebuilt when rules actually change.
 */
public final class HighlightManager {
	private static volatile Map<Identifier, List<CompiledRule>> byType = new HashMap<>();
	private static volatile List<CompiledRule> anyType = new ArrayList<>();

	private HighlightManager() {
	}

	public static void rebuild() {
		Map<Identifier, List<CompiledRule>> newByType = new HashMap<>();
		List<CompiledRule> newAnyType = new ArrayList<>();

		for (HighlightRule rule : ConfigManager.get().rules) {
			if (!rule.enabled) {
				continue;
			}

			CompiledRule compiled;
			try {
				compiled = new CompiledRule(rule);
			} catch (PatternSyntaxException e) {
				continue;
			}

			Identifier typeId = (rule.entityTypeId == null || rule.entityTypeId.isBlank())
					? null
					: Identifier.tryParse(rule.entityTypeId);

			if (typeId == null) {
				newAnyType.add(compiled);
			} else {
				newByType.computeIfAbsent(typeId, key -> new ArrayList<>()).add(compiled);
			}
		}

		byType = newByType;
		anyType = newAnyType;
	}

	/** @return packed ARGB outline color, or 0 if the entity shouldn't be outlined. */
	public static int getOutlineColor(Entity entity) {
		if (!ConfigManager.get().modEnabled) {
			return 0;
		}

		String normalizedName = normalizeName(entity);
		Identifier typeId = EntityType.getKey(entity.getType());

		List<CompiledRule> typeRules = byType.get(typeId);
		if (typeRules != null) {
			for (CompiledRule compiled : typeRules) {
				if (compiled.matchesName(normalizedName)) {
					return ARGB.opaque(compiled.rule.color);
				}
			}
		}

		for (CompiledRule compiled : anyType) {
			if (compiled.matchesName(normalizedName)) {
				return ARGB.opaque(compiled.rule.color);
			}
		}

		return 0;
	}

	private static String normalizeName(Entity entity) {
		if (!entity.hasCustomName()) {
			return "";
		}
		// Hypixel bakes §-formatting codes directly into mob name text; strip them so
		// user patterns don't have to account for color/level-prefix/health-suffix noise.
		return entity.getCustomName().getString().replaceAll("§[0-9a-fk-or]", "");
	}
}
