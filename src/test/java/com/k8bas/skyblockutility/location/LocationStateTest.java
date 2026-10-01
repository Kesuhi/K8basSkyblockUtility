package com.k8bas.skyblockutility.location;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationStateTest {
	private final LocationState state = new LocationState();
	private final List<LocationSnapshot> events = new ArrayList<>();

	private void location(String server, String type, String mode, String map) {
		LocationSnapshot changed = state.onLocation(server, type, mode, map);
		if (changed != null) {
			events.add(changed);
		}
	}

	/** AC-LOC-01: every mode seen in the user's logs maps to its island; catacombs and jerry give none. */
	@Test
	void everyLoggedModeMapsToItsIsland() {
		Map<String, String> expected = Map.ofEntries(
				Map.entry("mining_3", "Dwarven Mines"), Map.entry("mineshaft", "Glacite Mineshafts"),
				Map.entry("dynamic", "Private Island"), Map.entry("hub", "Hub"), Map.entry("garden", "Garden"),
				Map.entry("farming_1", "The Farming Islands"), Map.entry("crimson_isle", "Crimson Isle"),
				Map.entry("dungeon_hub", "Dungeon Hub"), Map.entry("dungeon", "Catacombs"),
				Map.entry("foraging_2", "Moonglade Marsh"), Map.entry("combat_3", "The End"),
				Map.entry("fishing_1", "Backwater Bayou"), Map.entry("combat_1", "Spider's Den"),
				Map.entry("mining_1", "Gold Mine"), Map.entry("crystal_hollows", "Crystal Hollows"));
		expected.forEach((mode, island) -> assertEquals(island, Islands.islandFor(mode), mode));
		assertNull(Islands.islandFor("catacombs"));
		assertNull(Islands.islandFor("jerry"));
		assertEquals("Jerry", Islands.islandFor("winter"));
		assertNull(Islands.islandFor(null));
	}

	/** AC-LOC-05, EC-LOC-02, EC-LOC-10: a repeat gives no event; every new shaft (server) does. */
	@Test
	void changesFireOncePerNewServerOrMode() {
		location("mini1", "SKYBLOCK", "hub", "Hub");
		location("mini1", "SKYBLOCK", "hub", "Hub");
		location("mini2", "SKYBLOCK", "mineshaft", "Mineshaft");
		location("mini3", "SKYBLOCK", "mineshaft", "Mineshaft");

		assertEquals(3, events.size());
		assertEquals(new LocationSnapshot("hub", "Hub", "mini1", "SKYBLOCK", "Hub", true), events.get(0));
		assertEquals("Glacite Mineshafts", events.get(1).island());
		assertEquals("mini3", events.get(2).serverName());
		assertEquals(events.get(2), state.current());
	}

	/** AC-LOC-06: an unknown mode gives no island, keeps the raw mode and is reported once. */
	@Test
	void anUnknownModeIsReportedOnce() {
		location("mini1", "SKYBLOCK", "limbo", null);
		assertEquals("limbo", state.newlyUnknownMode());
		location("mini2", "SKYBLOCK", "limbo", null);
		assertNull(state.newlyUnknownMode(), "the second time is not reported again");
		assertNull(state.current().island());
		assertEquals("limbo", state.current().rawMode());
		assertTrue(state.current().onSkyBlock(), "unmapped SkyBlock modes still count as SkyBlock");
	}

	/** AC-LOC-07, EC-LOC-03: disconnect and a world change without a new event clear the island. */
	@Test
	void disconnectAndWorldChangeClearTheIsland() {
		location("mini1", "SKYBLOCK", "dungeon_hub", "Dungeon Hub");
		LocationSnapshot worldChange = state.onWorldChange();
		assertNotNull(worldChange, "subscribers are told");
		assertNull(worldChange.island());
		assertFalse(worldChange.onSkyBlock());

		location("mini7", "SKYBLOCK", "dungeon", "Catacombs");
		assertEquals("Catacombs", state.current().island(), "the run's own event sets the new island");

		LocationSnapshot disconnect = state.onDisconnect();
		assertEquals(LocationSnapshot.NONE, disconnect);
		assertNull(state.onDisconnect(), "a second disconnect is not reported");
	}

	/** After a world change, the same server's event brings the island back. */
	@Test
	void theSameServerRestoresTheIslandAfterAWorldChange() {
		location("mini1", "SKYBLOCK", "hub", "Hub");
		state.onWorldChange();
		events.clear();
		location("mini1", "SKYBLOCK", "hub", "Hub");
		assertEquals(1, events.size());
		assertEquals("Hub", state.current().island());
	}

	/** AC-LOC-12, EC-LOC-04: non-SkyBlock servers give no island. */
	@Test
	void otherServerTypesHaveNoIsland() {
		location("lobby1", "BEDWARS", "hub", null);
		assertNull(state.current().island());
		assertFalse(state.current().onSkyBlock());
		location("mini1", null, "hub", null);
		assertNull(state.current().island());
	}

	@Test
	void forcingAnIslandForTests() {
		assertEquals("Dungeon Hub", state.force("Dungeon Hub").island());
		assertTrue(state.current().onSkyBlock());
		assertEquals(LocationSnapshot.NONE, state.force(null));
	}

	/** AC-LOC-13: every island name exactly once, each with a description. */
	@Test
	void theIslandListHasEveryNameOnceWithADescription() {
		List<Islands.Island> islands = Islands.all();
		Set<String> names = new HashSet<>();
		for (Islands.Island island : islands) {
			assertTrue(names.add(island.name()), "duplicate " + island.name());
			assertFalse(island.description().isBlank(), island.name());
		}
		Set<String> mapped = new HashSet<>();
		for (String mode : List.of("dynamic", "hub", "garden", "farming_1", "foraging_1", "foraging_2", "foraging_3", "combat_1",
				"combat_3", "crimson_isle", "mining_1", "mining_2", "mining_3", "crystal_hollows", "mineshaft", "fishing_1",
				"lotus_atoll", "safari", "kuudra", "winter", "rift", "dark_auction", "dungeon", "dungeon_hub")) {
			mapped.add(Islands.islandFor(mode));
		}
		assertEquals(mapped, names);
	}
}
