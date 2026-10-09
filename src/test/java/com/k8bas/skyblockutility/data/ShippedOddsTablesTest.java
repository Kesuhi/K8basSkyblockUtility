package com.k8bas.skyblockutility.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-ODDS-08 [A] on the shipped odds tables and their real schemas (T3.9g): they pass as shipped, and a row without
 * `sourceRefs` or without `license`, a per-corpse percentage field, SkyOcean's assumed dye weight, and the Vanguard
 * table without its notice each fail. Works on a copy of the project's data, schemas, notices and licence texts.
 */
class ShippedOddsTablesTest {
	@TempDir
	Path root;
	private Path data;
	private Path schemas;

	@BeforeEach
	void copyTheProject() throws IOException {
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		data = copy(project.resolve("src/main/resources/assets/k8bas_skyblock_utility/data"), root.resolve("data"));
		schemas = copy(project.resolve("data-schemas"), root.resolve("data-schemas"));
		copy(project.resolve("licenses"), root.resolve("licenses"));
		Files.copy(project.resolve("THIRD_PARTY_NOTICES.md"), root.resolve("THIRD_PARTY_NOTICES.md"));
	}

	private static Path copy(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path path : walk.toList()) {
				Path target = to.resolve(from.relativize(path).toString());
				if (Files.isDirectory(path)) {
					Files.createDirectories(target);
				} else {
					Files.copy(path, target);
				}
			}
		}
		return to;
	}

	private void edit(String table, UnaryOperator<String> change) throws IOException {
		Path file = data.resolve(table);
		Files.writeString(file, change.apply(Files.readString(file)));
		DataIndexer.writeIndex(data, schemas);
	}

	private List<DataValidator.Problem> problems() {
		return DataValidator.validate(data, schemas).getProblems();
	}

	private void fails(String table, String message) {
		List<DataValidator.Problem> problems = problems();
		assertTrue(problems.stream().anyMatch(p -> p.getTable().equals(table) && (p.getMessage() + p.getKey()).contains(message)),
				"expected " + table + " / " + message + " in " + problems);
	}

	private static final String VANGUARD = "thirdparty/skyocean/odds/vanguard.json";

	@Test
	void theShippedTablesPass() {
		assertEquals(List.of(), problems());
	}

	@Test
	void aRowWithoutSourceRefsFails() throws IOException {
		edit(VANGUARD, text -> text.replaceFirst("\"sourceRefs\": \\[[^]]*\\],", ""));
		fails("odds.vanguard", "sourceRefs");
	}

	@Test
	void aRowWithoutLicenseFails() throws IOException {
		edit(VANGUARD, text -> text.replaceFirst("\"license\": \"MIT\",\\s*\"verified\"", "\"verified\""));
		fails("odds.vanguard", "license");
	}

	@Test
	void aPerCorpsePercentageFieldFails() throws IOException {
		edit(VANGUARD, text -> text.replaceFirst("\"weight\": 10,", "\"weight\": 10, \"perCorpsePercent\": 1.83,"));
		fails("odds.vanguard", "no percentage field");
	}

	@Test
	void skyOceansAssumedDyeWeightFails() throws IOException {
		edit(VANGUARD, text -> text.replaceFirst("\"rows\": \\[", "\"rows\": [\n    {\"id\": \"DYE_FROSTBITTEN\", \"item\": \"DYE_FROSTBITTEN\", "
				+ "\"displayName\": \"Frostbitten Dye\", \"weight\": 1, \"sourceRefs\": [\"skyocean\"], \"license\": \"MIT\", \"verified\": true, \"gameVersion\": \"0.27.1\"},"));
		fails("odds.vanguard", "SkyOcean's dye weight is only assumed");
	}

	@Test
	void theVanguardTableWithoutItsNoticeFails() throws IOException {
		Path notices = root.resolve("THIRD_PARTY_NOTICES.md");
		Files.writeString(notices, Files.readString(notices).replace("## SkyOcean", "## Something else"));
		fails("odds.vanguard", "no '## SkyOcean' section");
	}
}
