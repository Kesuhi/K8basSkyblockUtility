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
 * The per-row source rules of every table (first written for the odds tables, T3.9g; kept after R42 removed them). A
 * synthetic table: a row without sources or with an unknown one fails, and a wiki-only row marked verified fails. (An
 * MIT table without a notice fails the T3.0j rules, DataLicenceTest.)
 */
class DataRowSourcesTest {
	@TempDir
	Path root;
	private Path data;
	private Path schemas;

	private static final String TABLE = """
			{
			  "table": "rows.test", "schemaVersion": 1, "dataVersion": 1, "gameVersion": "0.27.1",
			  "generatedAt": "2026-10-09T12:00:00Z", "license": "CC0-1.0",
			  "sources": {
			    "wiki": {"title": "a page you supplied", "kind": "wiki_crosscheck", "access": "user_supplied"},
			    "api": {"title": "the official API", "url": "https://api.hypixel.net/v2/resources"}
			  },
			  "rows": [
			    {"id": "SCATHA_RARE", "chance": 0.0024, "sourceRefs": ["wiki"], "license": "CC0-1.0", "verified": false},
			    {"id": "BOTH", "chance": 0.1, "sourceRefs": ["wiki", "api"], "license": "CC0-1.0", "verified": true}
			  ]
			}
			""";
	private static final String SCHEMA = """
			{
			  "$schema": "https://json-schema.org/draft/2020-12/schema",
			  "x-keys": {"/rows": "id"},
			  "type": "object",
			  "properties": {"table": {"const": "rows.test"}, "rows": {"type": "array"}}
			}
			""";

	@BeforeEach
	void validSet() throws IOException {
		data = Files.createDirectories(root.resolve("data"));
		schemas = Files.createDirectories(root.resolve("data-schemas"));
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		Files.copy(project.resolve("data-schemas/envelope.schema.json"), schemas.resolve("envelope.schema.json"));
		Files.copy(project.resolve("data-schemas/upstreams.json"), schemas.resolve("upstreams.json"));
		write(schemas.resolve("rows.test.schema.json"), SCHEMA);
		write(data.resolve("rows/test.json"), TABLE);
		DataIndexer.writeIndex(data, schemas);
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private void edit(UnaryOperator<String> change) throws IOException {
		Path table = data.resolve("rows/test.json");
		write(table, change.apply(Files.readString(table)));
		DataIndexer.writeIndex(data, schemas);
	}

	private List<DataValidator.Problem> problems() {
		return DataValidator.validate(data, schemas).getProblems();
	}

	private void fails(String key, String message) {
		List<DataValidator.Problem> problems = problems();
		assertTrue(problems.stream().anyMatch(p -> p.getTable().equals("rows.test") && p.getKey().contains(key) && p.getMessage().contains(message)),
				"expected " + key + " / " + message + " in " + problems);
	}

	@Test
	void theValidTablePasses() {
		assertEquals(List.of(), problems());
	}

	@Test
	void aRowWithoutSourcesFails() throws IOException {
		edit(text -> text.replace("\"sourceRefs\": [\"wiki\"], ", "\"sourceRefs\": [], "));
		fails("/rows/0/sourceRefs", "at least one source");
	}

	@Test
	void aRowCitingAnUnknownSourceFails() throws IOException {
		edit(text -> text.replace("\"sourceRefs\": [\"wiki\"], ", "\"sourceRefs\": [\"nowhere\"], "));
		fails("/rows/0/sourceRefs", "nowhere is not one of the table's sources");
	}

	@Test
	void aWikiOnlyRowMarkedVerifiedFails() throws IOException {
		edit(text -> text.replace("\"sourceRefs\": [\"wiki\"], \"license\": \"CC0-1.0\", \"verified\": false",
				"\"sourceRefs\": [\"wiki\"], \"license\": \"CC0-1.0\", \"verified\": true"));
		fails("/rows/0/verified", "verified: false until a second, non-wiki origin agrees");
	}

	@Test
	void aVerifiedRowOnAPageAPersonPastedFails() throws IOException {
		// A pasted page has no url and may have no kind: its access marks it (D-28 (i)).
		edit(text -> text.replace("\"api\": {", "\"pasted\": {\"title\": \"a page you pasted\", \"access\": \"user_supplied\"},\n    \"api\": {")
				.replace("\"sourceRefs\": [\"wiki\"], \"license\": \"CC0-1.0\", \"verified\": false", "\"sourceRefs\": [\"pasted\"], \"license\": \"CC0-1.0\", \"verified\": true"));
		fails("/rows/0/verified", "verified: false until a second, non-wiki origin agrees");
	}

	@Test
	void aTitleOnlyStandInIsNoOrigin() throws IOException {
		edit(text -> text.replace("\"api\": {", "\"pending\": {\"title\": \"nothing yet\"},\n    \"api\": {")
				.replace("\"sourceRefs\": [\"wiki\"], \"license\": \"CC0-1.0\", \"verified\": false", "\"sourceRefs\": [\"wiki\", \"pending\"], \"license\": \"CC0-1.0\", \"verified\": true"));
		fails("/rows/0/verified", "verified: false until a second, non-wiki origin agrees");
	}
}
