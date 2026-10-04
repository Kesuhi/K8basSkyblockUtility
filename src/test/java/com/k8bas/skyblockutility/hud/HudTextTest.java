package com.k8bas.skyblockutility.hud;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** REQ-HUD-12 (T2.7b): multi-line text elements. */
class HudTextTest {
	/** Every character 6 px wide, lines 9 px apart (the vanilla font's line height). */
	static final TextMeasure SIX = new TextMeasure() {
		@Override
		public int width(String text) {
			return text.length() * 6;
		}

		@Override
		public int lineHeight() {
			return 9;
		}
	};

	/** AC-HUD-11 [A]: of 9 lines with 2 and 5 off, 7 show; the bounds are the text's plus 2 px on every side. */
	@Test
	void theShownLinesAndTheirBoundsPlusTwoPixels() {
		List<String> lines = List.of("one", "two", "three", "four", "a much longer fifth", "six", "seven", "eight", "nine");
		Set<Integer> off = Set.of(2, 5);
		HudText text = HudText.of(lines, line -> !off.contains(line + 1));
		assertEquals(List.of("one", "three", "four", "six", "seven", "eight", "nine"), text.lines());
		assertEquals(5 * 6 + 2 * HudText.MARGIN, text.width(SIX), "the widest shown line, \"three\" or \"seven\" or \"eight\"");
		assertEquals(7 * 9 + 2 * HudText.MARGIN, text.height(SIX), "seven lines, the last one's descender shadow included");
		assertEquals(2, HudText.MARGIN);
	}

	@Test
	void theBoundsFollowTheContent() {
		assertEquals(3 * 6 + 4, HudText.of(List.of("abc"), line -> true).width(SIX));
		assertEquals(10 * 6 + 4, HudText.of(List.of("abc", "abcdefghij"), line -> true).width(SIX));
	}

	/** No line shown, no content: the element draws nothing (REQ-HUD-01). */
	@Test
	void noShownLineIsNoContent() {
		assertNull(HudText.of(List.of("a", "b"), line -> false));
		assertNull(HudText.of(List.of(), line -> true));
	}
}
