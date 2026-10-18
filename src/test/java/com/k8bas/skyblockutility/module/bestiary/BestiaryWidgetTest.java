package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.skyblock.CaptureFixtures;
import com.k8bas.skyblockutility.skyblock.SbNumber;
import com.k8bas.skyblockutility.skyblock.TabParser;
import com.k8bas.skyblockutility.skyblock.TabWidget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-BEST-09 [A], the widget part (T5.2a, REQ-BEST-10, REQ-BEST-17): the `Bestiary:` tab widget lines of the G1
 * captures parse, and so do the provisional forms the captures did not show: "MAX", a Roman tier and an abbreviated
 * count, which parses as approximate. Those three stay UNVERIFIED until a capture shows them (REQ-GS-14).
 */
class BestiaryWidgetTest {
	private static SbNumber exact(long value) {
		return new SbNumber(value, false);
	}

	@Test
	void theCapturedWidgetLinesParse() {
		// G1, 2026-10-01: the Glacite Mineshaft tab (CaptureFixtures, README "What the captures confirm").
		TabWidget widget = TabParser.parse(CaptureFixtures.tabRows(CaptureFixtures.G1, "tab-mineshaft.txt")).widget(BestiaryWidget.HEADER);
		assertNotNull(widget);
		List<WidgetLine> lines = BestiaryWidget.parse(widget);
		assertEquals(widget.getLines().size(), lines.size(), "every line of the widget is a family line: " + widget.getLines());
		assertEquals(new WidgetLine("Glacite Bowman", 12, exact(497), exact(500), false), lines.get(0));
		assertEquals(new WidgetLine("Glacite Mage", 13, exact(541), exact(750), false), lines.get(2));
		assertEquals(new WidgetLine("Glacite Walker", 9, exact(2_346), exact(3_000), false),
				BestiaryWidget.parseLine("Glacite Walker 9: 2,346/3,000"));
		assertEquals(new WidgetLine("Bal", 11, exact(71), exact(80), false), BestiaryWidget.parseLine("Bal 11: 71/80"));
	}

	@Test
	void theDwarvenMinesWidgetParsesAsFarAsItIsShown() {
		// G1: the widget runs to the end of the tab list's last column, so it is cut off (EC-GS-01); what is shown parses.
		TabWidget widget = TabParser.parse(CaptureFixtures.tabRows(CaptureFixtures.G1, "tab-dwarven-mines.txt")).widget(BestiaryWidget.HEADER);
		assertNotNull(widget);
		assertTrue(widget.getCutOff());
		List<WidgetLine> lines = BestiaryWidget.parse(widget);
		assertEquals(widget.getLines().size(), lines.size(), widget.getLines().toString());
		assertTrue(lines.contains(new WidgetLine("Bal", 11, exact(71), exact(80), false)), lines.toString());
	}

	@Test
	void theProvisionalFormsParse() {
		// UNVERIFIED (not in the G1 captures): the SPEC's sample, MAX, a Roman tier and an abbreviated count.
		assertEquals(new WidgetLine("Ghost", 15, exact(12_449), exact(12_500), false), BestiaryWidget.parseLine("Ghost 15: 12,449/12,500"));
		assertEquals(new WidgetLine("Ghost", 25, null, null, true), BestiaryWidget.parseLine("Ghost 25: MAX"));
		assertEquals(new WidgetLine("Ghost", 15, exact(12_449), exact(12_500), false), BestiaryWidget.parseLine("Ghost XV: 12,449/12,500"));
		WidgetLine abbreviated = BestiaryWidget.parseLine("Ghost 15: 12.4k/12.5k");
		assertNotNull(abbreviated);
		assertTrue(abbreviated.kills().getApproximate(), "12.4k is approximate");
		assertEquals(12_400, abbreviated.kills().getValue(), 0.001);
		assertNull(abbreviated.kills().exact(), "never an exact count");
		assertTrue(abbreviated.next().getApproximate());
		// A family name that ends in a numeral keeps it: the tier is the word before the colon.
		assertEquals(new WidgetLine("Barbarian Duke X", 3, exact(4), exact(5), false), BestiaryWidget.parseLine("Barbarian Duke X 3: 4/5"));
		assertFalse(BestiaryWidget.parseLine("Glacite Walker 9: 2,346/3,000").max());
	}

	@Test
	void otherLinesAreNoFamilyLines() {
		for (String line : List.of("", "Ghost", "Ghost 15", "Ghost 15: soon", "Ghost fifteen: 1/2", "Ghost 15: 1/", "Ghost 15: MAX/1", "Combat: 60")) {
			assertNull(BestiaryWidget.parseLine(line), line);
		}
	}
}
