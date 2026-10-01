package com.k8bas.skyblockutility.module.mobhighlighter;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterization of the 1.0.1 mob database parsing. */
class MobDatabaseParseTest {
	private static final String JSON = """
			[
			  {"id": "zealot", "displayName": "Zealot", "matchText": "Zealot", "island": "The End"},
			  {"id": "headless_horseman", "displayName": "Headless Horseman", "matchText": "Headless Horseman", "island": "Events", "subfolder": "Spooky Festival"}
			]
			""";

	@Test
	void parsesEveryField() {
		List<MobDatabaseEntry> entries = MobDatabase.parse(JSON);
		assertEquals(2, entries.size());
		assertEquals("zealot", entries.get(0).id);
		assertEquals("The End", entries.get(0).island);
		assertNull(entries.get(0).subfolder);
		assertEquals("Spooky Festival", entries.get(1).subfolder);
	}

	@Test
	void acceptsAUtf8BomAndCrlf() {
		assertEquals(2, MobDatabase.parse("﻿" + JSON.replace("\n", "\r\n")).size());
	}

	@Test
	void emptyDocumentsGiveAnEmptyList() {
		assertTrue(MobDatabase.parse("").isEmpty());
		assertTrue(MobDatabase.parse("null").isEmpty());
		assertTrue(MobDatabase.parse("[]").isEmpty());
	}

	@Test
	void resultIsImmutable() {
		List<MobDatabaseEntry> entries = MobDatabase.parse(JSON);
		assertThrows(UnsupportedOperationException.class, () -> entries.add(new MobDatabaseEntry()));
	}

	@Test
	void malformedJsonThrows() {
		assertThrows(JsonSyntaxException.class, () -> MobDatabase.parse("[{\"id\": "));
	}
}
