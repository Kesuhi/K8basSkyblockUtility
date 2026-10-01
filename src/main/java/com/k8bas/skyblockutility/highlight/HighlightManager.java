package com.k8bas.skyblockutility.highlight;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.location.IslandTracker;
import net.minecraft.client.Minecraft;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Rules are indexed by entity type so per-frame matching (called from the render thread via
 * EntityRendererMixin) is a map lookup plus a handful of string checks, not a linear scan. The
 * index is only rebuilt when rules actually change.
 *
 * One instance per module (Mob Highlighter, NPC Search's "unfixed" NPCs) rather than a single
 * static singleton, since both modules need the exact same nearby-nametag matching machinery but
 * with independently toggleable enabled state and rule sets. EntityRendererMixin queries every
 * ACTIVE instance for a given entity, using whichever's the first to return a non-zero color.
 */
public final class HighlightManager {
	/** An array, not a List, and rebuilt (not mutated) on registration — instances only ever get
	 *  added (once per module, at most a handful ever), so getOutlineColorFromAny (called ~twice
	 *  per rendered entity per frame) can iterate it directly instead of allocating an Iterator
	 *  every call. Volatile publishes the reference safely across threads without needing a lock
	 *  on the read side. */
	private static volatile HighlightManager[] ACTIVE = new HighlightManager[0];

	private volatile boolean enabled = true;
	private volatile Map<Identifier, List<CompiledRule>> byType = new HashMap<>();
	private volatile List<CompiledRule> anyType = new ArrayList<>();
	/** Optional — fires whenever a rule owned by this instance matches an entity. NPC Search uses
	 *  this for its "You found X" popup; Mob Highlighter leaves it unset. Not deduplicated or
	 *  throttled here (it fires on every matching frame, same as the render mixin calling this
	 *  instance) — that's the listener's job, since only it knows what "a new sighting" should
	 *  mean for its use case. */
	private volatile Consumer<HighlightRule> onMatch;

	/** Nearby-ArmorStand lookups (see resolveNameTag below) are far more expensive than a plain
	 *  field read, so the result is cached per entity per game tick — render fires far more often
	 *  than logic ticks, and the nametag can't change mid-tick anyway. WeakHashMap so despawned
	 *  entities don't pin cache entries forever. Shared across all instances: the same entity's
	 *  nametag is the same regardless of which module is asking. Wrapped as synchronized: it's
	 *  written from wherever EntityRendererMixin/MinecraftMixin's shouldEntityAppearGlowing run,
	 *  and other mods' entity-culling hooks (the exact reason MinecraftMixin exists at all) aren't
	 *  guaranteed to always be the same thread as vanilla's own render path — a bare WeakHashMap
	 *  under concurrent mutation can corrupt its internal state, not just return a stale value. */
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/highlight");
	/** Rule id + reason pairs already logged, so an inactive rule is logged once, not per rebuild. */
	private static final Set<String> LOGGED_INERT = ConcurrentHashMap.newKeySet();
	private volatile Map<String, String> inertRules = Map.of();
	private static final Map<Entity, CachedName> nameTagCache = Collections.synchronizedMap(new WeakHashMap<>());

	public HighlightManager() {
		HighlightManager[] current = ACTIVE;
		HighlightManager[] next = Arrays.copyOf(current, current.length + 1);
		next[current.length] = this;
		ACTIVE = next;
	}

	public void setEnabled(boolean value) {
		enabled = value;
	}

	public void setOnMatchListener(Consumer<HighlightRule> listener) {
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

		for (HighlightRule rule : rules) {
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

			CompiledRule compiled = new CompiledRule(rule);
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

	/** The entity type a rule is indexed under, or null for "any type" (blank or unparsable id). */
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

	/** Queries every active HighlightManager instance (Mob Highlighter, NPC Search) for this
	 *  entity, returning the first non-zero match. Called from EntityRendererMixin. */
	public static int getOutlineColorFromAny(Entity entity) {
		for (HighlightManager manager : ACTIVE) {
			int color = manager.getOutlineColor(entity);
			if (color != 0) {
				return color;
			}
		}
		return 0;
	}

	/** @return packed ARGB outline color, or 0 if the entity shouldn't be outlined. */
	private int getOutlineColor(Entity entity) {
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
		// report it to onMatch, whatever the rules say. Checked every frame, so glow starts as soon
		// as the entity is visible again.
		if (!eligible(entity.isInvisible(), false)) {
			return 0;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		// The local player never glows, also in third person (REQ-GLOW-11).
		if (entity == player) {
			return 0;
		}

		List<CompiledRule> typeRules = byType.get(EntityType.getKey(entity.getType()));
		if ((typeRules == null || typeRules.isEmpty()) && anyType.isEmpty()) {
			// Nothing could possibly match this entity — skip the nametag lookup entirely for
			// entities nobody has a rule for.
			return 0;
		}

		if (typeRules != null) {
			int color = findMatch(typeRules, entity, player);
			if (color != 0) {
				return color;
			}
		}

		return findMatch(anyType, entity, player);
	}

	private int findMatch(List<CompiledRule> candidates, Entity entity, LocalPlayer player) {
		String currentIsland = null;
		boolean currentIslandResolved = false;
		// A real player's own name is never read, so a name-tag match on one can only come from a
		// neighbouring tag (REQ-GLOW-11). Player-type NPCs keep matching.
		boolean realPlayer = entity instanceof Player && isRealPlayerUuid(entity.getUUID());

		for (CompiledRule compiled : candidates) {
			if (realPlayer && NameMatcher.needsName(compiled)) {
				continue;
			}
			// Island gating first, then distance: both are cheap rejects that skip the far more
			// expensive nametag lookup below for rules that can't possibly apply right now.
			if (compiled.rule.island != null) {
				if (!currentIslandResolved) {
					currentIsland = IslandTracker.getCurrentIsland();
					currentIslandResolved = true;
				}
				if (!islandAllows(compiled.rule.island, currentIsland)) {
					continue;
				}
			}

			double maxDistance = effectiveMaxDistance(compiled.rule.maxDistance, ConfigManager.general().mobScanRangeBlocks);
			if (player != null && outOfRange(entity.distanceToSqr(player), maxDistance)) {
				continue;
			}
			// resolveNameTag is only actually called here, inside matchesName, for match modes
			// that need it (NONE-mode/entity-type-only rules never touch it) — and its own
			// per-tick cache means checking it against several rules in the same tick is cheap
			// after the first, so there's no need for this loop to also cache/share it itself.
			if (compiled.matchesName(entity)) {
				Consumer<HighlightRule> listener = onMatch;
				if (listener != null) {
					listener.accept(compiled.rule);
				}
				return ARGB.opaque(compiled.rule.color);
			}
		}
		return 0;
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
