package com.k8bas.skyblockutility.location

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import net.minecraft.client.Minecraft
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * Tracks the player's location via the official Hypixel Mod API's ClientboundLocationPacket — the
 * only source (REQ-LOC-01): no /locraw, no chat or scoreboard parsing. The "Hypixel Mod API" mod
 * (a separate, required dependency — see fabric.mod.json) does the plugin-channel networking; this
 * class only registers handlers against the shared HypixelModAPI.getInstance() singleton.
 *
 * The current location is one immutable snapshot (REQ-LOC-04), readable from any thread.
 * Subscribers hear about every change on the client thread: a new server or mode, a world change
 * before the next event (the island is cleared, REQ-LOC-07) and a disconnect (REQ-LOC-05).
 */
object IslandTracker {
	private val LOGGER: Logger = LoggerFactory.getLogger("k8bas_skyblock_utility/location")
	private val STATE = LocationState()
	private val LISTENERS: MutableList<Consumer<LocationSnapshot>> = CopyOnWriteArrayList()

	@Volatile
	private var snapshot: LocationSnapshot = LocationSnapshot.NONE

	/** The current location; never null. */
	@JvmStatic
	fun snapshot(): LocationSnapshot = snapshot

	/**
	 * @return the player's current island using the same display names the mob/NPC databases
	 *  use (e.g. "Hub", "Dwarven Mines"), or null if unknown/not on a mapped island.
	 */
	@JvmStatic
	fun getCurrentIsland(): String? = snapshot.island

	/** Called on the client thread after every location change. */
	@JvmStatic
	fun onChange(listener: Consumer<LocationSnapshot>) {
		LISTENERS.add(listener)
	}

	/** The island names with descriptions, for UI pickers and command suggestions (REQ-LOC-09). */
	@JvmStatic
	fun islands(): List<Islands.Island> = Islands.all()

	/** Dev-only override (REQ-LOC-08): force an island in runClient or a gametest, or clear it with null. */
	@JvmStatic
	fun forceIsland(island: String?) {
		onClientThread { publish(STATE.force(island)) }
	}

	@JvmStatic
	fun register() {
		ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> onClientThread { publish(STATE.onDisconnect()) } }
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> publish(STATE.onWorldChange()) }

		// Hello arrives on every server switch; subscribing again is a no-op for the API, and the
		// location handler below is registered once (EC-LOC-01).
		HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket::class.java) {
			HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket::class.java)
		}

		HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket::class.java) { packet ->
			val serverType = packet.serverType.map { it.name() }.orElse(null)
			val mode = packet.mode.orElse(null)
			val map = packet.map.orElse(null)
			onClientThread {
				val changed = STATE.onLocation(packet.serverName, serverType, mode, map)
				if (STATE.newlyUnknownMode() != null) {
					LOGGER.info("Unknown SkyBlock mode '{}' (server {}); no island", mode, packet.serverName)
				}
				publish(changed)
			}
		}
	}

	/** Runs on the client thread: at once when already there (e.g. tests, events), else queued. */
	private fun onClientThread(task: Runnable) {
		val client: Minecraft? = Minecraft.getInstance()
		if (client == null || client.isSameThread) {
			task.run()
		} else {
			client.execute(task)
		}
	}

	private fun publish(changed: LocationSnapshot?) {
		if (changed == null) {
			return
		}
		snapshot = changed
		LOGGER.info(
			"Location: island={} mode={} map={} server={} type={}",
			changed.island, changed.rawMode, changed.map, changed.serverName, changed.serverType,
		)
		for (listener in LISTENERS) {
			try {
				listener.accept(changed)
			} catch (e: RuntimeException) {
				LOGGER.error("Location listener failed", e)
			}
		}
	}
}
