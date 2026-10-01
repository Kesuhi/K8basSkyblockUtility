package com.k8bas.skyblockutility.location;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
public final class IslandTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/location");
	private static final LocationState STATE = new LocationState();
	private static final List<Consumer<LocationSnapshot>> LISTENERS = new CopyOnWriteArrayList<>();
	private static volatile LocationSnapshot snapshot = LocationSnapshot.NONE;

	private IslandTracker() {
	}

	/** The current location; never null. */
	public static LocationSnapshot snapshot() {
		return snapshot;
	}

	/** @return the player's current island using the same display names the mob/NPC databases
	 *  use (e.g. "Hub", "Dwarven Mines"), or null if unknown/not on a mapped island. */
	public static String getCurrentIsland() {
		return snapshot.island();
	}

	/** Called on the client thread after every location change. */
	public static void onChange(Consumer<LocationSnapshot> listener) {
		LISTENERS.add(listener);
	}

	/** The island names with descriptions, for UI pickers and command suggestions (REQ-LOC-09). */
	public static List<Islands.Island> islands() {
		return Islands.all();
	}

	/** Dev-only override (REQ-LOC-08): force an island in runClient or a gametest, or clear it with null. */
	public static void forceIsland(String island) {
		onClientThread(() -> publish(STATE.force(island)));
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onClientThread(() -> publish(STATE.onDisconnect())));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> publish(STATE.onWorldChange()));

		// Hello arrives on every server switch; subscribing again is a no-op for the API, and the
		// location handler below is registered once (EC-LOC-01).
		HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket.class, hello ->
				HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class));

		HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet -> {
			String serverType = packet.getServerType().map(ServerType::name).orElse(null);
			String mode = packet.getMode().orElse(null);
			String map = packet.getMap().orElse(null);
			onClientThread(() -> {
				LocationSnapshot changed = STATE.onLocation(packet.getServerName(), serverType, mode, map);
				if (STATE.newlyUnknownMode() != null) {
					LOGGER.info("Unknown SkyBlock mode '{}' (server {}); no island", mode, packet.getServerName());
				}
				publish(changed);
			});
		});
	}

	/** Runs on the client thread: at once when already there (e.g. tests, events), else queued. */
	private static void onClientThread(Runnable task) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.isSameThread()) {
			task.run();
		} else {
			client.execute(task);
		}
	}

	private static void publish(LocationSnapshot changed) {
		if (changed == null) {
			return;
		}
		snapshot = changed;
		LOGGER.info("Location: island={} mode={} map={} server={} type={}", changed.island(), changed.rawMode(),
				changed.map(), changed.serverName(), changed.serverType());
		for (Consumer<LocationSnapshot> listener : LISTENERS) {
			try {
				listener.accept(changed);
			} catch (RuntimeException e) {
				LOGGER.error("Location listener failed", e);
			}
		}
	}
}
