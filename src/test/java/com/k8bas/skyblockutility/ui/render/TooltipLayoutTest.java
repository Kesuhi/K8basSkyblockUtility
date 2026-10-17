package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-19, AC-UI-17 (T2.3c): wrapped tooltips that stay inside the window; REQ-UI-44 (T3.34): anchored beside their control. */
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

	/**
	 * AC-UI-45 [A] and AC-UI-17 (REQ-UI-44, EC-UI-24): a 6-line tooltip beside a control at each window edge and corner and in the
	 * middle, at GUI scales 1-4 (1920x1080 and others), at 1366x768 with GUI scale 3 and at 320x240, lies inside the window and does
	 * not overlap its control whenever any side fits; when none does, it covers as little of the control as any place inside the
	 * window beside it would.
	 */
	@Test
	void aSixLineTooltipBesideItsControlStaysInsideAndOffIt() {
		List<int[]> windows = new java.util.ArrayList<>();
		for (int[] window : new int[][] {{1920, 1080}, {1280, 960}, {854, 480}, {640, 480}}) {
			for (int scale = 1; scale <= 4; scale++) {
				windows.add(new int[] {window[0] / scale, window[1] / scale});
			}
		}
		windows.add(new int[] {1366 / 3, 768 / 3});
		windows.add(new int[] {320, 240});
		int checked = 0;
		int besides = 0;
		for (int[] window : windows) {
			int w = window[0];
			int h = window[1];
			for (int[] size : new int[][] {{16, 16}, {22, 12}, {80, 12}, {Math.min(300, w - 8), 40}, {w - 8, 30}}) {
				for (int[] at : new int[][] {{0, 0}, {w - size[0], 0}, {0, h - size[1]}, {w - size[0], h - size[1]}, {(w - size[0]) / 2, 0},
						{(w - size[0]) / 2, h - size[1]}, {0, (h - size[1]) / 2}, {w - size[0], (h - size[1]) / 2}, {(w - size[0]) / 2, (h - size[1]) / 2},
						{4, 4}, {w - size[0] - 4, h - size[1] - 4}}) {
					TooltipLayout.Box box = TooltipLayout.anchored(SIX_LINES, at[0], at[1], size[0], size[1], w, h, WIDTH);
					String where = w + "x" + h + " control " + size[0] + "x" + size[1] + " at " + at[0] + "," + at[1] + ": " + box;
					assertEquals(6, box.lines().size(), where);
					assertInside(box, w, h, where);
					int overlap = overlap(box.x(), box.y(), box.width(), box.height(), at[0], at[1], size[0], size[1]);
					int gap = TooltipLayout.GAP;
					int m = TooltipLayout.MARGIN;
					boolean anySide = at[0] + size[0] + gap + box.width() <= w - m || at[0] - gap - box.width() >= m
							|| at[1] + size[1] + gap + box.height() <= h - m || at[1] - gap - box.height() >= m;
					if (anySide) {
						assertEquals(0, overlap, "a side fits, so it does not cover its control: " + where);
						besides++;
					} else {
						assertTrue(overlap <= leastCover(box.width(), box.height(), at[0], at[1], size[0], size[1], w, h),
								"no side fits: it covers as little of its control as it can: " + where);
					}
					checked++;
				}
			}
		}
		assertTrue(besides > checked / 2 && besides < checked, "both cases were met: " + besides + " of " + checked);
	}

	/** REQ-UI-44: right of its control with a 6 GUI px gap, top to top; flipped left at the right edge, then below, then above. */
	@Test
	void sitsBesideItsControlAndFlipsAsTheWindowNeeds() {
		TooltipLayout.Box right = TooltipLayout.anchored("tip", 100, 100, 40, 16, 400, 300, WIDTH);
		assertEquals(100 + 40 + TooltipLayout.GAP, right.x(), "right of it");
		assertEquals(100, right.y(), "its top at the control's top");
		TooltipLayout.Box left = TooltipLayout.anchored("a tooltip", 340, 100, 40, 16, 400, 300, WIDTH);
		assertEquals(340 - TooltipLayout.GAP, left.x() + left.width(), "flipped to its left at the right edge");
		assertEquals(100, left.y());
		TooltipLayout.Box below = TooltipLayout.anchored("a tooltip", 10, 100, 380, 16, 400, 300, WIDTH);
		assertEquals(100 + 16 + TooltipLayout.GAP, below.y(), "a control as wide as the window: below it");
		assertEquals(10, below.x(), "its left at the control's left");
		TooltipLayout.Box above = TooltipLayout.anchored("a tooltip", 10, 270, 380, 16, 400, 300, WIDTH);
		assertEquals(270 - TooltipLayout.GAP, above.y() + above.height(), "and above it at the bottom");
		TooltipLayout.Box low = TooltipLayout.anchored(SIX_LINES, 100, 290, 40, 6, 400, 300, WIDTH);
		assertEquals(100 + 40 + TooltipLayout.GAP, low.x(), "beside a control at the bottom, still to its right");
		assertEquals(300 - TooltipLayout.MARGIN - low.height(), low.y(), "moved up to stay inside the window");
	}

	private static int overlap(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
		int w = Math.min(ax + aw, bx + bw) - Math.max(ax, bx);
		int h = Math.min(ay + ah, by + bh) - Math.max(ay, by);
		return w > 0 && h > 0 ? w * h : 0;
	}

	/**
	 * The least a box can cover of the control anywhere inside the window, by trying every place: the area covered is the
	 * overlap across times the overlap down, so each axis is tried on its own.
	 */
	private static int leastCover(int bw, int bh, int x, int y, int w, int h, int screenW, int screenH) {
		int m = TooltipLayout.MARGIN;
		int across = Integer.MAX_VALUE;
		for (int px = m; px <= Math.max(m, screenW - m - bw); px++) {
			across = Math.min(across, Math.max(0, Math.min(px + bw, x + w) - Math.max(px, x)));
		}
		int down = Integer.MAX_VALUE;
		for (int py = m; py <= Math.max(m, screenH - m - bh); py++) {
			down = Math.min(down, Math.max(0, Math.min(py + bh, y + h) - Math.max(py, y)));
		}
		return across * down;
	}

	/** EC-UI-24: with no side free, the box takes the place inside the window that covers the least of a large control. */
	@Test
	void withNoSideFreeItCoversTheLeastItCan() {
		// A 100x60 box (the text below) by a control at 50..270 x 50..190 in a 320x240 window: no side fits; at the
		// window's top left corner it covers 54 x 14, less than beside any one side.
		String text = "x".repeat(15) + "\n2\n3\n4\n5\n6";
		TooltipLayout.Box box = TooltipLayout.anchored(text, 50, 50, 220, 140, 320, 240, WIDTH);
		int covered = overlap(box.x(), box.y(), box.width(), box.height(), 50, 50, 220, 140);
		assertEquals(leastCover(box.width(), box.height(), 50, 50, 220, 140, 320, 240), covered, "the least it can: " + box);
	}

	@Test
	void longTextWrapsToTheWindowAndIsCutWhenTooTall() {
		String paragraph = "word ".repeat(400).strip();
		TooltipLayout.Box wide = TooltipLayout.anchored(paragraph, 10, 10, 1, 1, 1280, 960, WIDTH);
		assertTrue(wide.width() <= TooltipLayout.PREFERRED_TEXT_WIDTH + 2 * TooltipLayout.PADDING, "no wider than the preferred width");
		TooltipLayout.Box tiny = TooltipLayout.anchored(paragraph, 10, 10, 1, 1, 160, 60, WIDTH);
		assertInside(tiny, 160, 60, "tiny window: " + tiny);
		assertTrue(tiny.lines().get(tiny.lines().size() - 1).endsWith(Ellipsis.DOTS), "the last line shows that more was cut");
	}

	@Test
	void aCutLineNeverEndsInACodeBeforeItsDots() {
		// 160x60 holds 4 lines; the 4th ends in a lone § and in a code, and both fit with the dots.
		assertEquals("l4...", TooltipLayout.anchored("l1\nl2\nl3\nl4§\nl5", 10, 10, 1, 1, 160, 60, WIDTH).lines().get(3));
		assertEquals("l4...", TooltipLayout.anchored("l1\nl2\nl3\nl4 §c\nl5", 10, 10, 1, 1, 160, 60, WIDTH).lines().get(3));
	}

	private static void assertInside(TooltipLayout.Box box, int w, int h, String where) {
		assertTrue(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= w && box.y() + box.height() <= h, where);
		for (String line : box.lines()) {
			assertTrue(WIDTH.applyAsInt(line) <= box.width() - 2 * TooltipLayout.PADDING, "the text fits the box: " + line);
		}
	}
}
