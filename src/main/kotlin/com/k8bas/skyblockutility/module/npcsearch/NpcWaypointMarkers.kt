package com.k8bas.skyblockutility.module.npcsearch

import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.render.marker.Marker
import com.k8bas.skyblockutility.render.marker.MarkerAnchor
import com.k8bas.skyblockutility.render.marker.MarkerBeam
import com.k8bas.skyblockutility.render.marker.MarkerLabel
import com.k8bas.skyblockutility.render.marker.MarkerProvider
import com.k8bas.skyblockutility.render.marker.WorldMarkers
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.world.phys.Vec3
import java.util.function.Consumer
import java.util.function.Function

/**
 * NPC Search's fixed waypoints (T3.4), supplied as markers to the world marker toolkit: at each
 * active fixed NPC's block, a beacon beam in the waypoint's resolved colour and, 1.5 blocks above, a
 * see-through name label with no plate plus the distance from the player. With "White waypoint
 * labels" ON the label is white and the distance yellow; OFF, both are in the rule's colour (R21).
 * Reimplemented from scratch after Skyblocker's look; no Skyblocker code (REQ-NPCWP-11). Positions
 * come only from rule or NPC-data coordinates, never from an entity (REQ-NPCWP-12).
 */
object NpcWaypointMarkers {
	/** Vanilla's chat yellow, the distance colour of Skyblocker-style waypoints (REQ-NPCWP-03). */
	const val DISTANCE_YELLOW: Int = 0xFFFF55

	/** The label floats this far above the waypoint block; the beam rises from the block (REQ-NPCWP-02). */
	const val LABEL_RISE: Double = 1.5

	/**
	 * The NPC Search settings a waypoint's look depends on. A class rather than a data class: the colours
	 * are copied on construction. Java sees `whiteLabels()`, `showBeams()`, `showDistance()`, `islandColors()`.
	 */
	class Settings(
		@get:JvmName("whiteLabels") val whiteLabels: Boolean,
		@get:JvmName("showBeams") val showBeams: Boolean,
		@get:JvmName("showDistance") val showDistance: Boolean,
		islandColors: Map<String, Int>,
	) {
		@get:JvmName("islandColors")
		val islandColors: Map<String, Int> = java.util.Map.copyOf(islandColors)

		override fun equals(other: Any?): Boolean = other is Settings && whiteLabels == other.whiteLabels && showBeams == other.showBeams &&
			showDistance == other.showDistance && islandColors == other.islandColors

		override fun hashCode(): Int =
			((whiteLabels.hashCode() * 31 + showBeams.hashCode()) * 31 + showDistance.hashCode()) * 31 + islandColors.hashCode()

		override fun toString(): String =
			"Settings[whiteLabels=$whiteLabels, showBeams=$showBeams, showDistance=$showDistance, islandColors=$islandColors]"
	}

	/** A waypoint's rule, for the per-frame enabled and island check, and its prepared marker. */
	private class Waypoint(val rule: NpcRule, val marker: Marker)

	@Volatile
	private var activeWaypoints: List<NpcRule> = java.util.List.of()

	@Volatile
	private var settings: Settings = Settings(true, true, true, java.util.Map.of())

	@Volatile
	private var waypoints: List<Waypoint> = java.util.List.of()

	private val PROVIDER: MarkerProvider = object : MarkerProvider {
		override fun isActive(): Boolean = waypoints.isNotEmpty()

		/**
		 * Enabled and island filtering happen fresh every frame (string compares over at most a few
		 * hundred entries, not an entity scan), so a waypoint appears or disappears as soon as the
		 * player changes island.
		 */
		override fun collect(out: Consumer<Marker>) {
			val currentIsland = IslandTracker.getCurrentIsland()
			for (waypoint in waypoints) {
				if (isShown(waypoint.rule, currentIsland)) {
					out.accept(waypoint.marker)
				}
			}
		}

		/**
		 * The waypoints come from the settings, not from a world: they are kept, and the island
		 * check above gates them (IslandTracker clears the island on every world change).
		 */
		override fun reset() {
		}
	}

