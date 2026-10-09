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
 * The odds tables' row rules (T3.9g, REQ-ODDS-09, REQ-ODDS-10): AC-ODDS-08 [A]. A synthetic odds table: a row without
 * sources or with an unknown one fails, a percentage field fails, a wiki-only row marked verified fails, and a dye row
 * from SkyOcean fails. (A row without a licence fails each odds schema; an MIT table without a notice fails the T3.0j
 * rules, DataLicenceTest.)
 */
class DataOddsRulesTest {
	@TempDir
	Path root;
	private Path data;
	private Path schemas;

	private static final String TABLE = """
			{
			  "table": "odds.test", "schemaVersion": 1, "dataVersion": 1, "gameVersion": "0.27.1",
			  "generatedAt": "2026-10-09T12:00:00Z", "license": "CC0-1.0",
			  "sources": {
			    "wiki": {"title": "a page you supplied", "kind": "wiki_crosscheck", "access": "user_supplied"},
			    "api": {"title": "the official API", "url": "https://api.hypixel.net/v2/resources"},
			    "ocean": {"title": "SkyOcean weights", "license": "MIT", "notice": "skyocean"}
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
			  "properties": {"table": {"const": "odds.test"}, "rows": {"type": "array"}}
			}
			""";

	@BeforeEach
	void validSet() throws IOException {
		data = Files.createDirectories(root.resolve("data"));
		schemas = Files.createDirectories(root.resolve("data-schemas"));
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		Files.copy(project.resolve("data-schemas/envelope.schema.json"), schemas.resolve("envelope.schema.json"));
		// The SkyOcean source is an approved MIT upstream with its notice and licence text, as for a real table.
		Files.copy(project.resolve("data-schemas/upstreams.json"), schemas.resolve("upstreams.json"));
		write(root.resolve("THIRD_PARTY_NOTICES.md"), "# Third-party notices\n\n## SkyOcean\n");
		write(root.resolve("licenses/skyocean.txt"), "MIT\n");
		write(schemas.resolve("odds.test.schema.json"), SCHEMA);
		write(data.resolve("odds/test.json"), TABLE);
		DataIndexer.writeIndex(data, schemas);
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private void edit(UnaryOperator<String> change) throws IOException {
		Path table = data.resolve("odds/test.json");
		write(table, change.apply(Files.readString(table)));
		DataIndexer.writeIndex(data, schemas);
	}

	private List<DataValidator.Problem> problems() {
		return DataValidator.validate(data, schemas).getProblems();
	}

	private void fails(String key, String message) {
		List<DataValidator.Problem> problems = problems();
		assertTrue(problems.stream().anyMatch(p -> p.getTable().equals("odds.test") && p.getKey().contains(key) && p.getMessage().contains(message)),
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
	void aPercentageFieldFails() throws IOException {
		edit(text -> text.replace("\"chance\": 0.0024,", "\"chance\": 0.0024, \"perCorpsePercent\": 1.83,"));
		fails("/rows/0/perCorpsePercent", "no percentage field");
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

	@Test
	void aPercentageMapAtTheRootFails() throws IOException {
		edit(text -> text.replace("\"rows\": [", "\"perCorpsePercent\": {\"CAGED_WISP\": 1.83},\n  \"rows\": ["));
		fails("/perCorpsePercent", "no percentage field");
	}

	@Test
	void aRollRangeUpsideDownFails() throws IOException {
		edit(text -> text.replace("\"rows\": [", "\"rolls\": {\"min\": 8, \"max\": 5, \"sourceRefs\": [\"wiki\"]},\n  \"rows\": ["));
		fails("/rolls/min", "min at most max");
	}

	@Test
	void aDyeRowFromSkyOceanFails() throws IOException {
		edit(text -> text.replace("{\"id\": \"BOTH\"", "{\"id\": \"FROSTBITTEN_DYE\", \"weight\": 1, \"sourceRefs\": [\"ocean\"], \"license\": \"MIT\", "
				+ "\"verified\": true},\n    {\"id\": \"BOTH\""));
		fails("/rows/1/sourceRefs", "SkyOcean's dye weight is only assumed");
	}
}
