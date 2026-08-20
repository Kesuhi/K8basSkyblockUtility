package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
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

	/** Nearby-ArmorStand lookups (see resolveNameTag below) are far more expensive than a plain
	 *  field read, so the result is cached per entity per game tick — render fires far more often
	 *  than logic ticks, and the nametag can't change mid-tick anyway. WeakHashMap so despawned
	 *  entities don't pin cache entries forever. */
	private static final Map<Entity, CachedName> nameTagCache = new WeakHashMap<>();

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

		List<CompiledRule> typeRules = byType.get(EntityType.getKey(entity.getType()));
		if ((typeRules == null || typeRules.isEmpty()) && anyType.isEmpty()) {
			// Nothing could possibly match this entity — skip building the lazy name supplier
			// entirely so entities nobody has a rule for never pay for a nametag lookup.
			return 0;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		Supplier<String> nameTag = () -> resolveNameTag(entity);

		if (typeRules != null) {
			int color = findMatch(typeRules, entity, nameTag, player);
			if (color != 0) {
				return color;
			}
		}

		return findMatch(anyType, entity, nameTag, player);
	}

	private static int findMatch(List<CompiledRule> candidates, Entity entity, Supplier<String> nameTag, LocalPlayer player) {
		for (CompiledRule compiled : candidates) {
			// Distance check first: cheaper than the string/regex match below.
			double maxDistance = effectiveMaxDistance(compiled.rule.maxDistance);
			if (player != null && Double.isFinite(maxDistance) && entity.distanceToSqr(player) > maxDistance * maxDistance) {
				continue;
			}
			if (compiled.matchesName(nameTag)) {
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

	/** Hypixel Skyblock mobs almost never carry their visible name as their own CustomName —
	 *  what you see floating above the mob is a separate, invisible ArmorStand entity riding
	 *  near it (confirmed against how other established Skyblock mods, e.g. SkyHanni's
	 *  EntityUtils, read mob nametags: by scanning for a nearby ArmorStand, not the mob's own
	 *  name). So the mob's own hasCustomName()/getCustomName() is checked only as a fallback,
	 *  for the rare entity that genuinely carries its own name. */
	private static String resolveNameTag(Entity entity) {
		long tick = entity.tickCount;
		CachedName cached = nameTagCache.get(entity);
		if (cached != null && cached.tick == tick) {
			return cached.name;
		}

		String name = findNearbyArmorStandName(entity);
		if (name == null && entity.hasCustomName()) {
			name = entity.getCustomName().getString();
		}
		String normalized = name == null ? "" : stripColorCodes(name);

		nameTagCache.put(entity, new CachedName(tick, normalized));
		return normalized;
	}

	private static String findNearbyArmorStandName(Entity entity) {
		Level level = entity.level();
		double halfWidth = entity.getBbWidth() / 2.0 + 1.0;
		AABB searchBox = new AABB(
				entity.getX() - halfWidth, entity.getY() - 0.5, entity.getZ() - halfWidth,
				entity.getX() + halfWidth, entity.getY() + entity.getBbHeight() + 2.5, entity.getZ() + halfWidth);

		ArmorStand nearest = null;
		double nearestDistSq = Double.MAX_VALUE;
		for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, searchBox)) {
			if (!stand.hasCustomName()) {
				continue;
			}
			double distSq = stand.distanceToSqr(entity);
			if (distSq < nearestDistSq) {
				nearestDistSq = distSq;
				nearest = stand;
			}
		}
		return nearest != null ? nearest.getCustomName().getString() : null;
	}

	// Hypixel bakes §-formatting codes directly into mob name text; strip them so user patterns
	// don't have to account for color/level-prefix/health-suffix noise.
	private static String stripColorCodes(String raw) {
		return raw.replaceAll("§[0-9a-fk-or]", "");
	}

	private record CachedName(long tick, String name) {
	}
}
