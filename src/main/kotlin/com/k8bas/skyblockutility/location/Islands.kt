package com.k8bas.skyblockutility.location

/**
 * Hypixel SkyBlock modes and the island names features use (REQ-LOC-02, -03, -09). The names are
 * the exact strings stored in rules and in the mob/NPC data, so they never change without a
 * migration. Hypixel never sends "catacombs" or "jerry"; those keys are gone.
 */
object Islands {
	const val CATACOMBS: String = "Catacombs"
	const val DUNGEON_HUB: String = "Dungeon Hub"

	/** An island name with a short description for pickers in the UI. Nullable as the Java record's were. */
	@JvmRecord
	data class Island(val name: String?, val description: String?) {
		override fun toString(): String = "Island[name=$name, description=$description]"
	}

	private val MODE_TO_ISLAND: Map<String, String> = mapOf(
		"dynamic" to "Private Island",
		"hub" to "Hub",
		"garden" to "Garden",
		"farming_1" to "The Farming Islands",
		"foraging_1" to "The Park",
		"foraging_2" to "Moonglade Marsh",
		"foraging_3" to "Torrhus Canyon",
		"combat_1" to "Spider's Den",
		"combat_3" to "The End",
		"crimson_isle" to "Crimson Isle",
		"mining_1" to "Gold Mine",
		"mining_2" to "Deep Caverns",
		"mining_3" to "Dwarven Mines",
		"crystal_hollows" to "Crystal Hollows",
		"mineshaft" to "Glacite Mineshafts",
		"fishing_1" to "Backwater Bayou",
		"lotus_atoll" to "Lotus Atoll",
		"safari" to "Critter Safari",
		"kuudra" to "Kuudra",
		"winter" to "Jerry",
		"rift" to "The Rift",
		"dark_auction" to "Dark Auction",
		// Dungeons are two islands (D-2): inside a run, and the lobby.
		"dungeon" to CATACOMBS,
		"dungeon_hub" to DUNGEON_HUB,
	)

	/** In picker order. */
	private val DESCRIPTIONS: Map<String, String> = linkedMapOf(
		"Hub" to "the main hub",
		"Private Island" to "your own island",
		"Garden" to "the Garden",
		"The Farming Islands" to "the Barn and Mushroom Desert",
		"The Park" to "the first foraging island",
		"Moonglade Marsh" to "the foraging marsh (Galatea)",
		"Torrhus Canyon" to "the foraging canyon",
		"Spider's Den" to "the spider island",
		"The End" to "the End island",
		"Crimson Isle" to "the Nether island",
		"Gold Mine" to "the first mine",
		"Deep Caverns" to "the deep mine",
		"Dwarven Mines" to "the Dwarven Mines and Glacite Tunnels",
		"Crystal Hollows" to "the crystal mine",
		"Glacite Mineshafts" to "inside a mineshaft",
		"Backwater Bayou" to "the fishing bayou",
		"Lotus Atoll" to "the atoll",
		"Critter Safari" to "the safari",
		"Kuudra" to "inside a Kuudra fight",
		"Jerry" to "Jerry's Workshop (winter)",
		"The Rift" to "the Rift",
		"Dark Auction" to "the Dark Auction room",
		DUNGEON_HUB to "dungeon lobby",
		CATACOMBS to "inside dungeon runs",
	)

	/** The island for a mode, or null for an unmapped or missing mode. */
	@JvmStatic
	fun islandFor(mode: String?): String? = if (mode == null) null else MODE_TO_ISLAND[mode]

	/** Every island name exactly once, with its description, for UI pickers (unmodifiable). */
	@JvmStatic
	fun all(): List<Island> = DESCRIPTIONS.entries.stream().map { Island(it.key, it.value) }.toList()
}
