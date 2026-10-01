package com.k8bas.skyblockutility.config.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-CFG-02: exactly the missing steps run, in ascending order, and a second load changes nothing. */
class MigratorTest {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final List<Integer> applied = new ArrayList<>();

	private MigrationStep step(int number) {
		return new MigrationStep() {
			public int number() {
				return number;
			}

			public String description() {
				return "test step " + number;
			}

			public void apply(JsonObject root) {
				applied.add(number);
				root.addProperty("step" + number, true);
			}
		};
	}

	private Migrator migrator() {
		return new Migrator("configVersion", List.of(step(1), step(2), step(3)));
	}

	private static JsonObject parse(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	void aFileWithoutVersionIsVersionZeroAndRunsEveryStep() {
		JsonObject root = parse("{\"general\":{}}");
		Migrator.Result result = migrator().migrate(root);
		assertEquals(0, result.fromVersion());
		assertEquals(List.of(1, 2, 3), result.ran());
		assertEquals(List.of(1, 2, 3), applied);
		assertEquals(3, root.get("configVersion").getAsInt());
	}

	@Test
	void anIntermediateVersionRunsOnlyTheMissingSteps() {
		JsonObject root = parse("{\"configVersion\":1}");
		assertEquals(List.of(2, 3), migrator().migrate(root).ran());
		assertEquals(List.of(2, 3), applied);
		assertFalse(root.has("step1"));
	}

	@Test
	void theCurrentVersionRunsNothing() {
		JsonObject root = parse("{\"configVersion\":3,\"x\":1}");
		Migrator.Result result = migrator().migrate(root);
		assertTrue(result.ran().isEmpty());
		assertFalse(result.changed());
		assertEquals(parse("{\"configVersion\":3,\"x\":1}"), root);
	}

	@Test
	void migratingTheResultAgainGivesIdenticalOutput() {
		JsonObject root = parse("{}");
		migrator().migrate(root);
		String first = GSON.toJson(root);
		migrator().migrate(root);
		assertEquals(first, GSON.toJson(root));
	}

	@Test
	void aNewerFileIsReportedAndLeftUnchanged() {
		JsonObject root = parse("{\"configVersion\":9,\"future\":true}");
		Migrator.Result result = migrator().migrate(root);
		assertTrue(result.newerThanKnown());
		assertEquals(parse("{\"configVersion\":9,\"future\":true}"), root);
		assertTrue(applied.isEmpty());
	}

	@Test
	void stepsMustBeNumberedInOrder() {
		assertThrows(IllegalArgumentException.class, () -> new Migrator("v", List.of(step(1), step(3))));
		assertThrows(IllegalArgumentException.class, () -> new Migrator("v", List.of(step(2))));
	}

	@Test
	void theConfigHasAVersionFieldAndOrderedSteps() {
		assertEquals("configVersion", ConfigMigrations.VERSION_FIELD);
		assertEquals(0, ConfigMigrations.MIGRATOR.versionOf(parse("{}")));
	}
}
