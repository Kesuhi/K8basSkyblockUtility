package com.k8bas.skyblockutility.module.hotspot

import java.util.function.IntFunction

/**
 * A hotspot's surface Y (T3.6, SPEC's hotspot terms, REQ-HOT-03): the top of the first water or lava block within 3
 * blocks below the stand. A stand standing in the liquid gets the top of that liquid column, not its own block.
 */
object HotspotSurface {
	private const val BELOW = 3
	private const val UP = 16

	/**
	 * @param footY  the stand's foot Y
	 * @param liquid the water or lava height (0..1) of the block at a Y, or null for any other block
	 * @return the surface Y, or null when no liquid is within reach (not loaded yet)
	 */
	@JvmStatic
	fun find(footY: Double, liquid: IntFunction<Float?>): Double? {
		val foot = Math.floor(footY).toInt()
		var y = (foot downTo foot - BELOW).firstOrNull { liquid.apply(it) != null } ?: return null
		repeat(UP) {
			if (liquid.apply(y + 1) == null) {
				return y + liquid.apply(y)!!.toDouble()
			}
			y++
		}
		return y + liquid.apply(y)!!.toDouble()
	}
}
