package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-GS-02 / AC-GS-02 [A]: the tab widgets of the G1 captures (tab-mineshaft.txt: a Glacite Mineshaft with
 * Frozen Corpses; tab-dwarven-mines.txt: the Dwarven Mines without it; both sanitised, see the fixture
 * README) plus synthetic rows for shapes no capture holds (an empty widget, an unknown line: UNVERIFIED).
 */
class TabParserTest {
	private static final List<TabRow> MINESHAFT = CaptureFixtures.tabRows("tab-mineshaft.txt");
	private static final List<TabRow> DWARVEN_MINES = CaptureFixtures.tabRows("tab-dwarven-mines.txt");

	@Test
	void widgetsGroupByHeaderInTheirColumnOrder() {
		TabSnapshot tab = TabParser.parse(MINESHAFT);
		TabWidget corpses = tab.widget("Frozen Corpses");
		assertNotNull(corpses);
		assertEquals(List.of("Lapis: NOT LOOTED", "Lapis: LOOTED", "Lapis: LOOTED"), corpses.getLines());
		assertEquals(WidgetPresence.PRESENT, tab.presence("Frozen Corpses"));
		assertEquals(List.of("Glacite Bowman 12: 497/500", "Glacite Caver 13: 685/750", "Glacite Mage 13: 541/750",
				"Glacite Mutt 13: 698/750"), tab.widget("Bestiary").getLines());
		assertEquals(List.of("Opal: ✖ Not Found", "Aquamarine: ✖ Not Found", "Peridot: ✖ Not Found",
				"Citrine: ✖ Not Found", "Onyx: ✖ Not Found"), tab.widget("Crystals").getLines());
		assertEquals(List.of("Mining Speed: 8742", "Mining Fortune: 2034", "Cold Resistance: 68"),
				tab.widget("Stats").getLines());
		assertTrue(tab.getUnknownLines().isEmpty(), "every captured line has a known shape: " + tab.getUnknownLines());
	}

	@Test
	void headersWithAnInlineValueAndKeyedLines() {
		TabSnapshot tab = TabParser.parse(MINESHAFT);
		assertEquals("Mineshaft", tab.widget("Area").getInline());
		TabWidget profile = tab.widget("Profile");
		assertEquals("Raspberry", profile.getInline());
		// The SB Level line of REQ-GS-02 sits inside the Profile widget.
		assertEquals("[435] 28/100 XP", profile.value("SB Level"));
		assertEquals("1B", profile.value("Bank"));
		assertNull(profile.value("Purse"));
		assertEquals("m000XX", tab.widget("Area").value("Server"));
	}

	@Test
	void lineOrderDoesNotDependOnTheClientsListOrder() {
		TabSnapshot expected = TabParser.parse(MINESHAFT);
		for (int seed = 0; seed < 20; seed++) {
			List<TabRow> shuffled = new ArrayList<>(MINESHAFT);
			Collections.shuffle(shuffled, new Random(seed));
			assertEquals(expected, TabParser.parse(shuffled), "seed " + seed);
		}
		List<TabRow> dwarven = new ArrayList<>(DWARVEN_MINES);
		Collections.shuffle(dwarven, new Random(7));
		assertEquals(TabParser.parse(DWARVEN_MINES), TabParser.parse(dwarven));
	}

	@Test
	void absentEmptyAndPresentAreDifferent() {
		TabSnapshot dwarven = TabParser.parse(DWARVEN_MINES);
		assertEquals(WidgetPresence.ABSENT, dwarven.presence("Frozen Corpses"));
		assertNull(dwarven.widget("Frozen Corpses"));
		assertEquals(WidgetPresence.PRESENT, dwarven.presence("Skills"));

		// No capture holds a header without entries (UNVERIFIED shape): a header followed by a blank row.
		List<TabRow> rows = new ArrayList<>(MINESHAFT);
		rows.removeIf(row -> row.name().startsWith("!C-n") || row.name().startsWith("!C-o") || row.name().startsWith("!C-p"));
		rows.add(new TabRow("!C-n", 0, ""));
		TabSnapshot empty = TabParser.parse(rows);
		assertEquals(WidgetPresence.EMPTY, empty.presence("Frozen Corpses"));
		assertEquals(List.of(), empty.widget("Frozen Corpses").getLines());
	}

	@Test
	void aHeaderWithOnlyAnInlineValueHasData() {
		// Synthetic (UNVERIFIED): `Profile: Raspberry` with its entries hidden, then a blank row.
		List<TabRow> rows = new ArrayList<>(MINESHAFT);
		rows.removeIf(row -> row.name().compareTo("!B-h") >= 0 && row.name().compareTo("!B-j") <= 0);
		rows.add(new TabRow("!B-h", 0, ""));
		TabSnapshot tab = TabParser.parse(rows);
		assertEquals(List.of(), tab.widget("Profile").getLines());
		assertEquals("Raspberry", tab.widget("Profile").getInline());
		assertEquals(WidgetPresence.PRESENT, tab.presence("Profile"));
	}

