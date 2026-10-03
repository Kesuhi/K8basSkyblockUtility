package com.k8bas.skyblockutility.ui.render;

import java.util.function.ToIntFunction;

/** Cutting text to a width with "..." (REQ-UI-02: long labels never overflow their card). */
public final class Ellipsis {
	/** The game's own ellipsis text. */
	public static final String DOTS = "...";
	/** Starts a formatting code; the game reads it together with the next character. */
	private static final char SECTION = '§';

	private Ellipsis() {
	}

	/**
	 * The text if it fits; otherwise its longest start that fits with "..." after it (no trailing
	 * space, never half a character, never a formatting code without its letter); "" if not even the
	 * dots fit. Takes O(log n) width measurements, as it may run every frame.
	 */
	public static String fit(String text, int maxWidth, ToIntFunction<String> width) {
		if (maxWidth <= 0) {
			return "";
		}
		if (width.applyAsInt(text) <= maxWidth) {
			return text;
		}
		if (width.applyAsInt(DOTS) > maxWidth) {
			return "";
		}
		int[] codePoints = text.codePoints().toArray();
		// The candidate's width never decreases as the count grows, so the largest count that fits is found by bisection.
		int low = 0;
		int high = codePoints.length - 1;
		while (low < high) {
			int middle = (low + high + 1) >>> 1;
			if (width.applyAsInt(candidate(codePoints, middle)) <= maxWidth) {
				low = middle;
			} else {
				high = middle - 1;
			}
		}
		return candidate(codePoints, low);
	}

	/** The first {@code count} code points, without trailing spaces or formatting codes (which show nothing), plus the dots. */
	private static String candidate(int[] codePoints, int count) {
		String start = new String(codePoints, 0, count);
		while (true) {
			String trimmed = start.stripTrailing();
			int length = trimmed.length();
			if (length >= 1 && trimmed.charAt(length - 1) == SECTION) {
				trimmed = trimmed.substring(0, length - 1);
			} else if (length >= 2 && trimmed.charAt(length - 2) == SECTION) {
				trimmed = trimmed.substring(0, length - 2);
			}
			if (trimmed.equals(start)) {
				return start + DOTS;
			}
			start = trimmed;
		}
	}
}
