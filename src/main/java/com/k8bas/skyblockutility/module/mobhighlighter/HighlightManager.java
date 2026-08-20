package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
 *
 * Driven entirely by MobHighlighterModule: the "enabled" flag mirrors the module's
 * config so this class doesn't need to know about ConfigManager or Module at all.
 */
public final class HighlightManager {
	private static volatile boolean enabled = true;
	private static volatile Map<Identifier, List<CompiledRule>> byType = new HashMap<>();
	private static volatile List<CompiledRule> anyType = new ArrayList<>();

	private HighlightManager() {
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static void rebuild(List<HighlightRule> rules) {
		Map<Identifier, List<CompiledRule>> newByType = new HashMap<>();
		List<CompiledRule> newAnyType = new ArrayList<>();

		for (HighlightRule rule : rules) {
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
		if (!enabled) {
			return 0;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		String normalizedName = normalizeName(entity);
		Identifier typeId = EntityType.getKey(entity.getType());

		List<CompiledRule> typeRules = byType.get(typeId);
		if (typeRules != null) {
			int color = findMatch(typeRules, entity, normalizedName, player);
			if (color != 0) {
				return color;
			}
		}

		return findMatch(anyType, entity, normalizedName, player);
	}

	private static int findMatch(List<CompiledRule> candidates, Entity entity, String normalizedName, LocalPlayer player) {
		for (CompiledRule compiled : candidates) {
			// Distance check first: cheaper than the string/regex match below.
			double maxDistance = effectiveMaxDistance(compiled.rule.maxDistance);
			if (player != null && Double.isFinite(maxDistance) && entity.distanceToSqr(player) > maxDistance * maxDistance) {
				continue;
			}
			if (compiled.matchesName(normalizedName)) {
				return ARGB.opaque(compiled.rule.color);
			}
		}
		return 0;
	}

	/** The tighter of the rule's own limit (if any) and the General "scan range" cap (if any). */
	private static double effectiveMaxDistance(double ruleMaxDistance) {
		double ruleLimit = ruleMaxDistance > 0 ? ruleMaxDistance : Double.POSITIVE_INFINITY;
		double globalLimit = ConfigManager.general().mobScanRangeBlocks;
		return Math.min(ruleLimit, globalLimit > 0 ? globalLimit : Double.POSITIVE_INFINITY);
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