	@Test
	void aWidgetAtTheEndOfTheGridMayBeCutOff() {
		// EC-GS-01: in tab-dwarven-mines.txt Bestiary runs from !D-r to the last row !D-t with no blank row
		// after it, so more families may follow out of sight: the lines are kept, but marked as maybe partial.
		TabSnapshot dwarven = TabParser.parse(DWARVEN_MINES);
		TabWidget bestiary = dwarven.widget("Bestiary");
		assertEquals(List.of("Bal 11: 71/80", "Glacite Walker 9: 2,346/3,000"), bestiary.getLines());
		assertTrue(bestiary.getCutOff());
		assertEquals(WidgetPresence.PARTIAL, dwarven.presence("Bestiary"));
		// A blank row or the next header ends a widget normally.
		assertFalse(dwarven.widget("Event Trackers").getCutOff());
		assertFalse(dwarven.widget("Forges").getCutOff());

		// Synthetic: only the header fits at the end of the grid; that is not "present but empty".
		List<TabRow> rows = new ArrayList<>(MINESHAFT);
		rows.removeIf(row -> row.name().equals("!D-t"));
		rows.add(new TabRow("!D-t", 0, "Frozen Corpses:"));
		// Hypixel always sends all 80 grid rows: the old widget's rows turn blank rather than disappear.
		for (char r = 'm'; r <= 'p'; r++) {
			String name = "!C-" + r;
			rows.removeIf(row -> row.name().equals(name));
			rows.add(new TabRow(name, 0, ""));
		}
		TabSnapshot cut = TabParser.parse(rows);
		assertEquals(WidgetPresence.PARTIAL, cut.presence("Frozen Corpses"));

		// A widget that runs into the next column's title is cut off at the column end, too.
		List<TabRow> column = new ArrayList<>(MINESHAFT);
		column.removeIf(row -> row.name().compareTo("!C-q") >= 0 && row.name().compareTo("!C-t") <= 0);
		for (char r = 'q'; r <= 't'; r++) {
			column.add(new TabRow("!C-" + r, 0, " Lapis: LOOTED"));
		}
		TabWidget corpses = TabParser.parse(column).widget("Frozen Corpses");
		assertEquals(7, corpses.getLines().size());
		assertTrue(corpses.getCutOff());
	}

	@Test
	void anUnknownLineIsFlaggedNotGuessed() {
		// Synthetic (UNVERIFIED): a line in a widget column that is neither a header nor an entry.
		List<TabRow> rows = new ArrayList<>(MINESHAFT);
		rows.removeIf(row -> row.name().equals("!D-h"));
		rows.add(new TabRow("!D-h", 0, "Something new"));
		TabSnapshot tab = TabParser.parse(rows);
		assertEquals(List.of("Something new"), tab.getUnknownLines());
		// The widgets around it still group as before.
		assertEquals(TabParser.parse(MINESHAFT).widget("Crystals"), tab.widget("Crystals"));
		assertEquals(TabParser.parse(MINESHAFT).widget("Bestiary"), tab.widget("Bestiary"));
	}

	@Test
	void playerRowsAreNeverKept() {
		// The Players column lists real players (masked in the fixture); the snapshot holds widgets only.
		for (List<TabRow> rows : List.of(MINESHAFT, DWARVEN_MINES)) {
			TabSnapshot tab = TabParser.parse(rows);
			String all = tab.toString();
			assertFalse(all.contains("Self"), all);
			assertFalse(all.contains("Player2"), all);
			assertFalse(all.contains("other players"), all);
			assertTrue(tab.getUnknownLines().isEmpty(), tab.getUnknownLines().toString());
		}
	}

	@Test
	void onlyTheEightyRowsVanillaShowsAreRead() {
		// EC-GS-01: rows past the 80 vanilla draws are not read; nothing is inferred for them.
		List<TabRow> rows = new ArrayList<>(MINESHAFT);
		rows.add(new TabRow("!E-a", 0, "Hidden:"));
		rows.add(new TabRow("!E-b", 0, " Never: shown"));
		assertEquals(TabParser.parse(MINESHAFT), TabParser.parse(rows));
	}

	@Test
	void noRowsGiveTheEmptySnapshot() {
		assertEquals(TabSnapshot.EMPTY, TabParser.parse(List.of()));
		assertEquals(WidgetPresence.ABSENT, TabSnapshot.EMPTY.presence("Profile"));
	}
}
