package com.k8bas.skyblockutility.module.hotspot

import kotlin.math.hypot

/**
 * The active fishing hotspots (T3.6, REQ-HOT-01..REQ-HOT-04, REQ-HOT-06), from the armor stands the client has,
 * on a tick clock it is given, so it is tested without a game. A hotspot is a stand whose name tag vanilla displays
 * and reads `HOTSPOT` (R2); its buff is the displayed name tag at the same X/Z, up to a block below. A removed stand
 * keeps its hotspot through a 10-tick grace period, and a stand sent again within 4 blocks continues the nearest such
 * hotspot with its radius samples and fished-in time. A hotspot whose grace period ends without that is gone; [gone]
 * hears of it when the player had fished in it within 30 s and was within 40 blocks when its stand was removed
 * (T3.6b, REQ-HOT-08, REQ-HOT-09). A world change clears everything with no event. Not thread-safe: the game side
 * drives it on the client thread.
 *
 * @param surface the liquid surface Y under a point, or null while none is found (the chunk is not loaded yet)
 * @param gone    a hotspot the player was fishing in is gone (the toggles are the caller's)
 */
class HotspotTracker @JvmOverloads constructor(
	private val text: HotspotText,
	private val surface: Surface,
	private val gone: java.util.function.Consumer<Hotspot> = java.util.function.Consumer { },
) {
	fun interface Surface {
		fun at(x: Double, y: Double, z: Double): Double?
	}

	/** Where the player is. */
	@JvmRecord
	data class Position(val x: Double, val y: Double, val z: Double)

	/** One armor stand as the scan sees it: its name and whether vanilla displays that name. */
	@JvmRecord
	data class Stand(val id: Int, val x: Double, val y: Double, val z: Double, val name: String, val nameShown: Boolean)

	/** One hotspot. Java sees `getStandId()`, `getBuff()`, `getRemovedAt()`, ... */
	class Hotspot internal constructor(standId: Int, x: Double, footY: Double, z: Double) {
		var standId: Int = standId
			internal set
		var x: Double = x
			internal set
		var z: Double = z
			internal set
		var footY: Double = footY
			internal set
		var buff: HotspotText.Buff = HotspotText.Buff.UNKNOWN
			internal set

		/** The tick its stand was removed, while in the grace period; null while the stand is there. */
		var removedAt: Long? = null
			internal set

		/** The last tick the own bobber was in its area (REQ-HOT-07); null if never. */
		var fishedAt: Long? = null
			internal set

		/** Whether its removal, if no stand comes back, is a "gone" (REQ-HOT-08 (4) and (5), decided at the removal). */
		internal var warnOnExpiry = false
		internal var liquidY: Double? = null
		internal var buffText: String? = null
		private val samples = ArrayDeque<Double>()

		/** The liquid surface, or the stand's foot until it is found (REQ-HOT-03). */
		fun surfaceY(): Double = liquidY ?: footY

		fun sampleCount(): Int = samples.size

		/** The estimated radius on [island] (REQ-HOT-04). */
		fun radius(island: String?): Double = HotspotRadius.estimate(samples.toList(), HotspotRadius.cap(island), HotspotRadius.fallback(island))

		internal fun sample(distance: Double) {
			samples.addLast(distance)
			if (samples.size > MAX_SAMPLES) {
				samples.removeFirst()
			}
		}

		internal fun horizontal(px: Double, pz: Double): Double = hypot(px - x, pz - z)

		/** SPEC's hotspot area: horizontally within the radius and half a block, and less than 3 blocks from the surface. */
		internal fun contains(px: Double, py: Double, pz: Double, island: String?): Boolean =
			horizontal(px, pz) <= radius(island) + AREA_MARGIN && Math.abs(py - surfaceY()) < AREA_DY
	}

	private val hotspots = ArrayList<Hotspot>()

	/** Every hotspot, in the order found; those in their grace period included. */
	fun hotspots(): List<Hotspot> = java.util.List.copyOf(hotspots)

	/** The stands the client has now, at tick [now], with no player position (no hotspot can then be gone). */
	fun scan(stands: List<Stand>, now: Long) = scan(stands, now, null)

	/** The stands the client has now, at tick [now], with the player at [player]. */
	fun scan(stands: List<Stand>, now: Long, player: Position?) {
		val named = stands.filter { it.nameShown }
		val hotspotStands = named.filter { HotspotText.isHotspot(it.name) }
		// A stand gone from this scan is removed first, so a stand sent again in the same tick continues it (EC-HOT-03).
		val ids = hotspotStands.mapTo(HashSet()) { it.id }
		for (spot in hotspots) {
			if (spot.removedAt == null && spot.standId !in ids) {
				spot.removedAt = now
				spot.warnOnExpiry = spot.fishedAt.let { it != null && now - it <= FISHED_TICKS } && player != null &&
					Math.sqrt(square(player.x - spot.x) + square(player.y - spot.surfaceY()) + square(player.z - spot.z)) <= WARN_BLOCKS
			}
		}
		for (stand in hotspotStands) {
			val spot = hotspots.firstOrNull { it.standId == stand.id && it.removedAt == null }
				?: resent(stand)
				?: Hotspot(stand.id, stand.x, stand.y, stand.z).also { hotspots.add(it) }
			spot.standId = stand.id
			spot.x = stand.x
			spot.z = stand.z
			spot.footY = stand.y
			spot.removedAt = null
			spot.warnOnExpiry = false
			if (spot.liquidY == null) {
				spot.liquidY = surface.at(stand.x, stand.y, stand.z)
			}
			buffLine(stand, named)?.let { line ->
				if (line != spot.buffText) {
					spot.buffText = line
					spot.buff = text.buff(line)
				}
			}
		}
		val expired = hotspots.filter { spot -> spot.removedAt.let { it != null && now - it >= GRACE_TICKS } }
		hotspots.removeAll(expired.toSet())
		expired.filter { it.warnOnExpiry }.forEach { gone.accept(it) }
	}

	/** The own bobber at this point at tick [now] (REQ-HOT-07): each hotspot whose area holds it was fished in now. */
	fun fished(x: Double, y: Double, z: Double, now: Long, island: String?) {
		for (spot in hotspots) {
			if (spot.removedAt == null && spot.contains(x, y, z, island)) {
				spot.fishedAt = now
			}
		}
	}

	/** A server particle at this point: a radius sample of the nearest hotspot it can belong to (REQ-HOT-04). */
	fun particle(x: Double, y: Double, z: Double) {
		val spot = hotspots.filter { it.removedAt == null }.minByOrNull { it.horizontal(x, z) } ?: return
		val distance = spot.horizontal(x, z)
		// Past the largest island cap (and a block) it is no ring particle; the island's own cap applies in the estimate.
		if (distance <= MAX_CAP + 1.0 && Math.abs(y - spot.surfaceY()) <= MAX_DY) {
			spot.sample(distance)
		}
	}

	/** [spot]'s radius on [island], or the island fallback for none. */
	fun radiusOrFallback(spot: Hotspot?, island: String?): Double = spot?.radius(island) ?: HotspotRadius.fallback(island)

	/** World change, server change or disconnect (REQ-HOT-06). */
	fun clear() {
		hotspots.clear()
	}

	/** The nearest hotspot in its grace period within 4 blocks of a stand sent again (EC-HOT-03, EC-HOT-05). */
	private fun resent(stand: Stand): Hotspot? = hotspots.filter { it.removedAt != null && it.horizontal(stand.x, stand.z) <= RESENT_BLOCKS }
		.minByOrNull { it.horizontal(stand.x, stand.z) }

	/**
	 * The text of the displayed name tag at the stand's X/Z, up to a block below (REQ-HOT-02): a recognised buff line
	 * first, so a name tag passing by does not win over it, then the nearest.
	 */
	private fun buffLine(stand: Stand, named: List<Stand>): String? = named
		.filter { it.id != stand.id && hypot(it.x - stand.x, it.z - stand.z) <= BUFF_XZ && stand.y - it.y in 0.0..BUFF_BELOW && !HotspotText.isHotspot(it.name) }
		.minWithOrNull(compareBy<Stand> { if (HotspotText.known(it.name)) 0 else 1 }.thenBy { hypot(it.x - stand.x, it.z - stand.z) })?.name

	companion object {
		/** How long a removed stand's hotspot is kept (REQ-HOT-06). */
		const val GRACE_TICKS = 10L

		/** A stand sent again this close continues a removed hotspot. */
		const val RESENT_BLOCKS = 4.0

		/** The fished window: 30 s (REQ-HOT-08 (4)). */
		const val FISHED_TICKS = 600L

		/** The player's distance to the centre at the removal (REQ-HOT-08 (5)). */
		const val WARN_BLOCKS = 40.0
		private const val AREA_MARGIN = 0.5
		private const val AREA_DY = 3.0
		private const val BUFF_XZ = 0.5
		private const val BUFF_BELOW = 1.0
		private const val MAX_CAP = 5.0
		private const val MAX_DY = 2.0
		private const val MAX_SAMPLES = 256

		private fun square(value: Double): Double = value * value
	}
}
