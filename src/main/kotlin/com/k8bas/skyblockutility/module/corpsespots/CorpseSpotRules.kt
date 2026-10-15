package com.k8bas.skyblockutility.module.corpsespots

import com.k8bas.skyblockutility.skyblock.MineshaftSnapshot
import com.k8bas.skyblockutility.skyblock.WidgetPresence
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The possible corpse spots' rules without a game (T3.3, R40). The shown set depends only on the toggles, the shaft
 * code and the Frozen Corpses widget, never on entities (REQ-CORPSE-04).
 */
object CorpseSpotRules {
	/**
	 * The spots to show (REQ-CORPSE-03, REQ-CORPSE-05): every spot of the shaft's code while the feature is on and the
	 * player is in a shaft whose code is known and has data; none once the widget lists only looted corpses, if
	 * "Hide spots when all corpses are looted" is on. A missing, cut-off or unreadable widget never hides them.
	 */
	@JvmStatic
	fun visible(config: CorpseSpotsConfig, shaft: MineshaftSnapshot, spots: CorpseSpots?): List<CorpseSpot> {
		if (!config.enabled || !shaft.inShaft || spots == null) {
			return listOf()
		}
		val corpses = shaft.corpses
		val allLooted = corpses.presence == WidgetPresence.PRESENT && corpses.corpses.isNotEmpty() && corpses.corpses.all { it.looted }
		return if (config.hideWhenLooted && allLooted) listOf() else spots.spots(shaft.code)
	}

	/**
	 * The `/ksu corpses` reply (REQ-CORPSE-06): a header with the shaft and its spot count, then `#n x, y, z · d m` per
	 * spot, nearest first, d the distance from [player] to the spot's block centre in whole metres rounded half-up;
	 * without a player, in the data's order and with no distance. Or one of the special replies.
	 */
	@JvmStatic
	fun list(shaft: MineshaftSnapshot, spots: CorpseSpots?, unavailable: Boolean, player: DoubleArray?): List<String> {
		if (!shaft.inShaft) {
			return listOf("Not in a Glacite Mineshaft")
		}
		val code = shaft.code ?: return listOf("Shaft not identified yet")
		if (spots == null || unavailable) {
			return listOf("Corpse spot data unavailable")
		}
		val list = spots.spots(code).takeIf { it.isNotEmpty() } ?: return listOf("No spot data for $code")
		val header = "${shaft.name ?: code}: ${list.size} possible corpse spots"
		if (player == null) {
			return listOf(header) + list.map { "#${it.number} ${it.x}, ${it.y}, ${it.z}" }
		}
		val sorted = list.map { it to metres(it, player) }.sortedWith(compareBy({ it.second }, { it.first.number }))
		return listOf(header) + sorted.map { (spot, d) -> "#${spot.number} ${spot.x}, ${spot.y}, ${spot.z} · $d m" }
	}

	private fun metres(spot: CorpseSpot, player: DoubleArray): Long {
		val dx = spot.x + 0.5 - player[0]
		val dy = spot.y + 0.5 - player[1]
		val dz = spot.z + 0.5 - player[2]
		return floor(sqrt(dx * dx + dy * dy + dz * dz) + 0.5).toLong()
	}
}
