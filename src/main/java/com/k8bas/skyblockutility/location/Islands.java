package com.k8bas.skyblockutility.location;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hypixel SkyBlock modes and the island names features use (REQ-LOC-02, -03, -09). The names are
 * the exact strings stored in rules and in the mob/NPC data, so they never change without a
 * migration. Hypixel never sends "catacombs" or "jerry"; those keys are gone.
 */
public final class Islands {
	public static final String CATACOMBS = "Catacombs";
	public static final String DUNGEON_HUB = "Dungeon Hub";

	/** An island name with a short description for pickers in the UI. */
	public record Island(String name, String description) {
	}

	private static final Map<String, String> MODE_TO_ISLAND = Map.ofEntries(
			Map.entry("dynamic", "Private Island"),
			Map.entry("hub", "Hub"),
			Map.entry("garden", "Garden"),
			Map.entry("farming_1", "The Farming Islands"),
			Map.entry("foraging_1", "The Park"),
			Map.entry("foraging_2", "Moonglade Marsh"),
			Map.entry("foraging_3", "Torrhus Canyon"),
			Map.entry("combat_1", "Spider's Den"),
			Map.entry("combat_3", "The End"),
			Map.entry("crimson_isle", "Crimson Isle"),
			Map.entry("mining_1", "Gold Mine"),
			Map.entry("mining_2", "Deep Caverns"),
			Map.entry("mining_3", "Dwarven Mines"),
			Map.entry("crystal_hollows", "Crystal Hollows"),
			Map.entry("mineshaft", "Glacite Mineshafts"),
			Map.entry("fishing_1", "Backwater Bayou"),
			Map.entry("lotus_atoll", "Lotus Atoll"),
			Map.entry("safari", "Critter Safari"),
			Map.entry("kuudra", "Kuudra"),
			Map.entry("winter", "Jerry"),
			Map.entry("rift", "The Rift"),
			Map.entry("dark_auction", "Dark Auction"),
			// Dungeons are two islands (D-2): inside a run, and the lobby.
			Map.entry("dungeon", CATACOMBS),
			Map.entry("dungeon_hub", DUNGEON_HUB));

	private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

	static {
		DESCRIPTIONS.put("Hub", "the main hub");
		DESCRIPTIONS.put("Private Island", "your own island");
		DESCRIPTIONS.put("Garden", "the Garden");
		DESCRIPTIONS.put("The Farming Islands", "the Barn and Mushroom Desert");
		DESCRIPTIONS.put("The Park", "the first foraging island");
		DESCRIPTIONS.put("Moonglade Marsh", "the foraging marsh (Galatea)");
		DESCRIPTIONS.put("Torrhus Canyon", "the foraging canyon");
		DESCRIPTIONS.put("Spider's Den", "the spider island");
		DESCRIPTIONS.put("The End", "the End island");
		DESCRIPTIONS.put("Crimson Isle", "the Nether island");
		DESCRIPTIONS.put("Gold Mine", "the first mine");
		DESCRIPTIONS.put("Deep Caverns", "the deep mine");
		DESCRIPTIONS.put("Dwarven Mines", "the Dwarven Mines and Glacite Tunnels");
		DESCRIPTIONS.put("Crystal Hollows", "the crystal mine");
		DESCRIPTIONS.put("Glacite Mineshafts", "inside a mineshaft");
		DESCRIPTIONS.put("Backwater Bayou", "the fishing bayou");
		DESCRIPTIONS.put("Lotus Atoll", "the atoll");
		DESCRIPTIONS.put("Critter Safari", "the safari");
		DESCRIPTIONS.put("Kuudra", "inside a Kuudra fight");
		DESCRIPTIONS.put("Jerry", "Jerry's Workshop (winter)");
		DESCRIPTIONS.put("The Rift", "the Rift");
		DESCRIPTIONS.put("Dark Auction", "the Dark Auction room");
		DESCRIPTIONS.put(DUNGEON_HUB, "dungeon lobby");
		DESCRIPTIONS.put(CATACOMBS, "inside dungeon runs");
	}

	private Islands() {
	}

	/** The island for a mode, or null for an unmapped or missing mode. */
	public static String islandFor(String mode) {
		return mode == null ? null : MODE_TO_ISLAND.get(mode);
	}

	/** Every island name exactly once, with its description, for UI pickers. */
	public static List<Island> all() {
		return DESCRIPTIONS.entrySet().stream().map(entry -> new Island(entry.getKey(), entry.getValue())).toList();
	}
}
