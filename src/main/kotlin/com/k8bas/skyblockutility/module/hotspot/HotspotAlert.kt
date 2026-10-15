package com.k8bas.skyblockutility.module.hotspot

/** The sounds the "hotspot gone" warning may play (REQ-HOT-10): a short list of vanilla sounds. */
enum class HotspotSound(val label: String) {
	PLING("Note block pling"),
	BELL("Bell"),
	CHIME("Chime"),
	EXPERIENCE("Experience orb"),
	ANVIL("Anvil"),
}

/**
 * The "hotspot gone" warning's channels (T3.6b, REQ-HOT-10, REQ-HOT-11): the title "Hotspot gone!" with the buff as
 * subtitle, one local sound, a local chat line and an optional notice, each behind its own toggle. Everything goes
 * through [Output], which shows things locally only, so this is tested with a recording fake (AC-HOT-05).
 */
object HotspotAlert {
	const val TITLE = "Hotspot gone!"

	/** Where a warning goes; every method is local to this client. */
	interface Output {
		fun title(title: String, subtitle: String)

		fun sound(sound: HotspotSound, volume: Float)

		fun chat(line: String)

		fun notice(title: String, body: String)
	}

	/** What the warning calls the hotspot's buff. */
	@JvmStatic
	fun buffName(buff: HotspotText.Buff): String = when (buff) {
		HotspotText.Buff.SEA_CREATURE_CHANCE -> "Sea Creature Chance"
		HotspotText.Buff.FISHING_SPEED -> "Fishing Speed"
		HotspotText.Buff.DOUBLE_HOOK_CHANCE -> "Double Hook Chance"
		HotspotText.Buff.TREASURE_CHANCE -> "Treasure Chance"
		HotspotText.Buff.TROPHY_FISH_CHANCE -> "Trophy Fish Chance"
		HotspotText.Buff.SHARD -> "Shard"
		HotspotText.Buff.UNKNOWN -> "Fishing"
	}

	/** The chat line, e.g. "Sea Creature Chance hotspot is gone." */
	@JvmStatic
	fun chatLine(buff: HotspotText.Buff): String = buffName(buff) + " hotspot is gone."

	/** Sends a warning for a hotspot with [buff] to each channel [config] has on. */
	@JvmStatic
	fun fire(buff: HotspotText.Buff, config: HotspotConfig, output: Output) {
		val name = buffName(buff)
		if (config.warnTitle) {
			output.title(TITLE, "$name hotspot")
		}
		if (config.warnSound) {
			output.sound(config.sound ?: HotspotSound.PLING, config.soundVolume.coerceIn(0, 100) / 100f)
		}
		if (config.warnChat) {
			output.chat(chatLine(buff))
		}
		if (config.warnNotice) {
			output.notice(TITLE, "$name hotspot")
		}
	}
}
