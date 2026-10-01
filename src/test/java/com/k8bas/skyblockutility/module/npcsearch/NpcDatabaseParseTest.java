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

	@Test
	void malformedJsonThrows() {
		assertThrows(JsonSyntaxException.class, () -> NpcDatabase.parse("{\"id\": 1}"));
	}
}
