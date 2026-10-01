package com.k8bas.skyblockutility.location;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The location rules without any game or network dependency, so they can be unit-tested
 * (REQ-LOC-04 to -07). Each input returns the new snapshot when subscribers must be told, or null.
 * Not thread-safe: IslandTracker drives it on the client thread.
 */
final class LocationState {
	private static final String SKYBLOCK = "SKYBLOCK";

	private LocationSnapshot current = LocationSnapshot.NONE;
	private final Set<String> loggedUnknownModes = new HashSet<>();
	/** Set by the last onLocation when its mode was unmapped and not yet reported this session. */
	private String newlyUnknownMode;

	LocationSnapshot current() {
		return current;
	}

	/** A location event. Fires when the server name or the mode differs from the current snapshot. */
	LocationSnapshot onLocation(String serverName, String serverType, String mode, String map) {
		boolean onSkyBlock = SKYBLOCK.equalsIgnoreCase(serverType);
		String island = onSkyBlock ? Islands.islandFor(mode) : null;
		newlyUnknownMode = onSkyBlock && mode != null && island == null && loggedUnknownModes.add(mode) ? mode : null;
		LocationSnapshot next = new LocationSnapshot(mode, map, serverName, serverType, island, onSkyBlock);
		boolean changed = !Objects.equals(serverName, current.serverName()) || !Objects.equals(mode, current.rawMode())
				|| current.island() == null && island != null;
		current = next;
		return changed ? next : null;
	}

	/** The world changed (e.g. a server transfer) before a new location event: forget the island. */
	LocationSnapshot onWorldChange() {
		if (current.island() == null && !current.onSkyBlock()) {
			return null;
		}
		current = new LocationSnapshot(current.rawMode(), current.map(), current.serverName(), current.serverType(), null, false);
		return current;
	}

	LocationSnapshot onDisconnect() {
		if (current.equals(LocationSnapshot.NONE)) {
			return null;
		}
		current = LocationSnapshot.NONE;
		return current;
	}

	/** Dev-only override (REQ-LOC-08): pretend to be on the island, or clear it with null. */
	LocationSnapshot force(String island) {
		current = island == null ? LocationSnapshot.NONE
				: new LocationSnapshot("forced", null, "forced", SKYBLOCK, island, true);
		return current;
	}

	/** The unmapped mode of the last event if it is the first time this session, else null. */
	String newlyUnknownMode() {
		return newlyUnknownMode;
	}
}
