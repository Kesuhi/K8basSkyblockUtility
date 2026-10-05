package com.k8bas.skyblockutility.render.marker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-MARK-02, REQ-NPCWP-12: a fixed anchor may show through blocks and carry a distance line and a
 * beam, so it must never be built from entity data. Where the coordinates come from cannot be read off
 * the source, so this backs the review (AC-NPCWP-12) the other way round: only the files listed below
 * may build a fixed anchor, each with the reason its coordinates are static. A new source of fixed
 * coordinates fails here until it is reviewed and added. It cannot see what reaches a listed file:
 * NpcWaypointMarkers takes NpcRules, so where a rule's coordinates come from stays a review item.
 */
class FixedAnchorSourceTest {
	/** Both source trees (R30): a file is named by its path under the package root, with its extension. */
	private static final Path MAIN = Path.of(System.getProperty("k8bas.projectDir", "../..")).resolve("src/main");
	private static final List<String> TREES = List.of("java", "kotlin");
	private static final Map<String, String> ALLOWED = Map.of(
			"render/marker/MarkerAnchor.java", "defines the anchors",
			"module/npcsearch/NpcWaypointMarkers.java", "positions from NpcRule and NPC-data coordinates only");
	/** Every way to build one: the factory or the record, called, referenced (::) or imported, and {@code new Fixed(}. */
	private static final Pattern BUILDS_A_FIXED_ANCHOR = Pattern.compile("MarkerAnchor\\s*(\\.|::)\\s*(fixed|Fixed|\\*)|new\\s+Fixed\\s*\\(");

	@Test
	void onlyReviewedFilesBuildFixedAnchors() throws IOException {
		List<String> offenders;
		try (Stream<Path> files = Files.walk(MAIN)) {
			offenders = files.filter(file -> file.toString().endsWith(".java") || file.toString().endsWith(".kt"))
					.filter(file -> !ALLOWED.containsKey(relative(file)))
					.filter(FixedAnchorSourceTest::buildsAFixedAnchor)
					.map(FixedAnchorSourceTest::relative)
					.toList();
		}
		assertEquals(List.of(), offenders, "files that build a fixed marker anchor without being reviewed and listed");
	}

	@Test
	void theScanSeesTheSources() throws IOException {
		try (Stream<Path> files = Files.walk(MAIN)) {
			assertTrue(files.anyMatch(file -> relative(file).equals("render/marker/MarkerAnchor.java")), "the scan finds the main sources");
		}
		for (String allowed : ALLOWED.keySet()) {
			assertTrue(TREES.stream().anyMatch(tree -> Files.exists(MAIN.resolve(tree).resolve("com/k8bas/skyblockutility").resolve(allowed))),
					"a listed file that no longer exists: " + allowed);
		}
	}

	private static boolean buildsAFixedAnchor(Path file) {
		try {
			String source = Files.readString(file);
			return BUILDS_A_FIXED_ANCHOR.matcher(source).find();
		} catch (IOException e) {
			throw new AssertionError("cannot read " + file, e);
		}
	}

	private static String relative(Path file) {
		return MAIN.relativize(file).toString().replace('\\', '/').replaceFirst("^(java|kotlin)/com/k8bas/skyblockutility/", "");
	}
}
