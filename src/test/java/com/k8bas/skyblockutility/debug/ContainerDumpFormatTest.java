package com.k8bas.skyblockutility.debug;

import com.k8bas.skyblockutility.skyblock.MenuSlot;
import com.k8bas.skyblockutility.skyblock.MenuSnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The armed dump on the menu reader (T3.0e, AC-GS-13): every G1 menu capture
 * (fixtures/captures/g1-2026-10-01/containers, see its README: `/ksu debug dump containers on` on Hypixel,
 * 2026-10-01, names masked) read back into a menu snapshot and written again gives the same lines, so the
 * dump's output is unchanged by the move onto the reader.
 */
class ContainerDumpFormatTest {
	private static final Pattern TITLE = Pattern.compile("^\\[K8BAS-DUMP] container (\\d+) title=(.*) slots=(\\d+) state=(-?\\d+)$");
	private static final Pattern SLOT = Pattern.compile("^\\[K8BAS-DUMP] container \\d+ slot (\\d+) item=(\\S+) sbid=(\\S+) count=(\\d+) \\| (.*)$");
	private static final Pattern LORE = Pattern.compile("^\\[K8BAS-DUMP] container \\d+ slot (\\d+) lore (\\d+) \\| (.*)$");

	private record Capture(int number, MenuSnapshot menu) {
	}

	/** Reads one capture back; lore lines follow their slot line in order. */
	private static Capture parse(List<String> lines) {
		Matcher title = TITLE.matcher(lines.get(0));
		assertTrue(title.matches(), lines.get(0));
		List<MenuSlot> slots = new ArrayList<>();
		int index = -1;
		String item = null;
		String sbid = null;
		int count = 0;
		String name = null;
		List<String> lore = new ArrayList<>();
		for (String line : lines.subList(1, lines.size())) {
			Matcher slot = SLOT.matcher(line);
			Matcher loreLine = LORE.matcher(line);
			if (slot.matches()) {
				if (index >= 0) {
					slots.add(new MenuSlot(index, item, count, name, List.copyOf(lore), sbid.equals("-") ? null : sbid));
				}
				index = Integer.parseInt(slot.group(1));
				item = slot.group(2);
				sbid = slot.group(3);
				count = Integer.parseInt(slot.group(4));
				name = slot.group(5);
				lore.clear();
			} else if (loreLine.matches()) {
				assertEquals(index, Integer.parseInt(loreLine.group(1)), line);
				assertEquals(lore.size(), Integer.parseInt(loreLine.group(2)), line);
				lore.add(loreLine.group(3));
			} else {
				throw new AssertionError("not a container dump line: " + line);
			}
		}
		if (index >= 0) {
			slots.add(new MenuSlot(index, item, count, name, List.copyOf(lore), sbid.equals("-") ? null : sbid));
		}
		return new Capture(Integer.parseInt(title.group(1)),
				new MenuSnapshot(title.group(2), Integer.parseInt(title.group(4)), Integer.parseInt(title.group(3)), List.copyOf(slots)));
	}

	@Test
	void everyG1CaptureIsWrittenTheSameWay() throws IOException, URISyntaxException {
		Path folder = Path.of(ContainerDumpFormatTest.class.getResource("/fixtures/captures/g1-2026-10-01/containers").toURI());
		List<Path> files;
		try (Stream<Path> list = Files.list(folder)) {
			files = list.sorted().toList();
		}
		assertEquals(82, files.size(), "the G1 menu captures");
		for (Path file : files) {
			// Git may check them out with CRLF.
			List<String> lines = List.of(Files.readString(file, StandardCharsets.UTF_8).split("\r?\n"));
			Capture capture = parse(lines);
			assertEquals(lines, ContainerDumpFormat.lines(capture.number(), capture.menu(), UnaryOperator.identity()), file.getFileName().toString());
		}
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
