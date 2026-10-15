package com.k8bas.skyblockutility.module.corpsespots;

import com.k8bas.skyblockutility.data.DataRegistry;
import com.k8bas.skyblockutility.data.TableHandle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped corpse spot table, read through the registry as the game reads it (T3.3a, R40): AC-CORPSE-01 [A] (29
 * codes with 129 spots, 5 without data, together the 34 known codes; every crystal code shares one list) and
 * AC-CORPSE-02 [A] (each of the maintainer's 144 logged sightings lies within 1.0 block of a spot of its own code).
 */
class CorpseSpotTableTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "."));

	private static byte[] resource(String path) {
		try (InputStream in = CorpseSpotTableTest.class.getResourceAsStream("/assets/k8bas_skyblock_utility/data/" + path)) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static CorpseSpots shipped() {
		List<String> warnings = new ArrayList<>();
		DataRegistry registry = new DataRegistry(CorpseSpotTableTest::resource, Runnable::run, Runnable::run, warnings::add);
		TableHandle<CorpseSpots> handle = registry.register(CorpseSpotTable.SPEC);
		registry.start();
		assertEquals(List.of(), warnings, "the table loads without a warning");
		return handle.value();
	}

	@Test
	void theShippedTableHasEveryKnownCode() {
		CorpseSpots spots = shipped();
		assertEquals(29, spots.codes().size());
		assertEquals(129, spots.codes().stream().mapToInt(code -> spots.spots(code).size()).sum());
		assertEquals(Set.of("TOPA_2", "SAPP_2", "AMET_2", "AMBE_2", "JADE_2"), spots.getNoData());
		Set<String> all = new java.util.HashSet<>(spots.codes());
		all.addAll(spots.getNoData());
		assertEquals(34, all.size(), "together the 34 known codes");
		List<CorpseSpot> crystal = spots.spots("PERI_C");
		assertEquals(3, crystal.size());
		for (String code : List.of("RUBY_C", "ONYX_C", "AQUA_C", "CITR_C", "JASP_C", "OPAL_C")) {
			assertEquals(crystal, spots.spots(code), code + " shares the crystal list");
		}
		assertEquals(4, spots.spots("LITT_L").size(), "Littlefoot's den has its own 4");
		assertEquals(List.of(), spots.spots("TOPA_2"));
		assertEquals(List.of(), spots.spots("NOPE_1"));
		assertTrue(spots.knows("TOPA_2") && !spots.knows("NOPE_1"));
		// Numbers are the 1-based places in each code's list, so a label's number stays stable.
		assertEquals(List.of(1, 2, 3, 4, 5), spots.spots("OPAL_1").stream().map(CorpseSpot::number).toList());
	}

	@Test
	void everyLoggedSightingIsNearASpotOfItsCode() throws IOException {
		CorpseSpots spots = shipped();
		List<String> lines = Files.readAllLines(PROJECT.resolve("src/test/resources/fixtures/corpses/sightings.txt"), StandardCharsets.UTF_8)
				.stream().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
		assertEquals(144, lines.size());
		assertEquals(22, lines.stream().map(line -> line.split(" ")[0]).collect(Collectors.toSet()).size());
		List<String> misses = new ArrayList<>();
		for (String line : lines) {
			String[] parts = line.split(" ");
			String[] xyz = parts[2].split(",");
			int x = Integer.parseInt(xyz[0]), y = Integer.parseInt(xyz[1]), z = Integer.parseInt(xyz[2]);
			boolean near = spots.spots(parts[0]).stream().anyMatch(spot -> Math.sqrt(square(spot.x() - x) + square(spot.y() - y) + square(spot.z() - z)) <= 1.0);
			if (!near) {
				misses.add(line);
			}
		}
		assertEquals(List.of(), misses);
	}

	private static double square(int value) {
		return (double) value * value;
	}
}
