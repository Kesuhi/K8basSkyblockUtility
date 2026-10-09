package com.k8bas.skyblockutility.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.HudConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-HUD-03, REQ-CFG-12, EC-HUD-04, EC-HUD-05 (T2.7): positions in `general.hud.positions`. */
class HudPositionsTest {
	private static final HudPosition DEFAULT = new HudPosition(HudAnchor.TOP_LEFT, 4, 4, 1);

	private final HudConfig[] config = {null};
	private final List<String> logged = new ArrayList<>();
	private final HudPositions positions = new HudPositions(() -> config[0], () -> {
		if (config[0] == null) {
			config[0] = new HudConfig();
		}
		return config[0];
	}, logged::add);

	private static JsonElement json(String text) {
		return JsonParser.parseString(text);
	}

	@Test
	void withoutAnEntryTheDefaultApplies() {
		assertEquals(DEFAULT, positions.get("odds", DEFAULT));
		assertNull(config[0], "reading creates nothing in the file");
	}

	@Test
	void aStoredPositionRoundTrips() {
		HudPosition placed = new HudPosition(HudAnchor.BOTTOM_RIGHT, -6, -12, 1.257);
		positions.set("odds", placed);
		assertEquals(json("{\"anchor\":\"BOTTOM_RIGHT\",\"x\":-6,\"y\":-12,\"scale\":1.26}"), config[0].positions.get("odds"));
		assertEquals(new HudPosition(HudAnchor.BOTTOM_RIGHT, -6, -12, 1.26), positions.get("odds", DEFAULT));
	}

	/** EC-HUD-04: an entry for an unknown id is kept as it is, and setting another leaves it alone. */
	@Test
	void anUnknownIdIsKeptUnchanged() {
		config[0] = new HudConfig();
		JsonElement unknown = json("{\"anchor\":\"CENTER\",\"x\":1,\"y\":2,\"scale\":1.0,\"future\":true}");
		config[0].positions.put("from_a_newer_version", unknown);
		positions.set("odds", DEFAULT);
		assertSame(unknown, config[0].positions.get("from_a_newer_version"));
	}

	/** EC-HUD-05: a malformed entry falls back to the default place, its scale held to the range; logged once, not rewritten. */
	@Test
	void aMalformedEntryUsesTheDefaultPlaceAndIsNotRewritten() {
		config[0] = new HudConfig();
		List<String> bad = List.of("{\"anchor\":\"NOWHERE\",\"x\":5,\"y\":5,\"scale\":2.0}", "{\"anchor\":\"CENTER\",\"x\":5,\"y\":5,\"scale\":0}",
				"{\"anchor\":\"CENTER\",\"x\":5,\"y\":5,\"scale\":\"NaN\"}", "{\"anchor\":\"CENTER\",\"x\":5,\"y\":5,\"scale\":10}",
				"{\"anchor\":\"CENTER\",\"x\":5}", "[1,2,3]", "\"centre\"");
		List<Double> scales = List.of(2.0, 0.5, 1.0, 3.0, 1.0, 1.0, 1.0);
		for (int i = 0; i < bad.size(); i++) {
			JsonElement entry = json(bad.get(i));
			config[0].positions.put("e" + i, entry);
			HudPosition read = positions.get("e" + i, DEFAULT);
			assertEquals(new HudPosition(DEFAULT.anchor(), DEFAULT.x(), DEFAULT.y(), scales.get(i)), read, bad.get(i));
			positions.get("e" + i, DEFAULT);
			assertSame(entry, config[0].positions.get("e" + i), "not rewritten: " + bad.get(i));
		}
		assertEquals(bad.size(), logged.size(), "each logged once: " + logged);
		assertTrue(logged.get(0).contains("e0"), logged.get(0));
	}

	@Test
	void aParsedEntryIsReadOnceUntilItChanges() {
		positions.set("odds", DEFAULT);
		HudPosition first = positions.get("odds", DEFAULT);
		assertSame(first, positions.get("odds", DEFAULT), "the draw path reuses the parsed entry");
		positions.set("odds", new HudPosition(HudAnchor.CENTER, 0, 0, 2));
		assertEquals(HudAnchor.CENTER, positions.get("odds", DEFAULT).anchor());
	}

	/** AC-HUD-10 [R], made repeatable: the HUD package does no file, network or blocking work. */
	@Test
	void theHudPackageDoesNoIo() throws IOException {
		Pattern io = Pattern.compile("java\\.io\\.|java\\.nio\\.file|java\\.net\\.|HttpClient|ConfigManager\\.(save|flush)|\\.join\\(\\)|\\.get\\(\\d|Thread\\.sleep|\\.await");
		// The package's Java and Kotlin sources (R30 moved some of it to Kotlin).
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		int files = 0;
		for (String language : List.of("java", "kotlin")) {
			Path dir = project.resolve("src/main/" + language + "/com/k8bas/skyblockutility/hud");
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> walk = Files.walk(dir)) {
				for (Path file : walk.filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt")).toList()) {
					files++;
					assertTrue(!io.matcher(Files.readString(file)).find(), file.getFileName() + " does I/O or blocks");
				}
			}
		}
		assertTrue(files >= 15, "the scan sees the whole package: " + files);
	}
}
