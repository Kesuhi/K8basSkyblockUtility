package com.k8bas.skyblockutility.module.hotspot

import java.util.function.Consumer

/**
 * The fishing hotspot texts in one place (T3.6, REQ-HOT-01, REQ-HOT-02): the `HOTSPOT` name tag and the buff line
 * under it. Colour codes and outer spaces are ignored. A buff text no pattern knows is Unknown and is reported once
 * through [unknown].
 */
class HotspotText(private val unknown: Consumer<String>) {
	/** A hotspot's buff, with its ring colour (REQ-HOT-05). */
	enum class Buff(val color: Int) {
		SEA_CREATURE_CHANCE(0x00AAAA),
		FISHING_SPEED(0x55FFFF),
		DOUBLE_HOOK_CHANCE(0x5555FF),
		TREASURE_CHANCE(0xFFAA00),
		TROPHY_FISH_CHANCE(0xFFAA00),
		SHARD(0xFFFF55),
		UNKNOWN(0xFFFFFF),
	}

	private val reported = HashSet<String>()

	/** The buff a line names; Unknown, reported once per text, for any other. */
	fun buff(line: String): Buff {
		val text = plain(line)
		val buff = PATTERNS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second ?: Buff.UNKNOWN
		if (buff == Buff.UNKNOWN && reported.add(text)) {
			unknown.accept(text)
		}
		return buff
	}

	companion object {
		private const val NAME = "HOTSPOT"
		private val CODES = Regex("§.")

		private val PATTERNS = listOf(
			Regex("Sea Creature Chance$") to Buff.SEA_CREATURE_CHANCE,
			Regex("Fishing Speed$") to Buff.FISHING_SPEED,
			Regex("Double Hook Chance$") to Buff.DOUBLE_HOOK_CHANCE,
			Regex("Treasure Chance$") to Buff.TREASURE_CHANCE,
			Regex("Trophy Fish Chance$") to Buff.TROPHY_FISH_CHANCE,
			Regex("^Chance of .+ Shard$") to Buff.SHARD,
		)

		/** Whether a line names a buff a pattern knows (nothing is reported). */
		@JvmStatic
		fun known(line: String): Boolean = plain(line).let { text -> PATTERNS.any { (pattern, _) -> pattern.containsMatchIn(text) } }

		/** Whether a name tag is a hotspot's: exactly `HOTSPOT` once colour codes and outer spaces are gone. */
		@JvmStatic
		fun isHotspot(name: String): Boolean = plain(name) == NAME

		private fun plain(text: String): String = CODES.replace(text, "").trim()
	}
}
