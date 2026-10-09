package com.k8bas.skyblockutility.location

/**
 * The location rules without any game or network dependency, so they can be unit-tested
 * (REQ-LOC-04 to -07). Each input returns the new snapshot when subscribers must be told, or null.
 * Not thread-safe: IslandTracker drives it on the client thread. Internal (was package-private); its
 * members stay public so the Java test reads them by their names.
 */
internal class LocationState {
	private var current: LocationSnapshot = LocationSnapshot.NONE
	private val loggedUnknownModes = HashSet<String?>()

	/** Set by the last onLocation when its mode was unmapped and not yet reported this session. */
	private var newlyUnknownMode: String? = null

	fun current(): LocationSnapshot = current

	/** A location event. Fires when the server name or the mode differs from the current snapshot. */
	fun onLocation(serverName: String?, serverType: String?, mode: String?, map: String?): LocationSnapshot? {
		val onSkyBlock = SKYBLOCK.equals(serverType, ignoreCase = true)
		val island = if (onSkyBlock) Islands.islandFor(mode) else null
		newlyUnknownMode = if (onSkyBlock && mode != null && island == null && loggedUnknownModes.add(mode)) mode else null
		val next = LocationSnapshot(mode, map, serverName, serverType, island, onSkyBlock)
		val changed = serverName != current.serverName || mode != current.rawMode || current.island == null && island != null
		current = next
		return if (changed) next else null
	}

	/** The world changed (e.g. a server transfer) before a new location event: forget the island. */
	fun onWorldChange(): LocationSnapshot? {
		if (current.island == null && !current.onSkyBlock) {
			return null
		}
		current = LocationSnapshot(current.rawMode, current.map, current.serverName, current.serverType, null, false)
		return current
	}

	fun onDisconnect(): LocationSnapshot? {
		if (current == LocationSnapshot.NONE) {
			return null
		}
		current = LocationSnapshot.NONE
		return current
	}

	/** Dev-only override (REQ-LOC-08): pretend to be on the island, or clear it with null. */
	fun force(island: String?): LocationSnapshot {
		current = if (island == null) LocationSnapshot.NONE else LocationSnapshot("forced", null, "forced", SKYBLOCK, island, true)
		return current
	}

	/** The unmapped mode of the last event if it is the first time this session, else null. */
	fun newlyUnknownMode(): String? = newlyUnknownMode

	private companion object {
		const val SKYBLOCK: String = "SKYBLOCK"
	}
}
