package com.k8bas.skyblockutility.module.corpsespots

import kotlin.math.sqrt

/**
 * The Organ Donor ding (T3.3b, REQ-CORPSE-11, R41 (c)): the accessory plays a harp note when an unlooted Frozen
 * Corpse is within 20 blocks, at the player's own position, higher the closer the corpse is (captured at G3: pitch
 * about 1.1 at 17 blocks, 1.73 at 10, 1.97 within 3); the "Corpse ding" HUD element shows it (R43 (b)). A sound counts
 * as a ding when it is that harp, in the block category, within [NEAR] blocks of the player, in a Glacite Mineshaft.
 * Only its pitch is kept, as a warmth; its position is never used to pick a spot. The warmth holds [HOLD_MS] after the
 * last ding (dings repeat every 0.55–1.0 s). Not thread-safe: the client thread drives it.
 */
class DingHint {
	/** How warm the last ding was, from its pitch, with the word the HUD shows. */
	enum class Warmth(val label: String) {
		FAR("far"),
		CLOSE("close"),
		VERY_CLOSE("very close"),
	}

	private var lastWarmth: Warmth? = null
	private var lastAt = 0L

	/** A sound the client plays; true when it is a ding (then the warmth is updated). */
	fun heard(id: String, source: String, x: Double, y: Double, z: Double, pitch: Float, player: DoubleArray, inShaft: Boolean, nowMs: Long): Boolean {
		val dx = x - player[0]
		val dy = y - player[1]
		val dz = z - player[2]
		if (!inShaft || id != SOUND || source != SOURCE || sqrt(dx * dx + dy * dy + dz * dz) > NEAR) {
			return false
		}
		lastWarmth = warmth(pitch)
		lastAt = nowMs
		return true
	}

	/** The last ding's warmth while it holds, else null. */
	fun warmth(nowMs: Long): Warmth? = lastWarmth?.takeIf { nowMs - lastAt < HOLD_MS }

	fun clear() {
		lastWarmth = null
	}

	companion object {
		const val SOUND = "minecraft:block.note_block.harp"
		const val SOURCE = "block"

		/** The capture's dings were up to 3.5 blocks from the player. */
		const val NEAR = 4.0
		const val HOLD_MS = 2_000L

		/** The HUD line for [warmth]: "Corpse ding: very close", the warmth in yellow. */
		@JvmStatic
		fun hudLine(warmth: Warmth): String = "§6Corpse ding: §e${warmth.label}"

		@JvmStatic
		fun warmth(pitch: Float): Warmth = when {
			pitch >= 1.9f -> Warmth.VERY_CLOSE
			pitch >= 1.6f -> Warmth.CLOSE
			else -> Warmth.FAR
		}
	}
}
