package com.k8bas.skyblockutility.skyblock

import com.k8bas.skyblockutility.location.IslandTracker
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft

/**
 * Drives the game-state readers (T3.0a). Each client tick lets every reader read once; a location change
 * (T1.9), a world change and a disconnect clear them all, so nothing from the previous server is reported
 * (REQ-GS-15). The readers use these events and own none of them.
 */
object GameStateReaders {
	/** Asserts the client thread in dev builds (REQ-GS-11, AC-GS-11); a no-op in production. */
	internal val CLIENT_THREAD = Runnable {
		if (DEV) {
			val client = Minecraft.getInstance()
			check(client == null || client.isSameThread) { "Game-state readers run on the client thread only" }
		}
	}

	private val DEV: Boolean by lazy { FabricLoader.getInstance().isDevelopmentEnvironment }

	@JvmStatic
	fun register() {
		ClientTickEvents.END_CLIENT_TICK.register {
			TabReader.tick()
			SidebarReader.tick()
			InventoryReader.tick()
			ChatReader.tick()
		}
		// IslandTracker calls its listeners on the client thread.
		IslandTracker.onChange { clearAll() }
		// It does not publish a world change it does not see (e.g. off SkyBlock), so the readers listen too.
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> clearAll() }
		// A disconnect may be signalled on the network thread.
		ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { clearAll() } }
		// Chat and action bar (T3.0g): delivered as they arrive; nothing is cached, so nothing to clear.
		ChatReader.register()
	}

	private fun clearAll() {
		TabReader.clear()
		SidebarReader.clear()
		InventoryReader.clear()
	}
}
