package com.k8bas.skyblockutility.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table validator (T3.0i): AC-DATA-02 (each broken case fails, naming the table and the offending key;
 * only the valid set passes), AC-DATA-04 (`§` and long prose), EC-DATA-04 (duplicate table id), and
 * `dataIndex` writing the index the runtime registry reads (T3.0c). The data set is synthetic: two tables,
 * islands and NPCs that refer to them, with their schemas and the shared envelope schema.
 */
class DataValidatorTest {
	@TempDir
	Path root;
	private Path data;
	private Path schemas;

	private static final String ISLANDS = """
			{
			  "table": "test_islands", "schemaVersion": 1, "dataVersion": 2, "gameVersion": "0.27.1",
			  "generatedAt": "2026-10-09T12:00:00Z", "license": "CC0-1.0",
			  "sources": {"own": {"title": "test"}},
			  "rows": [{"id": "hub", "name": "Hub"}, {"id": "mines", "name": "Dwarven Mines"}]
			}
			""";
	private static final String NPCS = """
			{
			  "table": "test_npcs", "schemaVersion": 1, "dataVersion": 5, "gameVersion": "0.27.1",
			  "generatedAt": "2026-10-09T12:00:00Z", "license": "CC0-1.0",
			  "sources": {"own": {"title": "test"}},
			  "rows": [
			    {"id": "udel", "name": "Udel", "island": "test_islands:hub", "type": "minecraft:villager"},
			    {"id": "timmy", "name": "Researcher Timmy", "island": "test_islands:mines", "note": "A note that may be long because the schema allows the note field to hold prose, more than eighty characters."}
			  ]
			}
			""";
	private static final String ROW_SCHEMA = """
			{
			  "$schema": "https://json-schema.org/draft/2020-12/schema",
			  "x-keys": {"/rows": "id"},
			  "x-prose": ["note"],
			  "type": "object",
			  "required": ["rows"],
			  "properties": {
			    "table": {"const": "%s"},
			    "schemaVersion": {"const": 1},
			    "rows": {"type": "array", "items": {
			      "type": "object", "required": ["id", "name"], "additionalProperties": false,
			      "properties": {"id": {"type": "string"}, "name": {"type": "string"}, "island": {"type": "string"},
			        "type": {"type": "string"}, "note": {"type": "string"}}
			    }}
			  }
			}
			""";

	@BeforeEach
	void validSet() throws IOException {
		data = Files.createDirectories(root.resolve("data"));
		schemas = Files.createDirectories(root.resolve("data-schemas"));
		Files.copy(Path.of(System.getProperty("k8bas.projectDir", ".")).resolve("data-schemas/envelope.schema.json"),
				schemas.resolve("envelope.schema.json"));
		write(schemas.resolve("test_islands.schema.json"), ROW_SCHEMA.formatted("test_islands"));
		write(schemas.resolve("test_npcs.schema.json"), ROW_SCHEMA.formatted("test_npcs"));
		write(data.resolve("test/islands.json"), ISLANDS);
		write(data.resolve("test/npcs.json"), NPCS);
		DataIndexer.writeIndex(data, schemas);
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private void edit(String file, UnaryOperator<String> change) throws IOException {
		Path path = data.resolve(file);
		write(path, change.apply(Files.readString(path)));
	}

	private List<DataValidator.Problem> problems() {
		return DataValidator.validate(data, schemas).getProblems();
	}

	/** One problem, for this table, naming this key. */
	private void fails(String table, String key) {
		fails(table, key, "");
	}

	/** One problem, for this table, naming this key, whose message contains [message]. */
	private void fails(String table, String key, String message) {
		List<DataValidator.Problem> problems = problems();
		assertTrue(problems.stream().anyMatch(p -> p.getTable().equals(table) && p.getKey().contains(key) && p.getMessage().contains(message)),
				"expected " + table + " / " + key + " / " + message + " in " + problems);
	}

	@Test
	void theValidSetPasses() {
		DataValidator.Report report = DataValidator.validate(data, schemas);
		assertEquals(List.of(), report.getProblems());
		assertEquals(2, report.getBytes().size(), "bytes per table are reported (AC-DATA-10)");
		assertTrue(report.getBytes().get("test_npcs") > 0);
	}

	@Test
	void theIndexIsTheOneTheRuntimeReads() throws IOException {
		// T3.0c reads this index: path, normalised sha256, row count, versions.
		String index = Files.readString(data.resolve("index.json"));
		DataRegistry registry = new DataRegistry(path -> {
			try {
				return Files.readAllBytes(data.resolve(path));
			} catch (IOException e) {
				return null;
			}
		}, Runnable::run, Runnable::run, message -> { throw new AssertionError(message); });
		TableHandle<Integer> npcs = registry.register(new TableSpec<>("test_npcs", 1, table -> table.getBody().getAsJsonArray("rows").size()));
		registry.start();
		assertEquals(2, npcs.value(), index);
		assertEquals(List.of("test_islands: data v2, SkyBlock 0.27.1, not used", "test_npcs: data v5, SkyBlock 0.27.1, loaded"), registry.describe());
		assertTrue(index.contains("\"rows\": 2"), index);
	}

	@Test
	void aSchemaViolationFails() throws IOException {
		edit("test/npcs.json", text -> text.replace("\"name\": \"Udel\"", "\"name\": 7"));
		fails("test_npcs", "/rows/0/name");
	}

	@Test
	void aDuplicatePrimaryKeyFails() throws IOException {
		edit("test/npcs.json", text -> text.replace("\"id\": \"timmy\"", "\"id\": \"udel\""));
		DataIndexer.writeIndex(data, schemas);
		fails("test_npcs", "udel");
	}

	@Test
	void aDuplicateJsonKeyFails() throws IOException {
		edit("test/islands.json", text -> text.replace("\"name\": \"Hub\"", "\"name\": \"Hub\", \"name\": \"Hub 2\""));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "name");
	}

