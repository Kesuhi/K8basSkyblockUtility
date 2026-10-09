package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.render.marker.Marker;
import com.k8bas.skyblockutility.render.marker.MarkerLabel;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The NPC waypoints (T3.4): position, gating, look and toggles (REQ-NPCWP-01/-02/-03/-08). */
class NpcWaypointMarkersTest {
	private static final Function<String, NpcDatabaseEntry> NO_DATA = id -> null;
	private static final NpcWaypointMarkers.Settings DEFAULTS = new NpcWaypointMarkers.Settings(true, true, Map.of());

	private static NpcRule fixedRule(double x, double y, double z) {
		NpcRule rule = new NpcRule();
		rule.label = "Udel";
		rule.fixed = true;
		rule.island = "Crimson Isle";
		rule.color = 0x0AA351;
		rule.x = x;
		rule.y = y;
		rule.z = z;
		return rule;
	}

	private static NpcDatabaseEntry entry(String id, double x, double y, double z) {
		NpcDatabaseEntry entry = new NpcDatabaseEntry();
		entry.id = id;
		entry.displayName = id;
		entry.island = "Crimson Isle";
		entry.fixed = true;
		entry.x = x;
		entry.y = y;
		entry.z = z;
		return entry;
	}

	/** AC-NPCWP-13: a rule from the NPC data follows that entry's current coordinates. */
	@Test
	void aRuleFromTheDataFollowsItsCurrentCoordinates() {
		Map<String, NpcDatabaseEntry> data = Map.of("udel", entry("udel", 20, 70, 30));
		NpcRule moved = fixedRule(1, 2, 3);
		moved.sourceId = "udel";
		NpcRule own = fixedRule(4, 5, 6);
		NpcRule gone = fixedRule(7, 8, 9);
		gone.sourceId = "no_longer_listed";
		assertEquals(new Vec3(20.5, 70, 30.5), NpcWaypointMarkers.position(moved, data::get), "B: the data's position");
		assertEquals(new Vec3(4.5, 5, 6.5), NpcWaypointMarkers.position(own, data::get), "C: no sourceId");
		assertEquals(new Vec3(7.5, 8, 9.5), NpcWaypointMarkers.position(gone, data::get), "D: sourceId not in the data");
	}

	/** Some 1.0.1 configs stored an NPC's centre (x.5); that stays on the NPC's block instead of moving to its corner. */
	@Test
	void aStoredCentreStaysOnItsBlock() {
		assertEquals(new Vec3(-3.5, 70, -90.5), NpcWaypointMarkers.position(fixedRule(-3.5, 70, -90.5), NO_DATA));
		assertEquals(new Vec3(12.5, 70, 4.5), NpcWaypointMarkers.position(fixedRule(12.5, 70, 4.5), NO_DATA));
		assertEquals(new Vec3(-3.5, 70, -90.5), NpcWaypointMarkers.position(fixedRule(-4, 70, -91), NO_DATA), "the block itself");
	}

	/** AC-NPCWP-09: island gating. */
	@Test
	void onlyEnabledFixedRulesOnTheirIslandAreShown() {
		NpcRule lobby = fixedRule(0, 0, 0);
		lobby.island = "Dungeon Hub";
		assertTrue(NpcWaypointMarkers.isShown(lobby, "Dungeon Hub"), "mode dungeon_hub");
		assertFalse(NpcWaypointMarkers.isShown(lobby, "Catacombs"), "mode dungeon (EC-NPCWP-07)");
		NpcRule anywhere = fixedRule(0, 0, 0);
		anywhere.island = null;
		assertTrue(NpcWaypointMarkers.isShown(anywhere, "Hub"));
		anywhere.island = " ";
		assertTrue(NpcWaypointMarkers.isShown(anywhere, "Hub"), "a blank island means any island (EC-NPCWP-01)");
		assertTrue(NpcWaypointMarkers.isShown(anywhere, null), "also while the island is unknown");
		NpcRule padded = fixedRule(0, 0, 0);
		padded.island = "Hub ";
		assertTrue(NpcWaypointMarkers.isShown(padded, "Hub"), "a typed island with stray spaces still matches");
		NpcRule disabled = fixedRule(0, 0, 0);
		disabled.enabled = false;
		assertFalse(NpcWaypointMarkers.isShown(disabled, "Crimson Isle"));
		NpcRule moving = fixedRule(0, 0, 0);
		moving.fixed = false;
		assertFalse(NpcWaypointMarkers.isShown(moving, "Crimson Isle"), "moving rules keep their glow only");
	}

	/** AC-NPCWP-06: the label sits 1.5 blocks above the block centre; the distance is taken from the player. */
	@Test
	void theDistanceIsMeasuredToTheLabelFromThePlayer() {
		Marker marker = NpcWaypointMarkers.markerFor(fixedRule(10, 64, 0), DEFAULTS, NO_DATA);
		Vec3 label = WorldMarkers.labelPosition(marker.anchor().position(0), marker.label());
		assertEquals(new Vec3(10.5, 65.5, 0.5), label);
		assertEquals(10.62, new Vec3(0, 64, 0).distanceTo(label), 0.005);
		assertEquals("11m", WorldMarkers.distanceText(new Vec3(0, 64, 0), label));
		// A waypoint block 3 below the player: 3 m to the block, 2 m to its label.
		Marker underfoot = NpcWaypointMarkers.markerFor(fixedRule(0, 61, 0), DEFAULTS, NO_DATA);
		Vec3 below = WorldMarkers.labelPosition(underfoot.anchor().position(0), underfoot.label());
		assertEquals("2m", WorldMarkers.distanceText(new Vec3(0.5, 64, 0.5), below));
	}

