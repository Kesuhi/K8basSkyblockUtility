package com.k8bas.skyblockutility.settings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-10, REQ-UI-25 (T2.5b): what `/ksu <text>` does with its text, and commands that stay local. */
class CommandWordsTest {
	@Test
	void textStartingWithAReservedWordIsASubcommand() {
		assertEquals("hud", CommandWords.reserved("hud"));
		assertEquals("update", CommandWords.reserved("  UPDATE now"), "the first word, in any case");
		assertEquals("sbxp", CommandWords.reserved("sbxp"));
		assertEquals("debug", CommandWords.reserved("debug dump tab"));
	}

	@Test
	void anyOtherTextIsASearchTerm() {
		assertNull(CommandWords.reserved("glow"));
		assertNull(CommandWords.reserved("hudson"), "a word that only begins like one is a search");
		assertNull(CommandWords.reserved("show hud"), "only the first word counts");
		assertNull(CommandWords.reserved("corpses"), "dropped with R22");
		assertNull(CommandWords.reserved(""));
	}

	@Test
	void theFirstWordIsSplitOffAtAnyWhitespace() {
		assertEquals("HUD", CommandWords.firstWord("  HUD	now"));
		assertEquals("glow", CommandWords.firstWord("glow"));
		assertEquals("", CommandWords.firstWord("   "));
	}

	@Test
	void theListIsTheOneCentralPlace() {
		assertEquals(List.of("hud", "debug", "update", "sbxp"), CommandWords.RESERVED);
	}

	/** AC-UI-22 [R], made repeatable: the settings, UI and command code send nothing to the server. */
	@Test
	void settingsUiAndCommandCodeSendNothing() throws IOException {
		Pattern sends = Pattern.compile("sendCommand|sendChat|sendUnsignedCommand|\\.connection\\.send|getConnection\\(\\)\\.send|ServerboundChat");
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		int files = 0;
		// Both source trees (R30: some of these packages are Kotlin); a package not (yet) in a tree is skipped.
		for (String tree : List.of("src/main/java", "src/main/kotlin")) {
			Path root = project.resolve(tree).resolve("com/k8bas/skyblockutility");
			for (String pkg : List.of("settings", "ui", "debug")) {
				Path dir = root.resolve(pkg);
				if (!Files.isDirectory(dir)) {
					continue;
				}
				try (Stream<Path> walk = Files.walk(dir)) {
					for (Path file : walk.filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt")).toList()) {
						files++;
						String source = Files.readString(file);
						assertTrue(!sends.matcher(source).find(), file + " sends something to the server");
					}
				}
			}
		}
		assertTrue(files > 40, "the scan found the sources: " + files);
	}
}
