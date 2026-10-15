package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-GS-03 / AC-GS-03 [A]: sidebar lines composed from team prefix, entry and suffix, with Hypixel's
 * formatting and filler tokens removed. Captures: sidebar-mineshaft.txt and sidebar-dungeon-hub.txt (G1,
 * sanitised, see the fixture README); the separate prefix/entry/suffix split is synthetic, because the dump
 * logged each line composed.
 */
class SidebarParserTest {
	@Test
	void theShaftLineIsSplitAcrossPrefixEntryAndSuffix() {
		// AC-GS-03: the first line reads exactly "09/26/26 m000XX RUBY_2" (server id = the fixture placeholder).
		SidebarSnapshot sidebar = SidebarParser.parse(new SidebarRaw("SKYBLOCK", List.of(
				new SidebarLine("§709/26/26 §8m000", "§v", "§8XX RUBY_2"),
				new SidebarLine("  ", "§u", ""),
				new SidebarLine(" Autumn 1", "§t", "0th"))));
		assertEquals("09/26/26 m000XX RUBY_2", sidebar.getLines().get(0));
		assertEquals(List.of("09/26/26 m000XX RUBY_2", "", "Autumn 10th"), sidebar.getLines());
		assertEquals("SKYBLOCK", sidebar.getTitle());
	}

	@Test
	void theMineshaftCaptureReadsAsShown() {
		SidebarSnapshot sidebar = SidebarParser.parse(CaptureFixtures.sidebar("sidebar-mineshaft.txt"));
		assertEquals("SKYBLOCK", sidebar.getTitle());
		assertEquals(List.of("10/01/26 m000XX UMBE_1", "", "Autumn 10th", "3:00am", " Glacite Mineshafts", "",
				"Cold: -8❄", "Event Bonus: +16", "", "www.hypixel.net"), sidebar.getLines());
	}

	@Test
	void theG3MineshaftCaptureReadsAsShown() {
		// sidebar-mineshaft-onyx.txt (G3, 2026-10-15): here Hypixel split the server id after three characters.
		SidebarSnapshot sidebar = SidebarParser.parse(CaptureFixtures.sidebar(CaptureFixtures.G3, "sidebar-mineshaft-onyx.txt"));
		assertEquals("SKYBLOCK", sidebar.getTitle());
		assertEquals("10/15/26 m000XX ONYX_1", sidebar.getLines().get(0));
		assertTrue(sidebar.getLines().contains("Late Spring 22nd"), sidebar.getLines().toString());
		assertTrue(sidebar.getLines().contains("Cold: -2❄"), sidebar.getLines().toString());
		assertEquals("www.hypixel.net", sidebar.getLines().get(sidebar.getLines().size() - 1));
	}

	@Test
	void wordsAndNumbersSplitByFillerTokensAreJoined() {
		SidebarSnapshot sidebar = SidebarParser.parse(CaptureFixtures.sidebar("sidebar-dungeon-hub.txt"));
		assertEquals(List.of("10/01/26 m000XX", "", "Autumn 10th", "3:10am ☽", " Dungeon Hub", "",
				"Purse: 311,732,284", "Bits: 880,422", "", "Objective", "Run the race in 120s", "", "www.hypixel.net"),
				sidebar.getLines());
	}

	@Test
	void noFormattingOrInvisibleCharacterSurvives() {
		for (String file : List.of(CaptureFixtures.G1 + "sidebar-mineshaft.txt", CaptureFixtures.G1 + "sidebar-dungeon-hub.txt",
				CaptureFixtures.G3 + "sidebar-mineshaft-onyx.txt")) {
			for (String line : SidebarParser.parse(CaptureFixtures.sidebar("", file)).getLines()) {
				assertTrue(line.indexOf('§') < 0, file + ": " + line);
				assertEquals(line.strip(), line, file + ": trimmed: '" + line + "'");
			}
		}
		// Zero-width characters and a lone § at the end (a code cut off by the server) go too.
		SidebarSnapshot odd = SidebarParser.parse(new SidebarRaw("§6§lSKY​BLOCK", List.of(
				new SidebarLine("Bits: §b8​80", "§q", "§"))));
		assertEquals("SKYBLOCK", odd.getTitle());
		assertEquals(List.of("Bits: 880"), odd.getLines());
	}

	@Test
	void noObjectiveIsTheEmptySidebar() {
		assertEquals(SidebarSnapshot.EMPTY, SidebarParser.parse(new SidebarRaw("", List.of())));
		assertTrue(SidebarSnapshot.EMPTY.getLines().isEmpty());
	}
}