	/** REQ-NPCWP-02/-03/-04, AC-NPCWP-05 [A]: the Skyblocker-style look and the toggles. */
	@Test
	void theLookFollowsTheSettings() {
		NpcRule rule = fixedRule(0, 64, 0);
		rule.color = 0x00AAFF;
		Marker on = NpcWaypointMarkers.markerFor(rule, DEFAULTS, NO_DATA);
		MarkerLabel label = on.label();
		assertEquals(1, label.lines().size());
		assertEquals(0xFFFFFF, label.lines().get(0).color() & 0xFFFFFF, "a white label");
		assertEquals(0, label.backgroundColor(), "no background plate");
		assertTrue(label.seeThrough(), "fixed coordinates show through blocks");
		assertTrue(label.distanceLine());
		assertEquals(0xFFFF55, label.distanceColor() & 0xFFFFFF, "a yellow distance line");
		assertNotNull(on.beam());
		assertEquals(0xFF000000 | WaypointColors.ISLAND_DEFAULTS.get("Crimson Isle"), on.beam().argb(), "the beam in the island colour");

		// R31: the rule's own colour never reaches the label or its distance line.
		assertTrue((label.lines().get(0).color() & 0xFFFFFF) != rule.color && (label.distanceColor() & 0xFFFFFF) != rule.color);

		Marker noBeam = NpcWaypointMarkers.markerFor(rule, new NpcWaypointMarkers.Settings(false, true, Map.of()), NO_DATA);
		assertNull(noBeam.beam(), "Show beacon beams OFF");
		assertNotNull(noBeam.label(), "the label stays");
		Marker noDistance = NpcWaypointMarkers.markerFor(rule, new NpcWaypointMarkers.Settings(true, false, Map.of()), NO_DATA);
		assertFalse(noDistance.label().distanceLine(), "Show distance OFF: one text line");
	}

	/** AC-NPCWP-05 [A]: a fresh config has the module, beams and distance ON. */
	@Test
	void aFreshConfigHasEverythingOn() {
		NpcSearchConfig config = new NpcSearchConfig();
		assertTrue(config.enabled);
		assertTrue(config.showBeams);
		assertTrue(config.showDistance);
		assertTrue(config.islandBeamColors.isEmpty(), "every island starts at its documented default");
		assertNull(new NpcRule().beamColor, "a new rule follows its island colour");
	}

	/** AC-NPCWP-05: each saved toggle reaches the waypoints, and only its own part. */
	@Test
	void theSavedTogglesReachTheWaypoints() {
		NpcSearchConfig config = new NpcSearchConfig();
		config.showBeams = false;
		config.islandBeamColors.put("Crimson Isle", 0x123456);
		Marker noBeam = NpcWaypointMarkers.markerFor(fixedRule(0, 64, 0), NpcSearchModule.waypointSettings(config), NO_DATA);
		assertNull(noBeam.beam());
		assertTrue(noBeam.label().distanceLine());
		assertEquals(0xFFFFFFFF, noBeam.label().lines().get(0).color());
		config.showBeams = true;
		config.showDistance = false;
		Marker noDistance = NpcWaypointMarkers.markerFor(fixedRule(0, 64, 0), NpcSearchModule.waypointSettings(config), NO_DATA);
		assertFalse(noDistance.label().distanceLine());
		assertEquals(0xFF123456, noDistance.beam().argb(), "the edited island colour");
		config.showDistance = true;
		Marker both = NpcWaypointMarkers.markerFor(fixedRule(0, 64, 0), NpcSearchModule.waypointSettings(config), NO_DATA);
		assertEquals(0xFFFFFFFF, both.label().lines().get(0).color(), "white, never the rule's colour (R31)");
		assertNotNull(both.beam());
	}

	/** R31: every waypoint label is opaque white with a yellow distance line, whatever colour its rule has stored. */
	@Test
	void labelsAreAlwaysWhite() {
		for (int stored : new int[] {0x0AA351, 0x00FF5555, 0xFFFFFF, 0}) {
			NpcRule rule = fixedRule(0, 64, 0);
			rule.color = stored;
			MarkerLabel label = NpcWaypointMarkers.markerFor(rule, DEFAULTS, NO_DATA).label();
			assertEquals(0xFFFFFFFF, label.lines().get(0).color(), "stored " + Integer.toHexString(stored));
			assertEquals(0xFFFFFF55, label.distanceColor(), "stored " + Integer.toHexString(stored));
		}
	}

	/** R31: a moving NPC is outlined in white, so its "You found" title is white too; the rest of the rule carries over. */
	@Test
	void movingNpcsAreOutlinedInWhite() {
		NpcRule rule = fixedRule(0, 64, 0);
		rule.fixed = false;
		rule.id = "npc-1";
		rule.color = 0xFF5555;
		rule.namePattern = "Trinity";
		rule.sourceId = "trinity";
		com.k8bas.skyblockutility.highlight.HighlightRule outline = NpcSearchModule.toHighlightRule(rule);
		assertEquals(0xFFFFFF, outline.color);
		assertEquals("npc-1", outline.id);
		assertEquals("Trinity", outline.namePattern);
		assertEquals("trinity", outline.sourceId);
		assertEquals("Crimson Isle", outline.island);
	}
}