	/**
	 * Called by NpcSearchModule whenever its rules or waypoint settings change, with every fixed
	 * NpcRule, not pre-filtered; an empty list while the module itself is disabled. The markers are
	 * built here, once per change: a fixed rule's position only changes with a rebuild (the NPC data
	 * loading also triggers one).
	 */
	@JvmStatic
	fun update(rules: List<NpcRule>, newSettings: Settings) {
		activeWaypoints = java.util.List.copyOf(rules)
		settings = newSettings
		rebuild()
	}

	/** The display settings the waypoints are drawn with now. */
	@JvmStatic
	fun settings(): Settings = settings

	@JvmStatic
	fun register() {
		WorldMarkers.add(PROVIDER)
		// A rule from the NPC data follows that entry's current coordinates (REQ-NPCWP-01).
		NpcDatabase.onLoaded { rebuild() }
	}

	/** An enabled fixed rule on the current island; a blank island means any island (EC-NPCWP-01). */
	@JvmStatic
	fun isShown(rule: NpcRule, currentIsland: String?): Boolean {
		if (!rule.enabled || !rule.fixed) {
			return false
		}
		val island = rule.island
		// Java's isBlank and trim; both inline, so nothing is allocated per frame for them.
		return island == null || island.all(Character::isWhitespace) || island.trim { it <= ' ' } == currentIsland
	}

	/**
	 * The waypoint block's bottom centre: the NPC data entry's current coordinates if the rule came
	 * from one that is still listed as fixed, otherwise the rule's stored ones (AC-NPCWP-13). The
	 * centre of the block holding the coordinates, so whole block coordinates (the NPC data) and
	 * positions already on a centre (x.5, as some 1.0.1 configs stored them) land on the same spot.
	 */
	@JvmStatic
	fun position(rule: NpcRule, data: Function<String, NpcDatabaseEntry?>): Vec3 {
		val sourceId = rule.sourceId
		val entry = if (sourceId == null) null else data.apply(sourceId)
		if (entry != null && entry.fixed && entry.x != null && entry.y != null && entry.z != null) {
			return blockCentre(entry.x, entry.y, entry.z)
		}
		return blockCentre(rule.x, rule.y, rule.z)
	}

	private fun blockCentre(x: Double, y: Double, z: Double): Vec3 = Vec3(Math.floor(x) + 0.5, y, Math.floor(z) + 0.5)

	/** The colour of a label and its distance line: white, or the rule's colour drawn opaque. */
	@JvmStatic
	fun labelColor(ruleColor: Int, white: Boolean): Int = if (white) 0xFFFFFFFF.toInt() else ARGB.opaque(ruleColor)

	@JvmStatic
	fun markerFor(rule: NpcRule, settings: Settings, data: Function<String, NpcDatabaseEntry?>): Marker {
		val textColor = labelColor(rule.color, settings.whiteLabels)
		var label = MarkerLabel.of(MarkerLabel.Line(Component.literal(rule.label), textColor)).asSeeThrough().raisedBy(LABEL_RISE)
		if (settings.showDistance) {
			label = label.withDistance(if (settings.whiteLabels) ARGB.opaque(DISTANCE_YELLOW) else textColor)
		}
		val beam = if (settings.showBeams) MarkerBeam(WaypointColors.beamColor(rule, settings.islandColors)) else null
		val block = position(rule, data)
		return Marker(MarkerAnchor.fixed(block.x, block.y, block.z), label, beam, null)
	}

	private fun rebuild() {
		val current = settings
		val built = ArrayList<Waypoint>()
		for (rule in activeWaypoints) {
			built.add(Waypoint(rule, markerFor(rule, current) { NpcDatabase.byId(it) }))
		}
		waypoints = java.util.List.copyOf(built)
	}
}
