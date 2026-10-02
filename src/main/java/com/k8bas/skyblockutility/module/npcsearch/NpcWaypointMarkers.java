package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.render.marker.Marker;
import com.k8bas.skyblockutility.render.marker.MarkerAnchor;
import com.k8bas.skyblockutility.render.marker.MarkerLabel;
import com.k8bas.skyblockutility.render.marker.MarkerProvider;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * NPC Search's fixed waypoints, supplied as markers to the world marker toolkit (T3.0b): a floating,
 * see-through name label plus live distance at each active fixed NPC's position. Only fixed
 * coordinates are drawn through blocks (P4). The look is the 1.0.1 label (dark plate), white by
 * default or in the rule's colour (R21), until T3.4 brings the Skyblocker-style waypoint.
 */
public final class NpcWaypointMarkers {
	private static final int BACKGROUND_COLOR = 0x70202020;

	/** A waypoint's rule, for the per-frame enabled and island check, and its prepared marker. */
	private record Waypoint(NpcRule rule, Marker marker) {
	}

	private static volatile List<NpcRule> activeWaypoints = List.of();
	private static volatile boolean whiteLabels = true;
	private static volatile List<Waypoint> waypoints = List.of();

	private static final MarkerProvider PROVIDER = new MarkerProvider() {
		@Override
		public boolean isActive() {
			return !waypoints.isEmpty();
		}

		/** Enabled and island filtering happen fresh every frame (string compares over a couple dozen
		 *  entries, not an entity scan), so a waypoint appears or disappears as soon as the player
		 *  changes island. */
		@Override
		public void collect(Consumer<Marker> out) {
			String currentIsland = IslandTracker.getCurrentIsland();
			for (Waypoint waypoint : waypoints) {
				NpcRule rule = waypoint.rule();
				if (rule.enabled && (rule.island == null || rule.island.equals(currentIsland))) {
					out.accept(waypoint.marker());
				}
			}
		}

		/** The waypoints come from the settings, not from a world: they are kept, and the island
		 *  check above gates them (IslandTracker clears the island on every world change). */
		@Override
		public void reset() {
		}
	};

	private NpcWaypointMarkers() {
	}

	/** The "White waypoint labels" setting (R21); set by NpcSearchModule. */
	public static void setWhiteLabels(boolean white) {
		whiteLabels = white;
		rebuild();
	}

	/** The colour of a label and its distance line: white, or the rule's colour drawn opaque. */
	static int labelColor(int ruleColor, boolean white) {
		return white ? 0xFFFFFFFF : ARGB.opaque(ruleColor);
	}

	/** Called by NpcSearchModule whenever its rule set changes — every fixed NpcRule, not
	 *  pre-filtered. Pass an empty list while the module itself is disabled. The markers are built
	 *  here, once per rebuild: a fixed rule's position never changes without a rebuild (there is no
	 *  coordinate-editing UI). */
	public static void setActiveWaypoints(List<NpcRule> rules) {
		activeWaypoints = rules;
		rebuild();
	}

	public static void register() {
		WorldMarkers.add(PROVIDER);
	}

	private static void rebuild() {
		boolean white = whiteLabels;
		List<Waypoint> built = new ArrayList<>();
		for (NpcRule rule : activeWaypoints) {
			int color = labelColor(rule.color, white);
			MarkerLabel label = MarkerLabel.of(new MarkerLabel.Line(Component.literal(rule.label), color))
					.withBackground(BACKGROUND_COLOR).asSeeThrough().withDistance(color);
			built.add(new Waypoint(rule, new Marker(MarkerAnchor.fixed(rule.x, rule.y + 1.5, rule.z), label)));
		}
		waypoints = List.copyOf(built);
	}
}