	@Test
	void aDanglingReferenceFails() throws IOException {
		edit("test/npcs.json", text -> text.replace("test_islands:hub", "test_islands:crimson"));
		DataIndexer.writeIndex(data, schemas);
		fails("test_npcs", "test_islands:crimson");
	}

	@Test
	void aStringLikeAReferenceToNoTableIsNoReference() throws IOException {
		// `minecraft:villager` names no table, so it is not checked as a reference.
		assertEquals(List.of(), problems());
	}

	@Test
	void aChecksumMismatchFails() throws IOException {
		edit("test/islands.json", text -> text.replace("Dwarven Mines", "Dwarven  Mines"));
		fails("test_islands", "sha256");
	}

	@Test
	void aRowCountOrSchemaVersionMismatchFails() throws IOException {
		Path index = data.resolve("index.json");
		write(index, Files.readString(index).replace("\"rows\": 2", "\"rows\": 3"));
		fails("test_islands", "rows");
		DataIndexer.writeIndex(data, schemas);
		String text = Files.readString(index);
		int at = text.indexOf("\"schemaVersion\": 1");
		write(index, text.substring(0, at) + "\"schemaVersion\": 2" + text.substring(at + "\"schemaVersion\": 1".length()));
		fails("test_islands", "schemaVersion");
	}

	@Test
	void aFileMissingFromTheIndexFails() throws IOException {
		write(data.resolve("test/extra.json"), ISLANDS.replace("test_islands", "test_extra"));
		write(schemas.resolve("test_extra.schema.json"), ROW_SCHEMA.formatted("test_extra"));
		fails("test_extra", "index");
	}

	@Test
	void anIndexEntryWithoutFileFails() throws IOException {
		Files.delete(data.resolve("test/islands.json"));
		fails("test_islands", "path");
	}

	@Test
	void aMissingDataVersionFails() throws IOException {
		edit("test/islands.json", text -> text.replace("\"dataVersion\": 2, ", ""));
		DataIndexer.writeIndex(data, schemas);
		// The envelope schema itself, not only the index mismatch the edit also causes.
		fails("test_islands", "/dataVersion", "required");
	}

