package com.k8bas.skyblockutility.highlight;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test
	void rebuildIndexesEnabledValidRulesByType() {
		HighlightManager manager = new HighlightManager();
		manager.rebuild(List.of(
				rule("minecraft:zombie", NameMatchMode.CONTAINS, "Zombie", true),
				rule("minecraft:zombie", NameMatchMode.CONTAINS, "Off", false),
				rule("", NameMatchMode.CONTAINS, "Trinity", true),
				rule("Not A Type!", NameMatchMode.EXACT, "Duncan", true),
				rule(null, NameMatchMode.REGEX, "([", true)));

		List<CompiledRule> zombie = manager.rulesForType(Identifier.tryParse("minecraft:zombie"));
		assertEquals(1, zombie.size());
		assertEquals("Zombie", zombie.get(0).rule.namePattern);

		List<CompiledRule> any = manager.rulesForAnyType();
		assertEquals(List.of("Trinity", "Duncan"), any.stream().map(c -> c.rule.namePattern).toList());
	}

	private static HighlightRule rule(String type, NameMatchMode mode, String pattern, boolean enabled) {
		HighlightRule rule = new HighlightRule();
		rule.entityTypeId = type;
		rule.nameMatchMode = mode;
		rule.namePattern = pattern;
		rule.enabled = enabled;
		return rule;
	}
}
