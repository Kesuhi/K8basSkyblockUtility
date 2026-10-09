package com.k8bas.skyblockutility.ui.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Where a tooltip goes and how its text wraps (REQ-UI-19): beside the cursor, flipped to its left at
 * the right edge, and always entirely inside the window. Text wraps at spaces, "\n" breaks a line, and
 * a word longer than a line is cut. Formatting codes carry on to the next line, as they would in one
 * string. Text taller than the window is cut, its last line ending in "...".
 */
public final class TooltipLayout {
	/** The text width a tooltip wraps at, if the window has room for it. */
	public static final int PREFERRED_TEXT_WIDTH = 200;
	public static final int PADDING = 4;
	/** Kept free between the box and the window's edge. */
	public static final int MARGIN = 4;
	public static final int LINE_HEIGHT = 10;
	/** From the cursor to the box. */
	public static final int OFFSET = 12;
	private static final char SECTION = '§';

	private TooltipLayout() {
	}

	/** A placed tooltip, in GUI pixels; the lines are already wrapped. */
	public record Box(List<String> lines, int x, int y, int width, int height) {
	}

	public static Box layout(String text, int mouseX, int mouseY, int screenWidth, int screenHeight, ToIntFunction<String> width) {
		int maxText = Math.max(1, Math.min(PREFERRED_TEXT_WIDTH, screenWidth - 2 * MARGIN - 2 * PADDING));
		List<String> lines = wrap(text, maxText, width);
		int maxLines = Math.max(1, (screenHeight - 2 * MARGIN - 2 * PADDING + 1) / LINE_HEIGHT);
		if (lines.size() > maxLines) {
			lines = new ArrayList<>(lines.subList(0, maxLines));
			lines.set(maxLines - 1, withDots(lines.get(maxLines - 1), maxText, width));
		}
		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, width.applyAsInt(line));
		}
		int boxWidth = textWidth + 2 * PADDING;
		int boxHeight = lines.size() * LINE_HEIGHT - 1 + 2 * PADDING;
		int x = mouseX + OFFSET;
		if (x + boxWidth > screenWidth - MARGIN) {
			x = mouseX - OFFSET - boxWidth;
		}
		x = clamp(x, MARGIN, screenWidth - MARGIN - boxWidth);
		int y = clamp(mouseY - OFFSET, MARGIN, screenHeight - MARGIN - boxHeight);
		return new Box(List.copyOf(lines), x, y, boxWidth, boxHeight);
	}

	/** Lines no wider than {@code maxWidth}; an empty text gives one empty line. */
	public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
		List<String> lines = new ArrayList<>();
		String carried = "";
		for (String paragraph : text.split("\n", -1)) {
			StringBuilder line = new StringBuilder(carried);
			boolean empty = true;
			for (String word : paragraph.split(" ")) {
				if (word.isEmpty()) {
					continue;
				}
				String joined = empty ? line + word : line + " " + word;
				if (width.applyAsInt(joined) <= maxWidth) {
					line.setLength(0);
					line.append(joined);
					empty = false;
					continue;
				}
				if (!empty) {
					lines.add(line.toString());
					carried = activeFormat(carried, line.toString());
					line.setLength(0);
					line.append(carried);
				}
				// The word alone may still be too long: cut it, one line at a time.
				String rest = word;
				while (width.applyAsInt(line + rest) > maxWidth) {
					int cut = longestFit(line.toString(), rest, maxWidth, width);
					String piece = line + rest.substring(0, cut);
					lines.add(piece);
					carried = activeFormat(carried, piece);
					rest = rest.substring(cut);
					line.setLength(0);
					line.append(carried);
				}
				line.append(rest);
				empty = false;
			}
			lines.add(line.toString());
			carried = activeFormat(carried, line.toString());
		}
		return lines;
	}

	/** How many chars of {@code word} fit after {@code start}: at least one character, never a code without its letter. */
	private static int longestFit(String start, String word, int maxWidth, ToIntFunction<String> width) {
		int best = 0;
		int i = 0;
		while (i < word.length()) {
			int next = word.charAt(i) == SECTION && i + 1 < word.length() ? i + 2 : word.offsetByCodePoints(i, 1);
			if (best > 0 && width.applyAsInt(start + word.substring(0, next)) > maxWidth) {
				break;
			}
			best = next;
			i = next;
		}
		return best;
	}

	/**
	 * The formatting codes in force after {@code line}, started with {@code before}: the last colour
	 * and the styles after it; a colour drops the styles before it, and {@code §r} drops everything.
	 */
	static String activeFormat(String before, String line) {
		String colour = "";
		StringBuilder styles = new StringBuilder();
		String all = before + line;
		for (int i = 0; i + 1 < all.length(); i++) {
			if (all.charAt(i) != SECTION) {
				continue;
			}
			char code = Character.toLowerCase(all.charAt(i + 1));
			if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') {
				colour = "" + SECTION + code;
				styles.setLength(0);
			} else if (code >= 'k' && code <= 'o') {
				String style = "" + SECTION + code;
				if (styles.indexOf(style) < 0) {
					styles.append(style);
				}
			} else if (code == 'r') {
				colour = "";
				styles.setLength(0);
			}
			i++;
		}
		return colour + styles;
	}

	/** The line shortened until "..." fits after it (no trailing space, no code without its letter). */
	private static String withDots(String line, int maxWidth, ToIntFunction<String> width) {
		String start = trimEnd(line);
		while (!start.isEmpty() && width.applyAsInt(start + Ellipsis.DOTS) > maxWidth) {
			start = trimEnd(start.substring(0, start.offsetByCodePoints(start.length(), -1)));
		}
		return start + Ellipsis.DOTS;
	}

	/** Without trailing spaces and formatting codes, which show nothing (a lone § would eat a dot). */
	private static String trimEnd(String text) {
		String trimmed = text;
		while (true) {
			String next = trimmed.stripTrailing();
			int length = next.length();
			if (length >= 1 && next.charAt(length - 1) == SECTION) {
				next = next.substring(0, length - 1);
			} else if (length >= 2 && next.charAt(length - 2) == SECTION) {
				next = next.substring(0, length - 2);
			}
			if (next.equals(trimmed)) {
				return trimmed;
			}
			trimmed = next;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, Math.max(min, max)));
	}
}