	@Test
	void aDateAsDataVersionFails() throws IOException {
		edit("test/islands.json", text -> text.replace("\"dataVersion\": 2", "\"dataVersion\": \"2026-10-09\""));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "/dataVersion", "type");
	}

	@Test
	void sourcesAsAListFails() throws IOException {
		edit("test/islands.json", text -> text.replace("\"sources\": {\"own\": {\"title\": \"test\"}}", "\"sources\": [\"own\"]"));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "sources");
	}

	@Test
	void aLicenceOutsideTheListFails() throws IOException {
		edit("test/islands.json", text -> text.replace("CC0-1.0", "facts"));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "license");
	}

	@Test
	void formattingCodesFail() throws IOException {
		// AC-DATA-04.
		edit("test/islands.json", text -> text.replace("\"Hub\"", "\"§aHub\""));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "/rows/0/name");
	}

	@Test
	void longProseOutsideTheAllowlistFails() throws IOException {
		// AC-DATA-04: 81 characters in a field not on the schema's x-prose list; the allowlisted note may be long.
		edit("test/islands.json", text -> text.replace("\"Hub\"", "\"" + "x".repeat(81) + "\""));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "/rows/0/name");
		edit("test/islands.json", text -> text.replace("x".repeat(81), "x".repeat(80)));
		DataIndexer.writeIndex(data, schemas);
		assertEquals(List.of(), problems());
	}

	@Test
	void aDuplicateTableIdFails() throws IOException {
		// EC-DATA-04: two files declare the same table id.
		write(data.resolve("test/islands-copy.json"), ISLANDS);
		fails("test_islands", "table");
	}

	@Test
	void aTableWithoutSchemaFails() throws IOException {
		Files.delete(schemas.resolve("test_npcs.schema.json"));
		fails("test_npcs", "schema");
	}

	@Test
	void theDataVersionGuardWantsExactlyOneStepPerChange() throws IOException {
		// T3.0i -PbaseRef: a changed table must have the base's dataVersion + 1; an unchanged or new one is free.
		String base = NPCS;
		assertEquals(List.of(), DataValidator.checkDataVersions(data, id -> id.equals("test_npcs") ? base : null));
		edit("test/npcs.json", text -> text.replace("Researcher Timmy", "Timmy"));
		List<DataValidator.Problem> stale = DataValidator.checkDataVersions(data, id -> id.equals("test_npcs") ? base : null);
		assertEquals(1, stale.size(), stale.toString());
		assertTrue(stale.get(0).getTable().equals("test_npcs") && stale.get(0).getKey().equals("dataVersion"), stale.toString());
		edit("test/npcs.json", text -> text.replace("\"dataVersion\": 5", "\"dataVersion\": 6"));
		assertEquals(List.of(), DataValidator.checkDataVersions(data, id -> id.equals("test_npcs") ? base : null));
		// A table that moved keeps its guard: it is found by id, not by path.
		Files.move(data.resolve("test/npcs.json"), data.resolve("test/people.json"));
		edit("test/people.json", text -> text.replace("\"dataVersion\": 6", "\"dataVersion\": 5"));
		assertEquals(1, DataValidator.checkDataVersions(data, id -> id.equals("test_npcs") ? base : null).size());
	}

	@Test
	void anIndexEntryUnderAnotherIdFails() throws IOException {
		// The file at the entry's path declares another table id: the runtime would not find it.
		edit("test/npcs.json", text -> text.replace("\"table\": \"test_npcs\"", "\"table\": \"test_people\""));
		write(schemas.resolve("test_people.schema.json"), ROW_SCHEMA.formatted("test_people"));
		fails("test_npcs", "table", "test_people");
	}

	@Test
	void dottedTableIdsAreValidAndTheirReferencesChecked() throws IOException {
		// Planned ids such as ref.skills and odds.supplied.
		for (String file : List.of("test/islands.json", "test/npcs.json")) {
			edit(file, text -> text.replace("test_islands", "ref.islands"));
		}
		Files.move(schemas.resolve("test_islands.schema.json"), schemas.resolve("ref.islands.schema.json"));
		write(schemas.resolve("ref.islands.schema.json"), ROW_SCHEMA.formatted("ref.islands"));
		DataIndexer.writeIndex(data, schemas);
		assertEquals(List.of(), problems());
		edit("test/npcs.json", text -> text.replace("ref.islands:hub", "ref.islands:crimson"));
		DataIndexer.writeIndex(data, schemas);
		fails("test_npcs", "ref.islands:crimson");
	}

	@Test
	void proseThatStartsLikeAReferenceIsNotOne() throws IOException {
		// "test_islands: ..." in an allowlisted note, or with a space in the key, is text, not a reference.
		edit("test/npcs.json", text -> text.replace("A note that may be long", "test_islands: tier 5 brackets assumed, a note that may be long"));
		edit("test/npcs.json", text -> text.replace("\"name\": \"Udel\"", "\"name\": \"test_islands: not a key\""));
		DataIndexer.writeIndex(data, schemas);
		assertEquals(List.of(), problems());
	}

	@Test
	void aFormattingCodeInAKeyFails() throws IOException {
		edit("test/islands.json", text -> text.replace("\"own\": {", "\"§aown\": {"));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "/sources/§aown", "formatting");
	}

	@Test
	void aPathTheRuntimeRejectsFails() throws IOException {
		Files.move(data.resolve("test/islands.json"), data.resolve("test/islands v2.json"));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "path", "islands v2.json");
	}

	@Test
	void anXKeysPointerThatMatchesNothingFails() throws IOException {
		write(schemas.resolve("test_islands.schema.json"), ROW_SCHEMA.formatted("test_islands").replace("\"/rows\": \"id\"", "\"/row\": \"id\""));
		DataIndexer.writeIndex(data, schemas);
		fails("test_islands", "x-keys", "/row");
	}
}
