package com.k8bas.skyblockutility.location;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;

import java.util.Map;
import java.util.Optional;

/**
 * Tracks the player's current Skyblock island via the official Hypixel Mod API's
 * ClientboundLocationPacket — the same officially-supported, plugin-message-based mechanism a
 * real currently-maintained mod (Firmament, built against this exact Minecraft version) already
 * uses for the same purpose, rather than scraping the scoreboard or tab list ourselves.
 *
 * The "Hypixel Mod API" mod itself (a separate, required dependency — see fabric.mod.json) does
 * the actual plugin-channel registration/networking; this class only registers handlers against
 * the shared HypixelModAPI.getInstance() singleton it feeds.
 */
public final class IslandTracker {
	// The packet's "mode" field is Hypixel's own internal locraw-style island key — the exact
	// same keys mob_database.json and npc_database.json were built from, so a rule's stored
	// island string always lines up with what this reports without a second translation table.
	private static final Map<String, String> MODE_TO_ISLAND = Map.ofEntries(
			Map.entry("hub", "Hub"),
			Map.entry("combat_1", "Spider's Den"),
			Map.entry("combat_3", "The End"),
			Map.entry("crimson_isle", "Crimson Isle"),
			Map.entry("mining_1", "Gold Mine"),
			Map.entry("mining_2", "Deep Caverns"),
			Map.entry("mining_3", "Dwarven Mines"),
			Map.entry("crystal_hollows", "Crystal Hollows"),
			Map.entry("foraging_1", "The Park"),
			Map.entry("foraging_2", "Moonglade Marsh"),
			Map.entry("jerry", "Jerry"),
			Map.entry("winter", "Jerry"),
			Map.entry("kuudra", "Kuudra"),
			Map.entry("catacombs", "Catacombs"),
			Map.entry("dungeon_hub", "Catacombs"),
			Map.entry("garden", "Garden"),
			Map.entry("lotus_atoll", "Lotus Atoll"),
			Map.entry("foraging_3", "Torrhus Canyon"),
			Map.entry("safari", "Critter Safari"));

	private static volatile String currentIsland = null;

	private IslandTracker() {
	}

	/** @return the player's current island using the same display names the mob/NPC databases
	 *  use (e.g. "Hub", "Dwarven Mines"), or null if unknown/not on a mapped island. */
	public static String getCurrentIsland() {
		return currentIsland;
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> currentIsland = null);

		HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket.class, hello ->
				HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class));

		HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet -> {
			Optional<String> mode = packet.getMode();
			currentIsland = mode.map(MODE_TO_ISLAND::get).orElse(null);
		});
	}
}
