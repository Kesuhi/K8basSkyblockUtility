package com.k8bas.skyblockutility.module.bestiary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-BEST-12 [R], in part (REQ-BEST-13, REQ-BEST-12, REQ-GS-18): the Bestiary Tracker's sources send nothing to the
 * server, click or open nothing, use no Hypixel API, and name no widget command of their own (the shared hint does).
 */
class BestiaryRulesTest {
	@Test
	void theModuleOnlyReads() throws IOException {
		Path dir = Path.of(System.getProperty("k8bas.projectDir", ".")).resolve("src/main/kotlin/com/k8bas/skyblockutility/module/bestiary");
		List<String> hits = new ArrayList<>();
		int files = 0;
		try (Stream<Path> list = Files.list(dir)) {
			for (Path file : list.toList()) {
				String code = Files.readString(file);
				files++;
				for (String forbidden : List.of("sendCommand", "sendChat", "connection.send", "handleInventory", "handleContainerInput", "closeContainer",
						"setScreen", "api.hypixel.net", "/be ", "/widget", "/widgets", "/tablist")) {
					if (code.contains(forbidden)) {
						hits.add(file.getFileName() + ": " + forbidden);
					}
				}
			}
		}
		assertTrue(files >= 6, files + " files");
		assertEquals(List.of(), hits);
	}
}
