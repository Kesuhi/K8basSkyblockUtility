package com.k8bas.skyblockutility.highlight;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Name-line resolution (T3.1, REQ-GLOW-09, AC-GLOW-08 [A]): action labels ("CLICK"), empty lines and lines whose
 * name is not shown are skipped, then the nearest remaining line is the entity's name. The table is SPEC's;
 * the Mort case is the G1 entity capture (fixtures/captures/g1-2026-10-01/entities-dungeon-hub.txt, see its
 * README). Labels other than CLICK are UNVERIFIED until captured.
 */
class NameLinesTest {
	private static NameLines.Line line(String text, double heightAboveFeet, boolean shown) {
		return new NameLines.Line(text, heightAboveFeet * heightAboveFeet, shown);
	}

	@Test
	void theNameWinsOverANearerClickLabel() {
		// "Tomioka" 2.0 above the feet plus "CLICK" 1.7 above → "Tomioka".
		assertEquals("Tomioka", NameLines.pick(List.of(line("Tomioka", 2.0, true), line("CLICK", 1.7, true))));
	}

	@Test
	void aClickLabelAloneIsNoName() {
		// "CLICK" only → no line; the caller falls back to the entity's own name or empty.
		assertNull(NameLines.pick(List.of(line("CLICK", 1.7, true))));
		assertNull(NameLines.pick(List.of(line("§e§lCLICK", 1.7, true))), "with Hypixel's colour codes");
	}

	@Test
	void oneMobTagAloneIsThatTagUnchanged() {
		assertEquals("[Lv55] Zealot 13k/13k❤", NameLines.pick(List.of(line("[Lv55] Zealot 13k/13k❤", 2.2, true))));
	}

	@Test
	void aHiddenNameAndAnEmptyLineAreIgnored() {
		assertEquals("Duncan", NameLines.pick(List.of(line("Hidden", 1.0, false), line("  ", 1.2, true), line("Duncan", 2.0, true))));
	}

	@Test
	void otherClickLabelsAreSkippedAndWordsMerelyContainingClickAreNot() {
		assertEquals("Trinity", NameLines.pick(List.of(line("RIGHT CLICK", 1.5, true), line("Trinity", 2.0, true))));
		assertEquals("Clicker Bob", NameLines.pick(List.of(line("Clicker Bob", 1.5, true), line("Trinity", 2.0, true))));
	}

	private static final Pattern DUMP = Pattern.compile(
			"^\\[K8BAS-DUMP] entities \\d+ type=\\S+ invisible=\\S+ pos=(-?[\\d.]+),(-?[\\d.]+),(-?[\\d.]+) below=(\\S+) dy=(\\S+) \\| (.*)$");

	/** The stands over Mort in the G1 capture, as lines measured from feet {@code feetDrop} below where the capture puts them. */
	private static List<NameLines.Line> mortLines(double feetDrop) throws IOException {
		String text;
		try (InputStream in = NameLinesTest.class.getResourceAsStream("/fixtures/captures/g1-2026-10-01/entities-dungeon-hub.txt")) {
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		record Stand(double x, double y, double z, String below, String dy, String name) {
		}
		List<Stand> stands = new ArrayList<>();
		for (String raw : text.split("\r?\n")) {
			Matcher m = DUMP.matcher(raw);
			assertTrue(m.matches(), raw);
			stands.add(new Stand(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)), m.group(4), m.group(5), m.group(6)));
		}
		Stand marker = stands.stream().filter(s -> s.below().equals("minecraft:player")).findFirst().orElseThrow();
		double feetX = marker.x(), feetY = marker.y() - Double.parseDouble(marker.dy()) - feetDrop, feetZ = marker.z();
		List<NameLines.Line> lines = new ArrayList<>();
		for (Stand s : stands) {
			double dx = s.x() - feetX, dy = s.y() - feetY, dz = s.z() - feetZ;
			// Within the resolver's search box around a player-sized body (half width 0.3 + 1 block).
			if (Math.abs(dx) <= 1.3 && Math.abs(dz) <= 1.3 && dy >= -0.5 && dy <= 1.8 + 2.5) {
				lines.add(new NameLines.Line(s.name(), dx * dx + dy * dy + dz * dz, true));
			}
		}
		assertEquals(3, lines.size(), "the three stands over Mort, not Adventurer Saul's: " + lines);
		return lines;
	}

	/** Nearest-wins without skipping anything: what the resolver did before T3.1. */
	private static String nearest(List<NameLines.Line> lines) {
		return lines.stream().min(java.util.Comparator.comparingDouble(NameLines.Line::distanceSq)).orElseThrow().text();
	}

	@Test
	void theCapturedNameTagsOverMortResolveToMort() throws IOException {
		// AC-GLOW-08 [A] on the G1 capture: "CLICK", "Mort" and "GATE KEEPER" over Mort's body, whose feet are the
		// stand marked below=minecraft:player minus its dy. The name stand is at the feet, so the old nearest-wins gave
		// "Mort" too: the capture shows no regression, not the fix.
		List<NameLines.Line> lines = mortLines(0);
		assertEquals("Mort", NameLines.pick(lines));
		assertEquals("Mort", nearest(lines), "the capture's layout never fooled nearest-wins");
	}

	@Test
	void withTheFeetLowerTheCapturedClickLabelIsNearestAndStillSkipped() throws IOException {
		// The same stands with the body 0.15 lower: CLICK is now the nearest line, the case REQ-GLOW-09 guards.
		List<NameLines.Line> lines = mortLines(0.15);
		assertEquals("CLICK", nearest(lines), "nearest-wins would take the label");
		assertEquals("Mort", NameLines.pick(lines));
	}
}
