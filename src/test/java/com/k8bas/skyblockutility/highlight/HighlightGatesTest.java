package com.k8bas.skyblockutility.highlight;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterization of the 1.0.1 island, distance and entity-type gates (AC-GLOW-01). */
class HighlightGatesTest {
	private static final double INF = Double.POSITIVE_INFINITY;

	/** AC-GLOW-03 [A]: invisible entities are never eligible, also when they wear visible armour. */
	@Test
	void invisibleEntitiesAreNeverEligible() {
		assertFalse(HighlightManager.eligible(true, false), "invisible");
		assertFalse(HighlightManager.eligible(true, true), "invisible with visible armour");
		assertTrue(HighlightManager.eligible(false, false), "visible");
		assertTrue(HighlightManager.eligible(false, true), "visible with armour");
	}

	@Test
	void islandGate() {
		assertTrue(HighlightManager.islandAllows(null, "Hub"));
		assertTrue(HighlightManager.islandAllows(null, null));
		assertTrue(HighlightManager.islandAllows("Hub", "Hub"));
		assertFalse(HighlightManager.islandAllows("Hub", "Dungeon Hub"));
		assertFalse(HighlightManager.islandAllows("Catacombs", null));
	}

	@Test
	void distanceLimitIsTheTighterOfRuleAndScanRange() {
		assertEquals(INF, HighlightManager.effectiveMaxDistance(0, 0));
		assertEquals(INF, HighlightManager.effectiveMaxDistance(-5, -1));
		assertEquals(64, HighlightManager.effectiveMaxDistance(0, 64));
		assertEquals(30, HighlightManager.effectiveMaxDistance(30, 64));
		assertEquals(64, HighlightManager.effectiveMaxDistance(100, 64));
		assertEquals(10, HighlightManager.effectiveMaxDistance(10, 0));
	}

	@Test
	void distanceGateIncludesTheBoundary() {
		assertFalse(HighlightManager.outOfRange(1e12, INF));
		assertFalse(HighlightManager.outOfRange(64 * 64, 64));
		assertTrue(HighlightManager.outOfRange(64 * 64 + 0.01, 64));
	}

	@Test
	void entityTypeKey() {
		assertNull(HighlightManager.typeKey(null));
		assertNull(HighlightManager.typeKey("  "));
		assertNull(HighlightManager.typeKey("Not A Type!"));
		assertEquals("minecraft:zombie", HighlightManager.typeKey("minecraft:zombie").toString());
		assertEquals("minecraft:zombie", HighlightManager.typeKey("zombie").toString());
	}

	private static final Predicate<Identifier> KNOWN = id -> List.of("minecraft:zombie", "minecraft:player").contains(id.toString());

	@Test
	void rebuildIndexesEnabledValidRulesByType() {
		HighlightManager manager = new HighlightManager();
		manager.rebuild(List.of(
				rule("minecraft:zombie", NameMatchMode.CONTAINS, "Zombie", true),
				rule("minecraft:zombie", NameMatchMode.CONTAINS, "Off", false),
				rule("", NameMatchMode.CONTAINS, "Trinity", true),
				rule("minecraft:player", NameMatchMode.NONE, "", true)), KNOWN);

		List<CompiledRule> zombie = manager.rulesForType(Identifier.tryParse("minecraft:zombie"));
		assertEquals(1, zombie.size());
		assertEquals("Zombie", zombie.get(0).rule.namePattern);
		assertEquals(List.of("Trinity"), manager.rulesForAnyType().stream().map(c -> c.rule.namePattern).toList());
		assertEquals(1, manager.rulesForType(Identifier.tryParse("minecraft:player")).size());
		assertTrue(manager.inertRules().isEmpty());
	}

	/** AC-GLOW-09 / EC-CFG-09 (T1.8b): rules that can't be evaluated are inert, never "match everything". */
	@Test
	void rulesThatCannotBeEvaluatedAreInert() {
		HighlightRule emptyContains = rule(null, NameMatchMode.CONTAINS, "", true);
		HighlightRule emptyExact = rule(null, NameMatchMode.EXACT, "", true);
		HighlightRule badRegex = rule(null, NameMatchMode.REGEX, "([", true);
		HighlightRule typo = rule("minecraft:zombi", NameMatchMode.CONTAINS, "Zombie", true);
		HighlightRule badSyntax = rule("Not A Type!", NameMatchMode.EXACT, "Duncan", true);
		List<HighlightRule> rules = List.of(emptyContains, emptyExact, badRegex, typo, badSyntax);

		HighlightManager manager = new HighlightManager();
		manager.rebuild(rules, KNOWN);
		manager.rebuild(rules, KNOWN);

		assertTrue(manager.rulesForAnyType().isEmpty(), "no inert rule ends up as an any-type rule");
		assertTrue(manager.rulesForType(Identifier.tryParse("minecraft:zombi")).isEmpty());
		assertEquals(5, manager.inertRules().size());
		assertTrue(manager.inertRules().get(badRegex.id).contains("invalid"));
		assertTrue(manager.inertRules().get(typo.id).contains("minecraft:zombi"));
		assertEquals("([", badRegex.namePattern, "the rule itself is kept unchanged");
	}

