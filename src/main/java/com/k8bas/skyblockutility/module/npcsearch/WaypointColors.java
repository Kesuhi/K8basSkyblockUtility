package com.k8bas.skyblockutility.module.npcsearch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The beam colour of an NPC waypoint (REQ-NPCWP-05, D-15): the rule's own beam colour, otherwise its
 * island's colour (edited, or the documented default below), otherwise the global default. A rule's
 * {@code color} is its glow and label colour and never feeds into the beam (REQ-NPCWP-07).
 */
public final class WaypointColors {
	/** The mod's long-standing NPC green, for a blank island or one without a colour. */
	public static final int GLOBAL_DEFAULT = 0x0AA351;

	/** One default per island name of {@link com.k8bas.skyblockutility.location.Islands} (documented in SPEC.md, REQ-NPCWP-05). */
	public static final Map<String, Integer> ISLAND_DEFAULTS;

	static {
		Map<String, Integer> defaults = new LinkedHashMap<>();
		defaults.put("Hub", 0x55FFFF);
		defaults.put("Private Island", 0x55FF55);
		defaults.put("Garden", 0x00AA00);
		defaults.put("The Farming Islands", 0xFFFF55);
		defaults.put("The Park", 0x2E8B57);
		defaults.put("Moonglade Marsh", 0x6B8E23);
		defaults.put("Torrhus Canyon", 0xCD853F);
		defaults.put("Spider's Den", 0xAA00AA);
		defaults.put("The End", 0xFF55FF);
		defaults.put("Crimson Isle", 0xFF5555);
		defaults.put("Gold Mine", 0xFFAA00);
		defaults.put("Deep Caverns", 0x5555FF);
		defaults.put("Dwarven Mines", 0x00AAAA);
		defaults.put("Crystal Hollows", 0xB266FF);
		defaults.put("Glacite Mineshafts", 0xAEEBFF);
		defaults.put("Backwater Bayou", 0x3C8DBC);
		defaults.put("Lotus Atoll", 0xFF88CC);
		defaults.put("Critter Safari", 0xD2B48C);
		defaults.put("Kuudra", 0xAA0000);
		defaults.put("Jerry", 0xFFFFFF);
		defaults.put("The Rift", 0xBF40BF);
		defaults.put("Dark Auction", 0x8B008B);
		defaults.put("Dungeon Hub", 0xAAAAAA);
		defaults.put("Catacombs", 0x555555);
		ISLAND_DEFAULTS = Collections.unmodifiableMap(defaults);
	}

	private WaypointColors() {
	}

	/**
	 * The resolved beam colour as 0xRRGGBB.
	 *
	 * @param islandColors the player's edited island colours; an island not in it uses its default
	 */
	public static int beamColor(NpcRule rule, Map<String, Integer> islandColors) {
		if (rule.beamColor != null) {
			return rule.beamColor & 0xFFFFFF;
		}
		String island = rule.island == null ? "" : rule.island.trim();
		if (island.isEmpty()) {
			return GLOBAL_DEFAULT;
		}
		Integer edited = islandColors.get(island);
		if (edited != null) {
			return edited & 0xFFFFFF;
		}
		return ISLAND_DEFAULTS.getOrDefault(island, GLOBAL_DEFAULT);
	}
}
