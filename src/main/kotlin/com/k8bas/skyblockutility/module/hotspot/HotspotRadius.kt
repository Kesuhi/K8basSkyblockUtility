package com.k8bas.skyblockutility.module.hotspot

import kotlin.math.round

/**
 * A hotspot's radius (T3.6, REQ-HOT-04): the most frequent horizontal distance of the server's ring particles from
 * the centre, in 0.5-block steps, capped per island. The caps are UNVERIFIED in game; until particles are seen the
 * fallback is used, which starts at the cap and is tuned at the field check (AC-HOT-08).
 */
object HotspotRadius {
	private const val GENERIC_CAP = 3.0

	/** No ring is smaller: a mode of 0 (particles at the centre) would be no ring at all. */
	const val MIN_RADIUS = 0.5
	private val CAPS = mapOf("Crimson Isle" to 5.0, "Torrhus Canyon" to 5.0, "Jerry" to 4.0, "Lotus Atoll" to 4.0)

	@JvmStatic
	fun cap(island: String?): Double = CAPS[island] ?: GENERIC_CAP

	@JvmStatic
	fun fallback(island: String?): Double = cap(island)

	/** The mode of [samples] rounded to 0.5 (the smaller on a tie), from [MIN_RADIUS] to [cap]; [fallback] with no samples. */
	@JvmStatic
	fun estimate(samples: List<Double>, cap: Double, fallback: Double): Double {
		if (samples.isEmpty()) {
			return fallback
		}
		val counts = samples.groupingBy { round(it * 2) / 2 }.eachCount()
		val mode = counts.entries.sortedWith(compareByDescending<Map.Entry<Double, Int>> { it.value }.thenBy { it.key }).first().key
		return minOf(mode, cap).coerceAtLeast(MIN_RADIUS)
	}
}
