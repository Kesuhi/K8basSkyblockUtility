package com.k8bas.skyblockutility.location;

/**
 * Where the player is, as last reported by the Hypixel Mod API location event (REQ-LOC-04).
 * Immutable, so it can be read from any thread.
 *
 * @param rawMode    the event's mode, e.g. "dungeon_hub", even when it maps to no island; null if absent
 * @param map        the event's map name; null if absent
 * @param serverName the server instance, e.g. "m000XX"; null when not connected to Hypixel
 * @param serverType the server type name, e.g. "SKYBLOCK"; null if absent
 * @param island     the mapped island name (exact strings stored in rules and data), or null
 * @param onSkyBlock whether the server type is SkyBlock, also for modes that map to no island
 */
public record LocationSnapshot(String rawMode, String map, String serverName, String serverType, String island, boolean onSkyBlock) {
	public static final LocationSnapshot NONE = new LocationSnapshot(null, null, null, null, null, false);
}
