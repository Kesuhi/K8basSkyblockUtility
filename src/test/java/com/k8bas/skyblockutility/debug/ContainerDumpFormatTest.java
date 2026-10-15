package com.k8bas.skyblockutility.debug;

import com.k8bas.skyblockutility.skyblock.CaptureFixtures;
import com.k8bas.skyblockutility.skyblock.MenuSlot;
import com.k8bas.skyblockutility.skyblock.MenuSnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The armed dump on the menu reader (T3.0e, AC-GS-13): every menu capture read back into a menu snapshot
 * (CaptureFixtures.menu) and written again gives the same lines, so the dump's output is unchanged by the move onto
 * the reader. The captures: fixtures/captures/g1-2026-10-01/containers (`/ksu debug dump containers on` on Hypixel,
 * 2026-10-01, written by 1.1.0) and fixtures/captures/g3-2026-10-15/containers (the same dump on 2026-10-15, written
 * by the T3.0e dump itself); see each folder's README. Names are masked.
 */
class ContainerDumpFormatTest {
	private static void everyCaptureIsWrittenTheSameWay(String folder, int expected) throws IOException, URISyntaxException {
		Path dir = Path.of(ContainerDumpFormatTest.class.getResource(folder + "containers").toURI());
		List<Path> files;
		try (Stream<Path> list = Files.list(dir)) {
			files = list.sorted().toList();
		}
		assertEquals(expected, files.size(), "the menu captures in " + folder);
		for (Path file : files) {
			// Git may check them out with CRLF.
			List<String> lines = List.of(Files.readString(file, StandardCharsets.UTF_8).split("\r?\n"));
			CaptureFixtures.MenuCapture capture = CaptureFixtures.menu(lines);
			assertEquals(lines, ContainerDumpFormat.lines(capture.number(), capture.menu(), UnaryOperator.identity()), file.getFileName().toString());
		}
	}

	@Test
	void everyG1CaptureIsWrittenTheSameWay() throws IOException, URISyntaxException {
		everyCaptureIsWrittenTheSameWay(CaptureFixtures.G1, 82);
	}

	@Test
	void everyG3CaptureIsWrittenTheSameWay() throws IOException, URISyntaxException {
		everyCaptureIsWrittenTheSameWay(CaptureFixtures.G3, 10);
	}

	@Test
	void namesTitlesAndLoreAreMaskedButNotItemIds() {
		MenuSnapshot menu = new MenuSnapshot("Alice's Bag", 7, 27,
				List.of(new MenuSlot(3, "minecraft:player_head", 2, "Alice", List.of("Owner: Alice"), "Alice")));
		assertEquals(List.of(
				"[K8BAS-DUMP] container 1 title=Self's Bag slots=27 state=7",
				"[K8BAS-DUMP] container 1 slot 3 item=minecraft:player_head sbid=Alice count=2 | Self",
				"[K8BAS-DUMP] container 1 slot 3 lore 0 | Owner: Self"),
				ContainerDumpFormat.lines(1, menu, text -> text.replace("Alice", "Self")));
	}
}
