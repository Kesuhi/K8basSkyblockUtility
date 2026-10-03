package com.k8bas.skyblockutility.gametest;

import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcWaypointMarkers;

import java.util.List;
import java.util.Map;

/** For gametests: shows NPC waypoints with chosen settings, without touching the saved config. Call on the client thread. */
public final class TestWaypoints {
	/** The fresh-config look: white labels, beams and distance (AC-NPCWP-05). */
	public static final NpcWaypointMarkers.Settings DEFAULTS = settings(true, true, true);
	/** Only the label and its distance line, for tests that measure the label alone. */
	public static final NpcWaypointMarkers.Settings LABELS_ONLY = settings(true, false, true);

	private TestWaypoints() {
	}

	public static NpcWaypointMarkers.Settings settings(boolean whiteLabels, boolean beams, boolean distance) {
		return new NpcWaypointMarkers.Settings(whiteLabels, beams, distance, Map.of());
	}

	public static void show(List<NpcRule> rules, NpcWaypointMarkers.Settings settings) {
		NpcWaypointMarkers.update(rules, settings);
	}

	public static void clear() {
		NpcWaypointMarkers.update(List.of(), DEFAULTS);
	}

	/** A fixed rule whose waypoint block is (x, y, z): the label is centred on that block, 1.5 above it. */
	public static NpcRule fixedRule(String label, String island, int color, int x, int y, int z) {
		NpcRule rule = new NpcRule();
		rule.label = label;
		rule.island = island;
		rule.fixed = true;
		rule.color = color;
		rule.x = x;
		rule.y = y;
		rule.z = z;
		return rule;
	}
}
