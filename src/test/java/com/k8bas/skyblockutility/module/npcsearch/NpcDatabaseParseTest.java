package com.k8bas.skyblockutility.module.npcsearch;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterization of the 1.0.1 NPC database parsing. */
class NpcDatabaseParseTest {
	private static final String JSON = """
			[
			  {"id": "elizabeth", "displayName": "Elizabeth", "island": "Hub", "fixed": true, "x": -3.5, "y": 70.0, "z": -90.5},
			  {"id": "trinity", "displayName": "Trinity", "island": "Catacombs", "fixed": false, "matchText": "Trinity"}
			]
			""";

	@Test
	void parsesFixedAndMovingEntries() {
		List<NpcDatabaseEntry> entries = NpcDatabase.parse(JSON);
		assertEquals(2, entries.size());
		assertTrue(entries.get(0).fixed);
		assertEquals(-90.5, entries.get(0).z);
		assertFalse(entries.get(1).fixed);
		assertEquals("Catacombs", entries.get(1).island);
		assertEquals("Trinity", entries.get(1).matchText);
	}

	/** AC-LOC-03 (T1.9b): fixed NPCs listed on Catacombs stand in the lobby; moving ones stay in runs. */
	@Test
	void fixedCatacombsEntriesMoveToTheDungeonHub() {
		List<NpcDatabaseEntry> entries = NpcDatabase.parse("""
				[{"id": "croesus", "displayName": "Croesus", "island": "Catacombs", "fixed": true, "x": 1, "y": 2, "z": 3},
				 {"id": "trinity", "displayName": "Trinity", "island": "Catacombs", "fixed": false, "matchText": "Trinity"}]
				""");
		assertEquals("Dungeon Hub", entries.get(0).island);
		assertEquals("Catacombs", entries.get(1).island);
	}

	@Test
	void acceptsAUtf8BomAndCrlf() {
		assertEquals(2, NpcDatabase.parse("﻿" + JSON.replace("\n", "\r\n")).size());
	}

	@Test
	void emptyDocumentsGiveAnEmptyList() {
		assertTrue(NpcDatabase.parse("").isEmpty());
		assertTrue(NpcDatabase.parse("null").isEmpty());
		assertTrue(NpcDatabase.parse("[]").isEmpty());
	}

	/** AC-NPCDB-04 [A] (T1.12): malformed entries are skipped; the others load. */
	@Test
	void malformedEntriesAreSkipped() {
		List<NpcDatabaseEntry> entries = NpcDatabase.parse("""
				[
				  {"id": "no-island", "displayName": "No Island", "island": null, "fixed": false, "matchText": "No Island"},
				  {"id": "no-coords", "displayName": "No Coords", "island": "Hub", "fixed": true},
				  {"id": "half-coords", "displayName": "Half Coords", "island": "Hub", "fixed": true, "x": 1, "y": 2},
				  {"id": "no-match", "displayName": "No Match", "island": "Hub", "fixed": false, "matchText": " "},
				  {"displayName": "No Id", "island": "Hub", "fixed": false, "matchText": "No Id"},
				  {"id": "no-name", "island": "Hub", "fixed": false, "matchText": "No Name"},
				  {"id": "bad-x", "displayName": "Bad X", "island": "Hub", "fixed": true, "x": "far", "y": 2, "z": 3},
				  null,
				  "trinity",
				  {"id": "origin", "displayName": "Origin", "island": "Hub", "fixed": true, "x": 0, "y": 0, "z": 0},
				  {"id": "trinity", "displayName": "Trinity", "island": "Catacombs", "fixed": false, "matchText": "Trinity"}
				]
				""");
		assertEquals(List.of("origin", "trinity"), entries.stream().map(entry -> entry.id).toList());
	}

	@Test
	void malformedJsonThrows() {
		assertThrows(JsonSyntaxException.class, () -> NpcDatabase.parse("{\"id\": 1}"));
	}
}
