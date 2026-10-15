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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-CORPSE-01 [A] (T3.3a, REQ-CORPSE-10): the shipped data with the corpse spot table validates, and a copy with an
 * unknown code, a code missing, a spot out of the plausibility box, a duplicate spot, or a crystal code with its own
 * list fails it (so `./gradlew check` fails), as does a copy whose meowdding-repo licence text is gone (AC-CORPSE-08).
 */
class CorpseSpotTableRulesTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "."));
	private static final String TABLE = "thirdparty/skyhanni-repo/mining/corpse_spots.json";

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

	@Test
	void theShippedDataValidates() throws IOException {
		assertEquals(List.of(), problemsAfter(UnaryOperator.identity()));
	}

	@Test
	void anUnknownCodeFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replace("\"code\": \"TOPA_1\"", "\"code\": \"ZZZZ_1\""));
		assertTrue(problems.stream().anyMatch(p -> p.contains("ZZZZ_1: not a mining_shafts code")), problems.toString());
		assertTrue(problems.stream().anyMatch(p -> p.contains("TOPA_1: a mining_shafts code with no row")), problems.toString());
	}

	@Test
	void aSpotOutOfTheBoxFails() throws IOException {
		List<String> problems = problemsAfter(text -> text.replaceFirst("\\{\"x\": -196, \"y\": 22,", "{\"x\": 196, \"y\": 22,"));
		assertTrue(problems.stream().anyMatch(p -> p.contains("mining.corpse_spots") && p.contains("/x")), problems.toString());
	}

	@Test
	void aDuplicateSpotFails() throws IOException {
		// TOPA_1's second spot moved onto its first.
		List<String> problems = problemsAfter(text -> text.replaceFirst("\\{\"x\": -123, \"y\": 8, \"z\": -171,", "{\"x\": -196, \"y\": 22, \"z\": -158,"));
		assertTrue(problems.stream().anyMatch(p -> p.contains("TOPA_1: within 2.0 of spot 1, a duplicate")), problems.toString());
	}

	@Test
	void aCrystalCodeWithItsOwnListFailsByName() throws IOException {
		// Any of the seven, including ONYX_C, which a hash-ordered reference would have taken as the shared list.
		Path table = data.resolve(TABLE);
		String shipped = Files.readString(table, StandardCharsets.UTF_8);
		for (String code : List.of("OPAL_C", "ONYX_C")) {
			Files.writeString(table, shipped, StandardCharsets.UTF_8);
			List<String> problems = problemsAfter(text -> withOneCrystalSpotMoved(text, code));
			List<String> crystal = problems.stream().filter(p -> p.contains("a crystal code whose spots differ")).toList();
			assertEquals(List.of("mining.corpse_spots: " + code + ": a crystal code whose spots differ from the shared list"), crystal, code);
		}
	}

	/** The table with the first spot of {@code code} moved to y 40. */
	private static String withOneCrystalSpotMoved(String text, String code) {
		int row = text.indexOf("\"code\": \"" + code + "\"");
		int spot = text.indexOf("{\"x\": ", row);
		return text.substring(0, spot) + text.substring(spot).replaceFirst("\"y\": (-?\\d+),", "\"y\": 40,");
	}

	@Test
	void aMissingLicenceTextFails() throws IOException {
		Files.delete(root.resolve("licenses/meowdding-repo.txt"));
		List<String> problems = problemsAfter(UnaryOperator.identity());
		assertTrue(problems.stream().anyMatch(p -> p.contains("licenses/meowdding-repo.txt is missing")), problems.toString());
	}
}
