package com.k8bas.skyblockutility.module.hotspot

/**
 * Which server particles can be a hotspot's ring (T3.6, REQ-HOT-04): pink dust in water, smoke on the Crimson Isle
 * (lava). Both are UNVERIFIED in game until the field check (AC-HOT-08); other dust nearby (a Hermit Crab pet,
 * EC-HOT-07) is left to the mode estimate and the island cap.
 */
object HotspotParticles {
	enum class Kind {
		DUST,
		SMOKE,
		OTHER,
	}

	private const val LAVA_ISLAND = "Crimson Isle"

	/** Whether a particle of [kind] (dust with the colour [red], [green], [blue] in 0..1) on [island] may be a ring's. */
	@JvmStatic
	fun isRing(kind: Kind, red: Float, green: Float, blue: Float, island: String?): Boolean = when (kind) {
		Kind.DUST -> red >= 0.6f && blue >= 0.3f && green <= red - 0.2f
		Kind.SMOKE -> island == LAVA_ISLAND
		Kind.OTHER -> false
	}
}
