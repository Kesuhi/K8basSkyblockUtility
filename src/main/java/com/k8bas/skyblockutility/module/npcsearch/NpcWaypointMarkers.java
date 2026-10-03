package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.render.marker.Marker;
import com.k8bas.skyblockutility.render.marker.MarkerAnchor;
import com.k8bas.skyblockutility.render.marker.MarkerBeam;
import com.k8bas.skyblockutility.render.marker.MarkerLabel;
import com.k8bas.skyblockutility.render.marker.MarkerProvider;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * NPC Search's fixed waypoints (T3.4), supplied as markers to the world marker toolkit: at each
 * active fixed NPC's block, a beacon beam in the waypoint's resolved colour and, 1.5 blocks above, a
 * see-through name label with no plate plus the distance from the player. With "White waypoint
 * labels" ON the label is white and the distance yellow; OFF, both are in the rule's colour (R21).
 * Reimplemented from scratch after Skyblocker's look; no Skyblocker code (REQ-NPCWP-11). Positions
 * come only from rule or NPC-data coordinates, never from an entity (REQ-NPCWP-12).
 */
public final class NpcWaypointMarkers {
	/** Vanilla's chat yellow, the distance colour of Skyblocker-style waypoints (REQ-NPCWP-03). */
	static final int DISTANCE_YELLOW = 0xFFFF55;
	/** The label floats this far above the waypoint block; the beam rises from the block (REQ-NPCWP-02). */
	static final double LABEL_RISE = 1.5;

	/** The NPC Search settings a waypoint's look depends on. */
	public record Settings(boolean whiteLabels, boolean showBeams, boolean showDistance, Map<String, Integer> islandColors) {
		public Settings {
			islandColors = Map.copyOf(islandColors);
		}
	}

	/** A waypoint's rule, for the per-frame enabled and island check, and its prepared marker. */
	private record Waypoint(NpcRule rule, Marker marker) {
	}

	private static volatile List<NpcRule> activeWaypoints = List.of();
	private static volatile Settings settings = new Settings(true, true, true, Map.of());
	private static volatile List<Waypoint> waypoints = List.of();

	private static final MarkerProvider PROVIDER = new MarkerProvider() {
		@Override
		public boolean isActive() {
			return !waypoints.isEmpty();
		}

		/** Enabled and island filtering happen fresh every frame (string compares over at most a few
		 *  hundred entries, not an entity scan), so a waypoint appears or disappears as soon as the
		 *  player changes island. */
		@Override
		public void collect(Consumer<Marker> out) {
			String currentIsland = IslandTracker.getCurrentIsland();
			for (Waypoint waypoint : waypoints) {
				if (isShown(waypoint.rule(), currentIsland)) {
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

	/**
	 * Called by NpcSearchModule whenever its rules or waypoint settings change, with every fixed
	 * NpcRule, not pre-filtered; an empty list while the module itself is disabled. The markers are
	 * built here, once per change: a fixed rule's position only changes with a rebuild (the NPC data
	 * loading also triggers one).
	 */
	public static void update(List<NpcRule> rules, Settings newSettings) {
		activeWaypoints = List.copyOf(rules);
		settings = newSettings;
		rebuild();
	}

	public static void register() {
		WorldMarkers.add(PROVIDER);
		// A rule from the NPC data follows that entry's current coordinates (REQ-NPCWP-01).
		NpcDatabase.onLoaded(NpcWaypointMarkers::rebuild);
	}

	/** An enabled fixed rule on the current island; a blank island means any island (EC-NPCWP-01). */
	static boolean isShown(NpcRule rule, String currentIsland) {
		if (!rule.enabled || !rule.fixed) {
			return false;
		}
		return rule.island == null || rule.island.isBlank() || rule.island.trim().equals(currentIsland);
	}

	/**
	 * The waypoint block's bottom centre: the NPC data entry's current coordinates if the rule came
	 * from one that is still listed as fixed, otherwise the rule's stored ones (AC-NPCWP-13). The
	 * centre of the block holding the coordinates, so whole block coordinates (the NPC data) and
	 * positions already on a centre (x.5, as some 1.0.1 configs stored them) land on the same spot.
	 */
	static Vec3 position(NpcRule rule, Function<String, NpcDatabaseEntry> data) {
		NpcDatabaseEntry entry = rule.sourceId == null ? null : data.apply(rule.sourceId);
		if (entry != null && entry.fixed && entry.x != null && entry.y != null && entry.z != null) {
			return blockCentre(entry.x, entry.y, entry.z);
		}
		return blockCentre(rule.x, rule.y, rule.z);
	}

	private static Vec3 blockCentre(double x, double y, double z) {
		return new Vec3(Math.floor(x) + 0.5, y, Math.floor(z) + 0.5);
	}

	/** The colour of a label and its distance line: white, or the rule's colour drawn opaque. */
	static int labelColor(int ruleColor, boolean white) {
		return white ? 0xFFFFFFFF : ARGB.opaque(ruleColor);
	}

	static Marker markerFor(NpcRule rule, Settings settings, Function<String, NpcDatabaseEntry> data) {
		int textColor = labelColor(rule.color, settings.whiteLabels());
		MarkerLabel label = MarkerLabel.of(new MarkerLabel.Line(Component.literal(rule.label), textColor))
				.asSeeThrough().raisedBy(LABEL_RISE);
		if (settings.showDistance()) {
			label = label.withDistance(settings.whiteLabels() ? ARGB.opaque(DISTANCE_YELLOW) : textColor);
		}
		MarkerBeam beam = settings.showBeams() ? new MarkerBeam(WaypointColors.beamColor(rule, settings.islandColors())) : null;
		Vec3 block = position(rule, data);
		return new Marker(MarkerAnchor.fixed(block.x, block.y, block.z), label, beam, null);
	}

	private static void rebuild() {
		Settings current = settings;
		List<Waypoint> built = new ArrayList<>();
		for (NpcRule rule : activeWaypoints) {
			built.add(new Waypoint(rule, markerFor(rule, current, NpcDatabase::byId)));
		}
		waypoints = List.copyOf(built);
	}
}
