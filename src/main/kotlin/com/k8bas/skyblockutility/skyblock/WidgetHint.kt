package com.k8bas.skyblockutility.skyblock

import net.minecraft.network.chat.Component

/**
 * The one hint that tells the player a Hypixel tab widget is off and how to turn it on (REQ-GS-18). The
 * mineshaft alert, the bestiary HUD and the SkyBlock XP optimizer all use this key; none writes its own
 * command. The wording names `/widget`, which SkyHanni sends for this (Skyblocker sends `/widgets`, NEU
 * `/tab`); it stays UNVERIFIED until the G3 field check (Q-MSA-04, R24).
 */
object WidgetHint {
	const val KEY = "k8bas_skyblock_utility.hint.tab_widget"

	/** The hint for the widget whose header is [widget], e.g. "Frozen Corpses". */
	@JvmStatic
	fun text(widget: String): Component = Component.translatable(KEY, widget)
}
