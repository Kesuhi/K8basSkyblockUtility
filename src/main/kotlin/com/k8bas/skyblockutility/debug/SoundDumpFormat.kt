package com.k8bas.skyblockutility.debug

import java.util.Locale
import kotlin.math.sqrt

/**
 * The line the armed sound dump writes for one sound the client plays (T3.3c, REQ-GS-13):
 * `[K8BAS-DUMP] sound <n> t=+<ms since armed>ms tick=<game time> id=<event> file=<sound file> src=<category>
 * pos=<x,y,z> rel=<relative> att=<attenuation> vol=<volume> pitch=<pitch> range=<range> player=<x,y,z> d=<distance>`.
 * Enough to tell the Organ Donor ding from other sounds, and to see where it is played and how often.
 */
object SoundDumpFormat {
	/** One sound as the dump sees it; plain values, so it is tested without a game. */
	@JvmRecord
	data class Sound(
		val id: String,
		val file: String,
		val source: String,
		val x: Double,
		val y: Double,
		val z: Double,
		val relative: Boolean,
		val attenuation: String,
		val volume: Float,
		val pitch: Float,
		val range: Float,
	)

	/** [player] is the player's x, y, z, or null without a player (then the distance is a dash too). */
	@JvmStatic
	fun line(capture: Int, sinceArmedMs: Long, tick: Long, sound: Sound, player: DoubleArray?): String {
		val where = if (player == null) "player=- d=-" else String.format(
			Locale.ROOT, "player=%.2f,%.2f,%.2f d=%.2f", player[0], player[1], player[2],
			sqrt(square(sound.x - player[0]) + square(sound.y - player[1]) + square(sound.z - player[2])),
		)
		return String.format(
			Locale.ROOT, "%s sound %d t=+%dms tick=%d id=%s file=%s src=%s pos=%.2f,%.2f,%.2f rel=%b att=%s vol=%.2f pitch=%.3f range=%.1f %s",
			DebugCommand.DUMP_TAG, capture, sinceArmedMs, tick, sound.id, sound.file, sound.source, sound.x, sound.y, sound.z,
			sound.relative, sound.attenuation, sound.volume, sound.pitch, sound.range, where,
		)
	}

	private fun square(value: Double): Double = value * value
}
