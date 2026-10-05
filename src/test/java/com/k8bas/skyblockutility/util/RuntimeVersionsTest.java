package com.k8bas.skyblockutility.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** AC-PORT-10 [A] (T1.4). */
class RuntimeVersionsTest {
	/** Outside a running game the Loader knows no "minecraft" mod, so no version is made up. */
	@Test
	void theMinecraftVersionComesFromTheLoader() {
		assertEquals(RuntimeVersions.UNKNOWN, RuntimeVersions.versionOf("minecraft", id -> Optional.empty()));
		assertEquals(RuntimeVersions.UNKNOWN, RuntimeVersions.minecraft());
	}

	/** No string literal under src/main starts with a 26.1 version (comments may mention it). */
	@Test
	void noHardCoded261VersionUnderSrcMain() throws IOException {
		Path root = Path.of(System.getProperty("k8bas.projectDir", "../..")).resolve("src/main");
		Pattern literal = Pattern.compile("\"26\\.1(\\.|\")");
		List<String> hits;
		try (Stream<Path> files = Files.walk(root)) {
			hits = files.filter(Files::isRegularFile)
					.filter(file -> file.toString().endsWith(".java") || file.toString().endsWith(".kt") || file.toString().endsWith(".json"))
					.flatMap(file -> {
						try {
							return Files.readAllLines(file).stream().filter(line -> literal.matcher(line).find())
									.map(line -> file + ": " + line.trim());
						} catch (IOException e) {
							throw new IllegalStateException(e);
						}
					}).toList();
		}
		assertEquals(List.of(), hits);
	}
}
