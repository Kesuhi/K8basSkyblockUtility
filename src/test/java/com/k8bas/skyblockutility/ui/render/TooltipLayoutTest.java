package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-19, AC-UI-17 (T2.3c): wrapped tooltips that stay inside the window. */
class TooltipLayoutTest {
	/** 6 px a character, like the game's font; a formatting code and its letter take no room. */
	private static final ToIntFunction<String> WIDTH = text -> {
		int width = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '§') {
				i++;
			} else {
				width += 6;
			}
		}
		return width;
	};

	private static final String SIX_LINES = "First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line";

	@Test
	void wrapsAtSpacesAndKeepsLineBreaks() {
		assertEquals(List.of("short"), TooltipLayout.wrap("short", 60, WIDTH));
		assertEquals(List.of("aaaa bbbb", "cccc"), TooltipLayout.wrap("aaaa bbbb cccc", 60, WIDTH));
		assertEquals(List.of("one", "", "two"), TooltipLayout.wrap("one\n\ntwo", 60, WIDTH), "an empty line stays");
		assertEquals(List.of("a", "b"), TooltipLayout.wrap("a   \n  b", 60, WIDTH), "spaces at the ends of lines go");
		assertEquals(List.of(""), TooltipLayout.wrap("", 60, WIDTH));
	}

	@Test
	void aWordLongerThanALineIsCut() {
		List<String> lines = TooltipLayout.wrap("x".repeat(25) + " y", 60, WIDTH);
		assertEquals(List.of("x".repeat(10), "x".repeat(10), "xxxxx y"), lines);
	}

	@Test
	void formattingCarriesOnToTheNextLine() {
		assertEquals(List.of("§caaaa bbbb", "§ccccc"), TooltipLayout.wrap("§caaaa bbbb cccc", 60, WIDTH));
		assertEquals(List.of("§c§laaaa", "§c§lbbbb"), TooltipLayout.wrap("§c§laaaa\nbbbb", 60, WIDTH), "colour and style");
		assertEquals(List.of("§ca §rb", "c"), TooltipLayout.wrap("§ca §rb c", 18, WIDTH), "§r ends it");
		assertEquals("§eb", TooltipLayout.wrap("§la §ea\nb", 6, WIDTH).get(2), "a colour ends the styles before it");
		// A code is never cut from its letter, even inside a cut word.
		for (String line : TooltipLayout.wrap("x".repeat(9) + "§cxxxx", 60, WIDTH)) {
			assertTrue(!line.endsWith("§"), "no dangling §: " + line);
		}
	}

	@Test
	void everyLineFitsTheWidth() {
		String text = "The quick brown fox jumps over the lazy dog, then §6Hypixel§r Skyblock-without-spaces-in-a-row and done.";
		for (int max = 6; max <= 120; max += 7) {
			for (String line : TooltipLayout.wrap(text, max, WIDTH)) {
				assertTrue(WIDTH.applyAsInt(line) <= max, max + ": " + line);
			}
		}
	}

	/** AC-UI-17: a 6-line tooltip at the right and bottom edges lies inside the window at GUI scales 1-4. */
	@Test
	void aSixLineTooltipAtTheEdgesStaysInsideAtEveryScale() {
		for (int[] window : new int[][] {{1280, 960}, {854, 480}, {640, 480}}) {
			for (int scale = 1; scale <= 4; scale++) {
				int w = window[0] / scale;
				int h = window[1] / scale;
				for (int[] mouse : new int[][] {{w - 1, h - 1}, {w - 2, 2}, {1, h - 1}, {w / 2, h / 2}}) {
					TooltipLayout.Box box = TooltipLayout.layout(SIX_LINES, mouse[0], mouse[1], w, h, WIDTH);
					String where = window[0] + "x" + window[1] + " scale " + scale + " mouse " + mouse[0] + "," + mouse[1] + ": " + box;
					assertEquals(6, box.lines().size(), where);
					assertInside(box, w, h, where);
				}
			}
		}
	}

	@Test
	void sitsBesideTheCursorAndFlipsAtTheRightEdge() {
		TooltipLayout.Box box = TooltipLayout.layout("tip", 100, 100, 400, 300, WIDTH);
		assertEquals(100 + TooltipLayout.OFFSET, box.x());
		assertEquals(100 - TooltipLayout.OFFSET, box.y());
		TooltipLayout.Box flipped = TooltipLayout.layout("a tooltip", 395, 100, 400, 300, WIDTH);
		assertTrue(flipped.x() + flipped.width() <= 395, "left of the cursor, not over it: " + flipped);
	}

	@Test
	void longTextWrapsToTheWindowAndIsCutWhenTooTall() {
		String paragraph = "word ".repeat(400).strip();
		TooltipLayout.Box wide = TooltipLayout.layout(paragraph, 10, 10, 1280, 960, WIDTH);
		assertTrue(wide.width() <= TooltipLayout.PREFERRED_TEXT_WIDTH + 2 * TooltipLayout.PADDING, "no wider than the preferred width");
		TooltipLayout.Box tiny = TooltipLayout.layout(paragraph, 10, 10, 160, 60, WIDTH);
		assertInside(tiny, 160, 60, "tiny window: " + tiny);
		assertTrue(tiny.lines().get(tiny.lines().size() - 1).endsWith(Ellipsis.DOTS), "the last line shows that more was cut");
	}

	@Test
	void aCutLineNeverEndsInACodeBeforeItsDots() {
		// 160x60 holds 4 lines; the 4th ends in a lone § and in a code, and both fit with the dots.
		assertEquals("l4...", TooltipLayout.layout("l1\nl2\nl3\nl4§\nl5", 10, 10, 160, 60, WIDTH).lines().get(3));
		assertEquals("l4...", TooltipLayout.layout("l1\nl2\nl3\nl4 §c\nl5", 10, 10, 160, 60, WIDTH).lines().get(3));
	}

	private static void assertInside(TooltipLayout.Box box, int w, int h, String where) {
		assertTrue(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= w && box.y() + box.height() <= h, where);
		for (String line : box.lines()) {
			assertTrue(WIDTH.applyAsInt(line) <= box.width() - 2 * TooltipLayout.PADDING, "the text fits the box: " + line);
		}
	}
}
