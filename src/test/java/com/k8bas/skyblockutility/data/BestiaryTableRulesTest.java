package com.k8bas.skyblockutility.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-BEST-14 [A] (T5.1, REQ-BEST-15): `check` validates the shipped bestiary table, and a copy fails it when a family is
 * missing against the pinned NEU commit, an id repeats, a bracket or category is unknown, a cap is not above 0, a tier
 * count disagrees with the bracket, a bracket is not increasing, the pin moved alone, or the NEU notice is gone. A
 * SkyBlock XP style reference `bestiary:ghost` resolves and a dangling one fails.
 */
class BestiaryTableRulesTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "."));
	private static final String TABLE = "thirdparty/neu-repo/bestiary/families.json";
	private static final String GHOST = "{\"id\":\"ghost\",\"name\":\"Ghost\",\"category\":\"mining_3\",\"bracket\":\"2\",\"cap\":25000,\"maxTiers\":20,";

	@TempDir
	Path root;
	private Path data;
	private Path schemas;

	@BeforeEach
	void copyOfTheProject() throws IOException {
		data = root.resolve("data");
		schemas = root.resolve("data-schemas");
		copy(PROJECT.resolve("src/main/resources/assets/k8bas_skyblock_utility/data"), data);
		copy(PROJECT.resolve("data-schemas"), schemas);
		copy(PROJECT.resolve("licenses"), root.resolve("licenses"));
		Files.copy(PROJECT.resolve("THIRD_PARTY_NOTICES.md"), root.resolve("THIRD_PARTY_NOTICES.md"));
	}

	private static void copy(Path from, Path to) throws IOException {
		try (Stream<Path> files = Files.walk(from)) {
			for (Path file : files.toList()) {
				Path target = to.resolve(from.relativize(file).toString());
				if (Files.isDirectory(file)) {
					Files.createDirectories(target);
				} else {
					Files.copy(file, target);
				}
			}
		}
	}

	private List<String> problemsAfter(UnaryOperator<String> change) throws IOException {
		Path table = data.resolve(TABLE);
		Files.writeString(table, change.apply(Files.readString(table, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
		DataIndexer.writeIndex(data, schemas);
		return DataValidator.validate(data, schemas).getProblems().stream().map(Object::toString).toList();
	}

	private static void assertProblem(List<String> problems, String... parts) {
		assertTrue(problems.stream().anyMatch(p -> Stream.of(parts).allMatch(p::contains)), String.join(" + ", parts) + " in " + problems);
	}

	@Test
	void theShippedTableValidates() throws IOException {
		assertEquals(List.of(), problemsAfter(UnaryOperator.identity()));
	}

	@Test
	void aMissingFamilyFailsTheCount() throws IOException {
		List<String> problems = problemsAfter(text -> text.replaceFirst("\\n    \\{\"id\":\"stoneworm\"[^\\n]*", ""));
		assertProblem(problems, "bestiary", "358 families", "359");
	}

	@Test
	void aRepeatedIdFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace("{\"id\":\"stoneworm\"", "{\"id\":\"ghost\""));
		assertProblem(problems, "bestiary", "id=ghost", "duplicate primary key");
	}

	@Test
	void anUnknownBracketFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace(GHOST, GHOST.replace("\"bracket\":\"2\"", "\"bracket\":\"9\"")));
		assertProblem(problems, "bestiary", "ghost", "bracket 9 is not one of the table's brackets");
	}

	@Test
	void anUnknownCategoryOrSubcategoryFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace(GHOST, GHOST.replace("\"mining_3\"", "\"mining_9\""))
				.replace("\"category\":\"fishing\",\"subcategory\":\"lava\"", "\"category\":\"fishing\",\"subcategory\":\"magma\""));
		assertProblem(problems, "bestiary", "ghost", "category mining_9 is not one of the table's categories");
		assertProblem(problems, "bestiary", "subcategory magma is not one of fishing's");
	}

	@Test
	void aFishingFamilyWithoutItsSubcategoryFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace("\"category\":\"fishing\",\"subcategory\":\"lava\",", "\"category\":\"fishing\","));
		assertProblem(problems, "bestiary", "category fishing needs a subcategory");
	}

	@Test
	void aValueOfTheWrongTypeIsReportedNotThrown() throws IOException {
		// The schema names the type; the bestiary rule skips what it cannot read instead of ending the validation.
		List<String> problems = problemsAfter(text -> text.replace(GHOST, GHOST.replace("\"cap\":25000", "\"cap\":\"25000\"").replace("\"bracket\":\"2\"", "\"bracket\":2"))
				.replace("\"8\": [1,2,3,4,5,8,", "\"8\": [1,2,3,4,\"5\",8,"));
		assertProblem(problems, "bestiary", "/cap", "type");
		assertProblem(problems, "bestiary", "/bracket", "type");
		assertProblem(problems, "bestiary", "brackets/8", "type");
	}

	@Test
	void aCapOfZeroFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace(GHOST, GHOST.replace("\"cap\":25000", "\"cap\":0")));
		assertProblem(problems, "bestiary", "/cap", "minimum");
	}

	@Test
	void aTierCountThatDisagreesWithTheBracketFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace(GHOST, GHOST.replace("\"maxTiers\":20", "\"maxTiers\":21")));
		assertProblem(problems, "bestiary", "ghost", "maxTiers 21, but bracket 2 with cap 25000 gives 20");
		List<String> zero = problemsAfter(text -> text.replace("\"maxTiers\":21", "\"maxTiers\":0"));
		assertProblem(zero, "bestiary", "/maxTiers", "minimum");
	}

	@Test
	void aBracketThatIsNotIncreasingFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace("\"8\": [1,2,3,4,5,8,", "\"8\": [1,2,3,4,5,5,"));
		assertProblem(problems, "bestiary", "brackets/8", "not increasing");
	}

	@Test
	void aMovedPinFailsUntilTheValidatorFollows() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace("24564a1ad22fdb0ca985d78710c401898eac586b", "e552aec9b5" + "0".repeat(30)));
		assertProblem(problems, "bestiary", "revision", "24564a1ad22fdb0ca985d78710c401898eac586b");
	}

	@Test
	void theNeuNoticeMustNameTheTable() throws IOException {
		Path notices = root.resolve("THIRD_PARTY_NOTICES.md");
		Files.writeString(notices, Files.readString(notices).replace(TABLE, "thirdparty/neu-repo/elsewhere.json"));
		assertProblem(problemsAfter(UnaryOperator.identity()), "bestiary", "THIRD_PARTY_NOTICES.md does not name " + TABLE);
		Files.delete(root.resolve("licenses/neu-repo.txt"));
		assertProblem(problemsAfter(UnaryOperator.identity()), "bestiary", "licenses/neu-repo.txt is missing");
	}

	@Test
	void aSkyblockXpReferenceResolvesAndADanglingOneFails() throws IOException {
		// A stand-in for a Phase 6 table: one CC0 row that names a family as bestiary:<id>.
		Files.writeString(schemas.resolve("sbxp.reference_test.schema.json"), """
				{"x-keys": {"/rows": "id"}, "type": "object", "properties": {"table": {"const": "sbxp.reference_test"},
				 "rows": {"type": "array", "items": {"type": "object", "required": ["id", "family"]}}}}
				""");
		Files.createDirectories(data.resolve("sbxp"));
		String reference = """
				{"table": "sbxp.reference_test", "schemaVersion": 1, "dataVersion": 1, "gameVersion": "0.27.1",
				 "generatedAt": "2026-10-18T18:00:00Z", "license": "CC0-1.0", "sources": {"own": {"title": "test"}},
				 "rows": [{"id": "ghost_task", "family": "bestiary:%s"}]}
				""";
		Files.writeString(data.resolve("sbxp/reference_test.json"), reference.formatted("ghost"));
		assertEquals(List.of(), problemsAfter(UnaryOperator.identity()));
		Files.writeString(data.resolve("sbxp/reference_test.json"), reference.formatted("ghosts"));
		List<String> problems = new ArrayList<>(problemsAfter(UnaryOperator.identity()));
		assertProblem(problems, "sbxp.reference_test", "bestiary:ghosts", "refers to a key that table bestiary does not have");
	}
}
