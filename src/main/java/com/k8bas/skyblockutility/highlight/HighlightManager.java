package com.k8bas.skyblockutility.highlight;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.location.IslandTracker;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches highlight rules against loaded entities. Matching runs once per client tick on the client
 * thread (REQ-GLOW-06): tick() computes every entity's outline colour and publishes the results as
 * one immutable map, and the render path only looks a colour up. Rules are indexed by entity type
 * so matching is a map lookup plus a handful of string checks per entity, not a linear scan; the
 * index is only rebuilt when rules change.
 *
 * One instance per module (Mob Highlighter, NPC Search's "unfixed" NPCs) rather than a single
 * static singleton, since both modules need the exact same nearby-nametag matching machinery but
 * with independently toggleable enabled state and rule sets. Instances are asked in registration
 * order, so Mob Highlighter's colour wins when both match (REQ-GLOW-08).
 */
public final class HighlightManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/highlight");

	/** An array, not a List, and rebuilt (not mutated) on registration — instances only ever get
	 *  added (once per module, at most a handful ever), so tick() can iterate it directly. Volatile
	 *  publishes the reference safely across threads without needing a lock on the read side. */
	private static volatile HighlightManager[] ACTIVE = new HighlightManager[0];

	/** Outline colour (packed ARGB) per entity id from the last tick; entities without one are absent. */
	private static volatile Int2IntMap colorsByEntityId = Int2IntMaps.EMPTY_MAP;

	/** Rule id + reason pairs already logged, so an inactive rule is logged once, not per rebuild. */
	private static final Set<String> LOGGED_INERT = ConcurrentHashMap.newKeySet();

	/** Nearby-ArmorStand lookups (see resolveNameTag below) are far more expensive than a plain
	 *  field read, so the result is cached per entity per game tick: several rules (and both
	 *  instances) asking about the same entity in one tick resolve it once (EC-GLOW-05). WeakHashMap
	 *  so despawned entities don't pin cache entries forever. Only the client thread touches it. */
	private static final Map<Entity, CachedName> nameTagCache = new WeakHashMap<>();

	/** For tests: name-tag world queries made, and how many of them ran off the client thread. */
	static int nameTagLookups;
	static int offThreadQueries;

	private volatile boolean enabled = true;
	private volatile Map<Identifier, List<CompiledRule>> byType = new HashMap<>();
	private volatile List<CompiledRule> anyType = new ArrayList<>();
	private volatile Map<String, String> inertRules = Map.of();
	/** Optional — fires on the client thread when a rule owned by this instance matches an entity,
	 *  once per tick while it matches. NPC Search uses this for its "You found X" title; Mob
	 *  Highlighter leaves it unset. Not deduplicated here — that's the listener's job, since only it
	 *  knows what "a new sighting" should mean for its use case. */
	private volatile BiConsumer<HighlightRule, Entity> onMatch;

	public HighlightManager() {
		HighlightManager[] current = ACTIVE;
		HighlightManager[] next = Arrays.copyOf(current, current.length + 1);
		next[current.length] = this;
		ACTIVE = next;
	}

	public void setEnabled(boolean value) {
		enabled = value;
	}

	public void setOnMatchListener(BiConsumer<HighlightRule, Entity> listener) {
		onMatch = listener;
	}

	public void rebuild(List<HighlightRule> rules) {
		rebuild(rules, BuiltInRegistries.ENTITY_TYPE::containsKey);
	}

	/** @param knownType whether an entity type id exists (the registry; replaced in tests) */
	void rebuild(List<HighlightRule> rules, Predicate<Identifier> knownType) {
		Map<Identifier, List<CompiledRule>> newByType = new HashMap<>();
		List<CompiledRule> newAnyType = new ArrayList<>();
		Map<String, String> newInert = new HashMap<>();

		for (int order = 0; order < rules.size(); order++) {
			HighlightRule rule = rules.get(order);
			if (!rule.enabled) {
				continue;
			}
			// A rule that can't be evaluated is inert: kept in the file, flagged in the UI, logged
			// once, and never "matches everything" (REQ-GLOW-10).
			String inert = inertReason(rule, knownType);
			if (inert != null) {
				newInert.put(rule.id, inert);
				if (LOGGED_INERT.add(rule.id + "|" + inert)) {
					LOGGER.warn("Highlight rule '{}' is inactive: {}", rule.label, inert);
				}
				continue;
			}

			CompiledRule compiled = new CompiledRule(rule, order);
			Identifier typeId = typeKey(rule.entityTypeId);
			if (typeId == null) {
				newAnyType.add(compiled);
			} else {
				newByType.computeIfAbsent(typeId, key -> new ArrayList<>()).add(compiled);
			}
		}

		byType = newByType;
		anyType = newAnyType;
		inertRules = Map.copyOf(newInert);
	}

	/** Why a rule cannot be evaluated, or null if it can: an empty CONTAINS or EXACT pattern, an
	 *  invalid regex, or an entity type that is not a valid, registered id. */
	static String inertReason(HighlightRule rule, Predicate<Identifier> knownType) {
		NameMatchMode mode = rule.nameMatchMode == null ? NameMatchMode.CONTAINS : rule.nameMatchMode;
		if ((mode == NameMatchMode.CONTAINS || mode == NameMatchMode.EXACT) && (rule.namePattern == null || rule.namePattern.isEmpty())) {
			return "the name pattern is empty";
		}
		if (mode == NameMatchMode.REGEX) {
			if (rule.namePattern == null || rule.namePattern.isEmpty()) {
				return "the regular expression is empty";
			}
			try {
				Pattern.compile(rule.namePattern);
			} catch (PatternSyntaxException e) {
				return "the regular expression is invalid (" + e.getDescription() + ")";
			}
		}
		if (rule.entityTypeId != null && !rule.entityTypeId.isBlank()) {
			Identifier id = Identifier.tryParse(rule.entityTypeId);
			if (id == null || !knownType.test(id)) {
				return "unknown entity type '" + rule.entityTypeId + "'";
			}
		}
		return null;
	}

	/** Inactive rules of the last rebuild, rule id to reason, for the settings UI. */
	public Map<String, String> inertRules() {
		return inertRules;
	}

	/** Real players have version-4 (random) UUIDs; Hypixel's player-shaped NPCs do not. */
	static boolean isRealPlayerUuid(UUID uuid) {
		return uuid.version() == 4;
	}

	/** The entity type a rule is indexed under, or null for "any type" (blank id). */
	static Identifier typeKey(String entityTypeId) {
		return (entityTypeId == null || entityTypeId.isBlank()) ? null : Identifier.tryParse(entityTypeId);
	}

	/** Rules indexed under one entity type, for tests. */
	List<CompiledRule> rulesForType(Identifier typeId) {
		return byType.getOrDefault(typeId, List.of());
	}

	/** Rules that apply to every entity type, for tests. */
	List<CompiledRule> rulesForAnyType() {
		return anyType;
	}

	private boolean hasRules() {
		return enabled && (!anyType.isEmpty() || !byType.isEmpty());
	}

	/** Matches every loaded entity once; registered on the client tick (client thread only). */
	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		HighlightManager[] managers = ACTIVE;
		if (level == null || Arrays.stream(managers).noneMatch(HighlightManager::hasRules)) {
			colorsByEntityId = Int2IntMaps.EMPTY_MAP;
			return;
		}
		Int2IntOpenHashMap colors = new Int2IntOpenHashMap();
		for (Entity entity : level.entitiesForRendering()) {
			for (HighlightManager manager : managers) {
				int color = manager.outlineColor(entity, client.player);
				if (color != 0) {
					colors.put(entity.getId(), color);
					break;
				}
			}
		}
		colorsByEntityId = colors;
	}

	/** The render path: this tick's outline colour for the entity, or 0. Never queries the world.
	 *  The invisibility check is repeated so an entity that just turned invisible never glows. */
	public static int getOutlineColorFromAny(Entity entity) {
		if (entity.isInvisible()) {
			return 0;
		}
		return colorsByEntityId.get(entity.getId());
	}

	/** @return packed ARGB outline color, or 0 if the entity shouldn't be outlined. */
	private int outlineColor(Entity entity, LocalPlayer player) {
		if (!enabled) {
			return 0;
		}
		// ArmorStands are the invisible nametag carriers findNearbyArmorStandName searches for,
		// never a mob/NPC a rule targets by name — without this, every one of them (there's one
		// per nearby nametag on Hypixel) would run its own AABB search looking for a *different*
		// nearby ArmorStand, for a match that could never usefully occur.
		if (entity instanceof ArmorStand) {
			return 0;
		}
		// Hypixel bans displaying invisible entities (REQ-GLOW-03, P2): never outline one and never
		// report it to onMatch, whatever the rules say. Checked every tick, so glow starts as soon
		// as the entity is visible again.
		if (!eligible(entity.isInvisible(), false)) {
			return 0;
		}
		// The local player never glows, also in third person (REQ-GLOW-11).
		if (entity == player) {
			return 0;
		}

		List<CompiledRule> typeRules = byType.getOrDefault(EntityType.getKey(entity.getType()), List.of());
		if (typeRules.isEmpty() && anyType.isEmpty()) {
			// Nothing could possibly match this entity — skip the nametag lookup entirely for
			// entities nobody has a rule for.
			return 0;
		}

		// A real player's own name is never read, so a name-tag match on one can only come from a
		// neighbouring tag (REQ-GLOW-11). Player-type NPCs keep matching.
		boolean realPlayer = entity instanceof Player && isRealPlayerUuid(entity.getUUID());
		String currentIsland = IslandTracker.getCurrentIsland();
		double scanRange = ConfigManager.general().mobScanRangeBlocks;
		CompiledRule match = firstMatch(typeRules, anyType, compiled -> {
			if (realPlayer && NameMatcher.needsName(compiled)) {
				return false;
			}
			// Island gating first, then distance: both are cheap rejects that skip the far more
			// expensive nametag lookup below for rules that can't possibly apply right now.
			if (!islandAllows(compiled.rule.island, currentIsland)) {
				return false;
			}
			double maxDistance = effectiveMaxDistance(compiled.rule.maxDistance, scanRange);
			if (player != null && outOfRange(entity.distanceToSqr(player), maxDistance)) {
				return false;
			}
			// resolveNameTag is only called inside matchesName, for match modes that need it, and
			// its per-tick cache makes repeat calls for the same entity cheap.
			return compiled.matchesName(entity);
		});
		if (match == null) {
			return 0;
		}
		BiConsumer<HighlightRule, Entity> listener = onMatch;
		if (listener != null) {
			listener.accept(match.rule, entity);
		}
		return ARGB.opaque(match.rule.color);
	}

	/** The first rule, in the module's list order, that applies (REQ-GLOW-08). Both lists are in
	 *  list order already; this walks them as one, so an any-type rule above a type rule wins. */
	static CompiledRule firstMatch(List<CompiledRule> typeRules, List<CompiledRule> anyTypeRules, Predicate<CompiledRule> applies) {
		int i = 0;
		int j = 0;
		while (i < typeRules.size() || j < anyTypeRules.size()) {
			CompiledRule next;
			if (j >= anyTypeRules.size() || (i < typeRules.size() && typeRules.get(i).order < anyTypeRules.get(j).order)) {
				next = typeRules.get(i++);
			} else {
				next = anyTypeRules.get(j++);
			}
			if (applies.test(next)) {
				return next;
			}
		}
		return null;
	}

	/** Whether an entity may be outlined at all. Only the invisibility flag counts: an invisible mob
	 *  stays ineligible even while its armour is drawn (wearsVisibleArmour is ignored on purpose). */
	static boolean eligible(boolean invisible, boolean wearsVisibleArmour) {
		return !invisible;
	}

	/** A rule restricted to an island applies only there; an unrestricted rule (null) applies everywhere. */
	static boolean islandAllows(String ruleIsland, String currentIsland) {
		return ruleIsland == null || ruleIsland.equals(currentIsland);
	}

	/** The tighter of the rule's own limit (if any) and the General "scan range" cap (if any). */
	static double effectiveMaxDistance(double ruleMaxDistance, double globalLimit) {
		double ruleLimit = ruleMaxDistance > 0 ? ruleMaxDistance : Double.POSITIVE_INFINITY;
		return Math.min(ruleLimit, globalLimit > 0 ? globalLimit : Double.POSITIVE_INFINITY);
	}

	/** An infinite limit never excludes; otherwise the entity must be within the limit. */
	static boolean outOfRange(double distanceSq, double maxDistance) {
		return Double.isFinite(maxDistance) && distanceSq > maxDistance * maxDistance;
	}

	/** Hypixel Skyblock mobs almost never carry their visible name as their own CustomName —
	 *  what you see floating above the mob is a separate, invisible ArmorStand entity riding
	 *  near it (confirmed against how other established Skyblock mods, e.g. SkyHanni's
	 *  EntityUtils, read mob nametags: by scanning for a nearby ArmorStand, not the mob's own
	 *  name). So the mob's own hasCustomName()/getCustomName() is checked only as a fallback,
	 *  for the rare entity that genuinely carries its own name. */
	static String resolveNameTag(Entity entity) {
		long tick = entity.tickCount;
		CachedName cached = nameTagCache.get(entity);
		if (cached != null && cached.tick == tick) {
			return cached.name;
		}

		String name = findNearbyArmorStandName(entity);
		if (name == null && entity.hasCustomName()) {
			name = entity.getCustomName().getString();
		}
		String normalized = name == null ? "" : NameMatcher.stripColorCodes(name);

		nameTagCache.put(entity, new CachedName(tick, normalized));
		return normalized;
	}

	private static String findNearbyArmorStandName(Entity entity) {
		nameTagLookups++;
		if (!Minecraft.getInstance().isSameThread()) {
			offThreadQueries++;
		}
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

	private record CachedName(long tick, String name) {
	}
}
