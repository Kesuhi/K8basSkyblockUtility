package com.k8bas.skyblockutility.module.corpsespots

import kotlin.math.sqrt

/**
 * Corpse spots ruled out by the Organ Donor's silence (T3.3e, REQ-CORPSE-12, R44). The ding plays while an unlooted
 * corpse is within 20 blocks, so once it has been heard in this shaft (the accessory works), a spot is ruled out when
 * the player stayed within [RADIUS] blocks of it for [QUIET_MS] with no ding: there is no unlooted corpse. Silence counts
 * only over ticks sampled back to back, so a pause (the option off, a frozen client) is never silence. A ding never rules a
 * far spot out (there can be several corpses), and its pitch and position are not used. Ruled-out spots come
 * back when the Frozen Corpses widget lists more corpses (a Dead Man's Chest) or in another shaft ([reset]). Pure, so it
 * is tested without a game; the client thread drives it.
 */
class SpotRuleOut {
	private var heard = false
	private var lastDing = 0L
	private var corpses = -1
	private val window = ArrayDeque<Pair<Long, DoubleArray>>()
	private val ruledOut = HashSet<Int>()

	/** The numbers of the spots ruled out in this shaft. */
	fun ruledOut(): Set<Int> = java.util.Set.copyOf(ruledOut)

	/** A ding at [nowMs]: the accessory works, and the quiet window starts again. */
	fun ding(nowMs: Long) {
		heard = true
		lastDing = nowMs
		window.clear()
	}

	/**
	 * One client tick: the player at [player] among [spots], with [corpseCount] corpses on the widget (0 or less when
	 * none is listed). Rules out every spot within reach for the whole quiet window.
	 */
	fun tick(nowMs: Long, player: DoubleArray, spots: List<CorpseSpot>, corpseCount: Int) {
		if (corpseCount > corpses) {
			if (corpses > 0) {
				// A new corpse may lie at any spot, and the silence so far was from before it: start over.
				ruledOut.clear()
				window.clear()
				lastDing = nowMs
			}
			corpses = corpseCount
		}
		if (window.isNotEmpty() && nowMs - window.last().first > MAX_GAP_MS) {
			// Not sampled in between: what happened there is unknown.
			window.clear()
		}
		window.addLast(nowMs to player.copyOf())
		while (window.isNotEmpty() && window.first().first < nowMs - QUIET_MS) {
			window.removeFirst()
		}
		if (!heard || nowMs - lastDing < QUIET_MS || window.first().first > nowMs - QUIET_MS + TICK_SLACK_MS) {
			return
		}
		for (spot in spots) {
			if (spot.number !in ruledOut && window.all { (_, at) -> distance(spot, at) <= RADIUS }) {
				ruledOut += spot.number
			}
		}
	}

	/** Another shaft (or leaving one): nothing ruled out, and the accessory must ding again there first. */
	fun reset() {
		heard = false
		corpses = -1
		window.clear()
		ruledOut.clear()
	}

	companion object {
		/** Less than the ding's 20 blocks, for the uncertain edge of its reach. */
		const val RADIUS = 16.0

		/** Dings repeat every 0.55–1.0 s within reach; 4 s without one is silence, with room for a lag spike. */
		const val QUIET_MS = 4_000L

		/** How far from the player a ding counts here: wider than the HUD's [DingHint.NEAR], as a missed ding rules out wrongly. */
		const val DING_NEAR = 8.0

		/** More than this between two samples starts the window again. */
		const val MAX_GAP_MS = 250L

		/** One tick of slack for where the window starts. */
		private const val TICK_SLACK_MS = 50L

		private fun distance(spot: CorpseSpot, at: DoubleArray): Double {
			val dx = spot.x + 0.5 - at[0]
			val dy = spot.y + 0.5 - at[1]
			val dz = spot.z + 0.5 - at[2]
			return sqrt(dx * dx + dy * dy + dz * dz)
		}
	}
}