	@Test
	void inertReasons() {
		assertNull(HighlightManager.inertReason(rule("minecraft:zombie", NameMatchMode.CONTAINS, "Zombie", true), KNOWN));
		// Decision 2026-10-01 (G1, S-6): a rule that ignores names needs an entity type, otherwise it
		// would outline every visible entity, players included.
		assertEquals("a rule that ignores names needs an entity type",
				HighlightManager.inertReason(rule(null, NameMatchMode.NONE, "", true), KNOWN));
		assertEquals("a rule that ignores names needs an entity type",
				HighlightManager.inertReason(rule("  ", NameMatchMode.NONE, "Zealot", true), KNOWN));
		assertNull(HighlightManager.inertReason(rule("minecraft:zombie", NameMatchMode.NONE, "", true), KNOWN));
		assertNull(HighlightManager.inertReason(rule("zombie", NameMatchMode.REGEX, "^Z", true), KNOWN));
		assertEquals("the name pattern is empty", HighlightManager.inertReason(rule(null, NameMatchMode.CONTAINS, null, true), KNOWN));
		assertEquals("the regular expression is empty", HighlightManager.inertReason(rule(null, NameMatchMode.REGEX, "", true), KNOWN));
	}

	/** AC-GLOW-07 [A] (T1.10a): within a module the first matching rule in list order wins, also
	 *  when an any-type rule is listed above a type rule. */
	@Test
	void theFirstMatchingRuleInListOrderWins() {
		CompiledRule anyFirst = new CompiledRule(rule(null, NameMatchMode.CONTAINS, "A", true), 0);
		CompiledRule typeSecond = new CompiledRule(rule("minecraft:zombie", NameMatchMode.CONTAINS, "B", true), 1);
		CompiledRule anyThird = new CompiledRule(rule(null, NameMatchMode.CONTAINS, "C", true), 2);
		List<CompiledRule> typeRules = List.of(typeSecond);
		List<CompiledRule> anyType = List.of(anyFirst, anyThird);

		assertEquals(anyFirst, HighlightManager.firstMatch(typeRules, anyType, c -> true));
		assertEquals(typeSecond, HighlightManager.firstMatch(typeRules, anyType, c -> c != anyFirst));
		assertEquals(anyThird, HighlightManager.firstMatch(typeRules, anyType, c -> c == anyThird));
		assertNull(HighlightManager.firstMatch(typeRules, anyType, c -> false));
		assertNull(HighlightManager.firstMatch(List.of(), List.of(), c -> true));
	}

	/** AC-GLOW-10 [A] (T1.8b): real players have random (version 4) UUIDs; Hypixel NPCs do not. */
	@Test
	void realPlayersAreRecognisedByTheirUuid() {
		assertTrue(HighlightManager.isRealPlayerUuid(UUID.randomUUID()));
		assertFalse(HighlightManager.isRealPlayerUuid(new UUID(0x8a3c1e2b1f4d2a5bL, 0x9c7e0d1e2f3a4b5cL)), "version 2, Hypixel NPC style");
		assertFalse(HighlightManager.isRealPlayerUuid(UUID.nameUUIDFromBytes("OfflinePlayer:Npc".getBytes())), "version 3");
	}

	private static HighlightRule rule(String type, NameMatchMode mode, String pattern, boolean enabled) {
		HighlightRule rule = new HighlightRule();
		rule.entityTypeId = type;
		rule.nameMatchMode = mode;
		rule.namePattern = pattern;
		rule.enabled = enabled;
		return rule;
	}

	/** REQ-GLOW-10: logged once per rule and kind of problem, so an entity type or regex typed live logs one line, not one per key. */
	@Test
	void aProblemIsLoggedOncePerKindNotPerTypedText() {
		assertEquals(HighlightManager.problemKind("unknown entity type 'm'"), HighlightManager.problemKind("unknown entity type 'minecraft:zombi'"));
		assertEquals(HighlightManager.problemKind("the regular expression is invalid (Unclosed group)"),
				HighlightManager.problemKind("the regular expression is invalid (Unclosed character class)"));
		assertNotEquals(HighlightManager.problemKind("the name pattern is empty"), HighlightManager.problemKind("unknown entity type 'x'"));
	}
}
