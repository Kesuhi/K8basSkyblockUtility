package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-17, REQ-UI-02 (T2.2): rounded corners from fills, text that fits, and clip rects. */
class ShapesMathTest {
	@Test
	void cornerInsetsFollowThePixelCentres() {
		assertArrayEquals(new int[] {2, 1, 0, 0}, Corners.insets(4));
		assertArrayEquals(new int[] {3, 1, 1, 0, 0}, Corners.insets(5));
		assertArrayEquals(new int[] {4, 2, 1, 1, 0, 0}, Corners.insets(6));
		assertArrayEquals(new int[0], Corners.insets(0));
		int[] large = Corners.insets(24);
		assertEquals(0, large[large.length - 1]);
		for (int i = 1; i < large.length; i++) {
			assertTrue(large[i] <= large[i - 1], "non-increasing");
		}
	}

	@Test
	void aRoundedRectIsDisjointMirroredSpans() {
		List<Corners.Span> spans = Corners.fill(20, 20, 6);
		assertEquals(7, spans.size(), "rows with the same inset are merged");
		assertEquals(new Corners.Span(4, 0, 16, 1), spans.get(0));
		assertEquals(new Corners.Span(4, 19, 16, 20), spans.get(spans.size() - 1));
		assertEquals(400 - 4 * 8, area(spans));
		assertDisjoint(spans);
		assertEquals(Corners.fill(6, 6, 3), Corners.fill(6, 6, 6), "the radius is clamped to half the shorter side");
		assertTrue(Corners.fill(0, 10, 4).isEmpty());
		assertTrue(Corners.fill(10, -1, 4).isEmpty());
		assertEquals(List.of(new Corners.Span(0, 0, 10, 5)), Corners.fill(10, 5, 0), "radius 0 is a plain rect");
	}

	@Test
	void anOutlineLiesBetweenTheOuterAndInnerShapes() {
		List<Corners.Span> ring = Corners.ring(20, 20, 6, 1);
		assertDisjoint(ring);
		boolean[][] outer = cover(Corners.fill(20, 20, 6), 20, 20);
		boolean[][] inner = cover(Corners.fill(18, 18, 5), 18, 18);
		boolean[][] drawn = cover(ring, 20, 20);
		for (int y = 0; y < 20; y++) {
			for (int x = 0; x < 20; x++) {
				boolean insideInner = x >= 1 && y >= 1 && x < 19 && y < 19 && inner[y - 1][x - 1];
				assertEquals(outer[y][x] && !insideInner, drawn[y][x], "pixel " + x + "," + y);
			}
		}
	}

	@Test
	void textIsCutWithAnEllipsis() {
		ToIntFunction<String> width = text -> text.codePointCount(0, text.length()) * 6;
		assertEquals("Hello", Ellipsis.fit("Hello", 30, width));
		assertEquals("Hello...", Ellipsis.fit("Hello World", 48, width));
		assertEquals("Hello...", Ellipsis.fit("Hello World", 54, width), "no space before the dots");
		assertEquals("H...", Ellipsis.fit("Hello", 29, width));
		assertEquals("", Ellipsis.fit("Hello", 17, width), "not even the dots fit");
		assertEquals("", Ellipsis.fit("abc", -5, width));
		String emoji = "a😀bcdef";
		assertEquals("a😀...", Ellipsis.fit(emoji, 30, width));
		// Charged per UTF-16 unit, half the pair would fit; it must not be split anyway.
		assertEquals("a...", Ellipsis.fit(emoji, 30, text -> text.length() * 6), "a surrogate pair is never split");
	}

	/** The game reads "§" with the next character as a formatting code, so a cut never leaves one dangling. */
	@Test
	void aCutNeverEndsOnAFormattingCode() {
		ToIntFunction<String> width = ShapesMathTest::formattedWidth;
		String text = "§cZealot §aBruiser";
		for (int max = 18; max <= 70; max++) {
			String fit = Ellipsis.fit(text, max, width);
			assertFalse(fit.replace("...", "").endsWith("§"), max + ": " + fit);
			String visible = fit.replaceAll("§.", "");
			assertTrue(fit.isEmpty() || fit.equals(text) || visible.endsWith("...") && !visible.endsWith(" ..."), max + ": " + fit);
			assertTrue(width.applyAsInt(fit) <= max, max + ": " + fit);
		}
		assertEquals("§cZealot...", Ellipsis.fit(text, 60, width));
	}

	@Test
	void longTextIsCutWithFewMeasurements() {
		int[] calls = {0};
		String text = "x".repeat(5000);
		String fit = Ellipsis.fit(text, 150, line -> {
			calls[0]++;
			return line.length() * 6;
		});
		assertEquals("x".repeat(22) + "...", fit);
		assertTrue(calls[0] < 40, "bisection, not one measurement per character: " + calls[0]);
	}

	/** Like the game's font: "§" plus the next character take no space, every other character 6. */
	private static int formattedWidth(String text) {
		int width = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '§' && i + 1 < text.length()) {
				i++;
				continue;
			}
			width += 6;
		}
		return width;
	}

	/** REQ-UI-02: a zero or negative clip is empty, never an error. */
	@Test
	void clipRectsNeverGoNegative() {
		assertTrue(ClipRect.of(10, 10, 0, 0).isEmpty());
		ClipRect negative = ClipRect.of(10, 10, -5, 20);
		assertEquals(0, negative.width());
		assertTrue(negative.isEmpty());
		assertTrue(ClipRect.of(10, 10, 20, -1).isEmpty());
		assertFalse(ClipRect.of(0, 0, 320, 240).isEmpty());
		assertEquals(Integer.MAX_VALUE, ClipRect.of(Integer.MAX_VALUE - 1, 0, 10, 10).right());
		assertEquals(330, ClipRect.of(10, 0, 320, 240).right());
	}

	private static int area(List<Corners.Span> spans) {
		return spans.stream().mapToInt(span -> (span.x1() - span.x0()) * (span.y1() - span.y0())).sum();
	}

	private static void assertDisjoint(List<Corners.Span> spans) {
		for (int i = 0; i < spans.size(); i++) {
			for (int j = i + 1; j < spans.size(); j++) {
				Corners.Span a = spans.get(i), b = spans.get(j);
				boolean overlap = a.x0() < b.x1() && b.x0() < a.x1() && a.y0() < b.y1() && b.y0() < a.y1();
				assertFalse(overlap, a + " overlaps " + b);
			}
		}
	}

	private static boolean[][] cover(List<Corners.Span> spans, int w, int h) {
		boolean[][] covered = new boolean[h][w];
		for (Corners.Span span : spans) {
			for (int y = span.y0(); y < span.y1(); y++) {
				for (int x = span.x0(); x < span.x1(); x++) {
					covered[y][x] = true;
				}
			}
		}
		return covered;
	}
}
