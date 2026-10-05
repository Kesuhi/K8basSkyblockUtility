package com.k8bas.skyblockutility.module.npcsearch

import java.util.Collections

/**
 * The beam colour of an NPC waypoint (REQ-NPCWP-05, D-15): the rule's own beam colour, otherwise its
 * island's colour (edited, or the documented default below), otherwise the global default. A rule's
 * `color` is its glow and label colour and never feeds into the beam (REQ-NPCWP-07).
 */
object WaypointColors {
	/** The mod's long-standing NPC green, for a blank island or one without a colour. */
	const val GLOBAL_DEFAULT: Int = 0x0AA351

	/** One default per island name of [com.k8bas.skyblockutility.location.Islands] (documented in SPEC.md, REQ-NPCWP-05). */
	@JvmField
	val ISLAND_DEFAULTS: Map<String, Int> = Collections.unmodifiableMap(
		linkedMapOf(
			"Hub" to 0x55FFFF,
			"Private Island" to 0x55FF55,
			"Garden" to 0x00AA00,
			"The Farming Islands" to 0xFFFF55,
			"The Park" to 0x2E8B57,
			"Moonglade Marsh" to 0x6B8E23,
			"Torrhus Canyon" to 0xCD853F,
			"Spider's Den" to 0xAA00AA,
			"The End" to 0xFF55FF,
			"Crimson Isle" to 0xFF5555,
			"Gold Mine" to 0xFFAA00,
			"Deep Caverns" to 0x5555FF,
			"Dwarven Mines" to 0x00AAAA,
			"Crystal Hollows" to 0xB266FF,
			"Glacite Mineshafts" to 0xAEEBFF,
			"Backwater Bayou" to 0x3C8DBC,
			"Lotus Atoll" to 0xFF88CC,
			"Critter Safari" to 0xD2B48C,
			"Kuudra" to 0xAA0000,
			"Jerry" to 0xFFFFFF,
			"The Rift" to 0xBF40BF,
			"Dark Auction" to 0x8B008B,
			"Dungeon Hub" to 0xAAAAAA,
			"Catacombs" to 0x555555,
		),
	)

	/**
	 * The resolved beam colour as 0xRRGGBB.
	 *
	 * @param islandColors the player's edited island colours; an island not in it uses its default. Read
	 *                     only when the rule has no beam colour of its own, as before (so it may be null then)
	 */
	@JvmStatic
	fun beamColor(rule: NpcRule, islandColors: Map<String, Int?>?): Int {
		val beam = rule.beamColor
		if (beam != null) {
			return beam and 0xFFFFFF
		}
		// Java's String.trim: code points up to U+0020.
		val island = rule.island?.trim { it <= ' ' } ?: ""
		if (island.isEmpty()) {
			return GLOBAL_DEFAULT
		}
		val edited = islandColors!![island]
		if (edited != null) {
			return edited and 0xFFFFFF
		}
		return ISLAND_DEFAULTS.getOrDefault(island, GLOBAL_DEFAULT)
	}
}
