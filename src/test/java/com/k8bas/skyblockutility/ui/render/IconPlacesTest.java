package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AC-UI-33's source scan [A] (REQ-UI-32, T3.21): with every icon at its place, no "×" is drawn as text and no chevron is
 * built from rows of fills any more; the icon kit draws them. The × is found however it is written (the character, its
 * escape or its code); a chevron by the old drawing method's name and by a fill inside a short counted loop, the shape all
 * three row-built chevrons had.
 */
class IconPlacesTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "."));
	/** A one-character "×" string, the way the clear button drew it as text. */
	private static final String CROSS_TEXT = "\"" + (char) 0xD7 + "\"";
	private static final String CROSS_CHAR = "'" + (char) 0xD7 + "'";
	private static final java.util.regex.Pattern CROSS_CODE = java.util.regex.Pattern.compile("\\\\u00[dD]7|0x[dD]7\\b");
	/** A loop of a handful of steps whose body fills: how the row-built chevrons were drawn. */
	private static final java.util.regex.Pattern ROW_LOOP = java.util.regex.Pattern.compile(
			"for \\(int \\w+ = 0; \\w+ < [2-6]; \\w+\\+\\+\\) \\{[^}]{0,160}?graphics\\.fill\\(");

	@Test
	void noCrossIsTextAndNoChevronIsRowsOfFills() throws IOException {
		List<String> found = new ArrayList<>();
		for (String root : new String[] {"src/main/java", "src/main/kotlin"}) {
			try (Stream<Path> files = Files.walk(PROJECT.resolve(root))) {
				for (Path file : files.filter(f -> f.toString().endsWith(".java") || f.toString().endsWith(".kt")).toList()) {
					String text = Files.readString(file);
					if (text.contains(CROSS_TEXT) || text.contains(CROSS_CHAR) || CROSS_CODE.matcher(text).find()) {
						found.add(PROJECT.relativize(file) + ": a × drawn as text");
					}
					if (text.contains("drawChevron") || ROW_LOOP.matcher(text).find()) {
						found.add(PROJECT.relativize(file) + ": a chevron of its own");
					}
				}
			}
		}
		assertEquals(List.of(), found);
	}
}
