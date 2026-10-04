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
	void theListIsTheOneCentralPlace() {
		assertEquals(List.of("hud", "debug", "update", "sbxp"), CommandWords.RESERVED);
	}

	/** AC-UI-22 [R], made repeatable: the settings, UI and command code send nothing to the server. */
	@Test
	void settingsUiAndCommandCodeSendNothing() throws IOException {
		Pattern sends = Pattern.compile("sendCommand|sendChat|sendUnsignedCommand|\\.connection\\.send|getConnection\\(\\)\\.send|ServerboundChat");
		Path root = Path.of(System.getProperty("k8bas.projectDir", ".")).resolve("src/main/java/com/k8bas/skyblockutility");
		List<Path> dirs = List.of(root.resolve("settings"), root.resolve("ui"), root.resolve("debug"));
		int files = 0;
		for (Path dir : dirs) {
			try (Stream<Path> walk = Files.walk(dir)) {
				for (Path file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
					files++;
					String source = Files.readString(file);
					assertTrue(!sends.matcher(source).find(), file + " sends something to the server");
				}
			}
		}
		assertTrue(files > 40, "the scan found the sources: " + files);
	}
}
