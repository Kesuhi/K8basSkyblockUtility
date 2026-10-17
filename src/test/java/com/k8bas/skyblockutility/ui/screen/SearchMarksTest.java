package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.SearchIndex;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-55 (REQ-UI-51, T3.44): where a search query shows in the text a card draws. Every occurrence, matched as the search
 * matches (trimmed, case-insensitive), in the text's own characters; overlapping or touching ones share one box; a match
 * the ellipsis cuts is marked over its visible part only; EC-UI-29 (pattern characters, letters whose lower case is longer).
 */
class SearchMarksTest {
	/** 6 GUI px per character, as the tests of the layout measure. */
	private static final ToIntFunction<String> SIX = text -> text.length() * 6;

	private static List<int[]> ranges(String text, String raw) {
		return SearchMarks.ranges(text, SearchIndex.normalize(raw));
	}

	private static void assertRanges(List<int[]> actual, int[]... expected) {
		assertEquals(expected.length, actual.size(), "ranges: " + actual.stream().map(java.util.Arrays::toString).toList());
		for (int i = 0; i < expected.length; i++) {
			assertArrayEquals(expected[i], actual.get(i), "range " + i);
		}
	}

	@Test
	void everyOccurrenceIgnoringCase() {
		assertRanges(ranges("Mob scan range", "scan"), new int[] {4, 8});
		assertRanges(ranges("Mob SCAN range", "  Scan "), new int[] {4, 8});
		assertRanges(ranges("Scan, then scan again", "scan"), new int[] {0, 4}, new int[] {11, 15});
		assertRanges(ranges("Mob scan range", "zzzz"));
		assertRanges(ranges("Mob scan range", ""), new int[0][]);
	}

	/** "an" in "banana" at 1 and 3: touching, so one box from 1 to 5; "aa" in "aaa" at 0 and 1: overlapping, one box. */
	@Test
	void overlappingOrTouchingMatchesShareOneBox() {
		assertRanges(ranges("banana", "an"), new int[] {1, 5});
		assertRanges(ranges("aaa", "aa"), new int[] {0, 3});
		assertRanges(ranges("an an", "an"), new int[] {0, 2}, new int[] {3, 5});
	}

	/** EC-UI-29: pattern characters are plain text; a letter whose lower case is longer maps back to its own character. */
	@Test
	void plainTextAndLettersThatChangeLength() {
		assertRanges(ranges("Fill (%)", "(%)"), new int[] {5, 8});
		assertRanges(ranges("a.b*c", ".b*"), new int[] {1, 4});
		// İ lower-cases to two characters, i and a combining dot; the match after it still covers only its own letters.
		assertRanges(ranges("İSCAN", "scan"), new int[] {1, 5});
		assertRanges(ranges("İstanbul", "i̇s"), new int[] {0, 2});
		// A word-final sigma lower-cases to ς only in the whole word, as the search lower-cases it: the match is found too.
		assertRanges(ranges("ΟΔΟΣ", "ος"), new int[] {2, 4});
	}

	/** A description wrapped into lines: each line gets the ranges that lie in it, also a match split by the wrap. */
	@Test
	void wrappedLinesGetTheirPartOfEachMatch() {
		String full = "How far the scan reaches around you";
		List<String> lines = List.of("How far the sc", "an reaches around you");
		List<List<int[]>> marks = SearchMarks.inLines(full, lines, "scan", false);
		// "How far the sc" holds "sc" of "scan" at 12-14; the next line "an" at 0-2.
		assertRanges(marks.get(0), new int[] {12, 14});
		assertRanges(marks.get(1), new int[] {0, 2});
	}

	/** The last line cut with the ellipsis: a match cut by it is marked over its visible part only, the dots never. */
	@Test
	void aMatchCutByTheEllipsisIsMarkedWhereItShows() {
		String full = "Mob scan range for every island";
		String cut = Ellipsis.fit(full, 6 * 9, SIX);
		assertEquals("Mob sc...", cut);
		assertRanges(SearchMarks.inLines(full, List.of(cut), "scan", true).get(0), new int[] {4, 6});
		assertRanges(SearchMarks.inLines(full, List.of(cut), "range", true).get(0));
		assertRanges(SearchMarks.inLines(full, List.of(cut), "...", true).get(0));
		// A cut line whose start comes again later with dots after it: the dots are the ellipsis, never the text's.
		String again = "Hi there Hi... x";
		assertRanges(SearchMarks.inLines(again, List.of("Hi..."), "i.", true).get(0));
		// Not cut, dots that are the text's own are marked.
		assertRanges(SearchMarks.inLines("Wait...", List.of("Wait..."), "...", false).get(0), new int[] {4, 7});
	}

	/** A formatting code the wrap carried to a line's start is not in the text there: the line is found after it. */
	@Test
	void aCarriedFormattingCodeIsSkipped() {
		List<List<int[]>> marks = SearchMarks.inLines("§aGreen scan", List.of("§aGreen", "§ascan"), "scan", false);
		assertRanges(marks.get(0));
		assertRanges(marks.get(1), new int[] {2, 6});
	}

	/**
	 * A line that does not stand where the one before ended (the wrap closed up a double space) marks nothing, nor does any
	 * line after it, as no match of the search could be placed there.
	 */
	@Test
	void fromALineTheWrapChangedNothingIsMarked() {
		List<List<int[]>> marks = SearchMarks.inLines("Mob  scan range", List.of("Mob scan", "range"), "range", false);
		assertRanges(marks.get(0));
		assertRanges(marks.get(1));
		assertRanges(SearchMarks.inLines("Mob scan range", List.of("Mob scan", "range"), "range", false).get(1), new int[] {0, 5});
	}

	/** REQ-UI-51: a box is the accent at 35 % alpha (89 of 255), with corners of 2 GUI px. */
	@Test
	void aBoxIsTheAccentAt35PercentWithRadius2() {
		int colour = OptionCard.markColour(0xFF29B6B2);
		assertEquals(Math.round(0.35F * 255F), colour >>> 24);
		assertEquals(0x29B6B2, colour & 0xFFFFFF, "the accent's colour");
		assertEquals(2, OptionCard.MARK_RADIUS);
	}

	/** The boxes span the matched glyphs' advance, from the line's top less one row, as high as the line. */
	@Test
	void boxesSpanTheMatchedAdvance() {
		List<int[]> boxes = SearchMarks.boxes("Mob scan range", ranges("Mob scan range", "scan"), 100, 50, 9, SIX);
		assertEquals(1, boxes.size());
		assertArrayEquals(new int[] {100 + 24, 49, 24, 9}, boxes.get(0));
		assertTrue(SearchMarks.boxes("Mob scan range", List.of(), 100, 50, 9, SIX).isEmpty());
	}
}
