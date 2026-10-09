package com.k8bas.skyblockutility.ui.widget;

/**
 * A single-line text field's text, caret and selection (REQ-UI-06, AC-UI-05), kept apart from drawing
 * so it is unit-tested. Words are separated by spaces. Pasted or typed text keeps only its first line,
 * tabs become spaces, and control characters and formatting codes are dropped. The text never exceeds
 * the maximum length, and a character outside the basic plane is never split.
 */
public final class TextEditModel {
	/** The system clipboard in the game, a fake in tests. */
	public interface Clipboard {
		String get();

		void set(String text);
	}

	private static final int KEY_A = 65, KEY_C = 67, KEY_V = 86, KEY_X = 88;
	private static final int KEY_BACKSPACE = 259, KEY_DELETE = 261, KEY_RIGHT = 262, KEY_LEFT = 263, KEY_HOME = 268, KEY_END = 269;

	private final int maxLength;
	private final Clipboard clipboard;
	private String text = "";
	private int caret;
	private int anchor;

	public TextEditModel(int maxLength, Clipboard clipboard) {
		this.maxLength = Math.max(0, maxLength);
		this.clipboard = clipboard;
	}

	public String text() {
		return text;
	}

	public int caret() {
		return caret;
	}

	public int anchor() {
		return anchor;
	}

	public boolean hasSelection() {
		return caret != anchor;
	}

	public int selectionStart() {
		return Math.min(caret, anchor);
	}

	public int selectionEnd() {
		return Math.max(caret, anchor);
	}

	public String selected() {
		return text.substring(selectionStart(), selectionEnd());
	}

	/** Replaces the text (cleaned and held to the limit); the caret goes to its end. */
	public void setText(String newText) {
		text = fitted(sanitize(newText), maxLength);
		caret = text.length();
		anchor = caret;
	}

	/** Puts the caret at {@code index}; with {@code select} the selection stretches to it. */
	public void moveTo(int index, boolean select) {
		caret = clampToBoundary(index);
		if (!select) {
			anchor = caret;
		}
	}

	/** Inserts at the caret, replacing the selection; only what fits goes in. Returns whether anything changed. */
	public boolean insert(String raw) {
		String clean = sanitize(raw);
		int room = maxLength - (text.length() - (selectionEnd() - selectionStart()));
		String piece = fitted(clean, room);
		boolean hadSelection = hasSelection();
		if (piece.isEmpty() && !hadSelection) {
			return false;
		}
		int start = selectionStart();
		text = text.substring(0, start) + piece + text.substring(selectionEnd());
		caret = start + piece.length();
		anchor = caret;
		return true;
	}

	/** A typed character; false if it is not allowed or does not fit. */
	public boolean typed(int codePoint) {
		String typed = Character.toString(codePoint);
		if (sanitize(typed).isEmpty()) {
			return false;
		}
		int room = maxLength - (text.length() - (selectionEnd() - selectionStart()));
		if (typed.length() > room) {
			return false;
		}
		return insert(typed);
	}

	public void backspace(boolean word) {
		if (hasSelection()) {
			deleteSelection();
		} else if (caret > 0) {
			remove(word ? wordLeft(caret) : previous(caret), caret);
		}
	}

	public void delete(boolean word) {
		if (hasSelection()) {
			deleteSelection();
		} else if (caret < text.length()) {
			remove(caret, word ? wordRight(caret) : next(caret));
		}
	}

	public void left(boolean select, boolean word) {
		if (hasSelection() && !select) {
			moveTo(selectionStart(), false);
			return;
		}
		moveTo(word ? wordLeft(caret) : previous(caret), select);
	}

	public void right(boolean select, boolean word) {
		if (hasSelection() && !select) {
			moveTo(selectionEnd(), false);
			return;
		}
		moveTo(word ? wordRight(caret) : next(caret), select);
	}

	public void home(boolean select) {
		moveTo(0, select);
	}

	public void end(boolean select) {
		moveTo(text.length(), select);
	}

	public void selectAll() {
		anchor = 0;
		caret = text.length();
	}

	public void copy() {
		if (hasSelection()) {
			clipboard.set(selected());
		}
	}

	public void cut() {
		if (hasSelection()) {
			clipboard.set(selected());
			deleteSelection();
		}
	}

	public void paste() {
		String pasted = clipboard.get();
		if (pasted != null) {
			insert(pasted);
		}
	}

	/**
	 * A key press with its modifiers (GLFW key codes); true if the key was an editing key. {@code ctrl}
	 * is the shortcut key (Cmd on macOS), never AltGr; Ctrl+A/C/X/V act only without Shift, as in vanilla.
	 */
	public boolean key(int key, boolean ctrl, boolean shift) {
		boolean shortcut = ctrl && !shift;
		switch (key) {
			case KEY_BACKSPACE -> backspace(ctrl);
			case KEY_DELETE -> delete(ctrl);
			case KEY_LEFT -> left(shift, ctrl);
			case KEY_RIGHT -> right(shift, ctrl);
			case KEY_HOME -> home(shift);
			case KEY_END -> end(shift);
			case KEY_A, KEY_C, KEY_X, KEY_V -> {
				if (!shortcut) {
					return false;
				}
				switch (key) {
					case KEY_A -> selectAll();
					case KEY_C -> copy();
					case KEY_X -> cut();
					default -> paste();
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/**
	 * The first line only (cut at the first line break, also U+0085, U+2028 and U+2029), tabs as
	 * spaces, without control characters (C0, DEL, C1) or the formatting sign.
	 */
	public static String sanitize(String raw) {
		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '\n' || c == '\r' || c == '\u0085' || c == ' ' || c == ' ') {
				break;
			}
			if (c == '\t') {
				out.append(' ');
			} else if (c >= 32 && !(c >= 127 && c <= 159) && c != '§') {
				out.append(c);
			}
		}
		return out.toString();
	}

	/** The longest start of {@code s} of at most {@code room} chars that does not end in half a pair. */
	private static String fitted(String s, int room) {
		if (room <= 0) {
			return "";
		}
		if (s.length() <= room) {
			return s;
		}
		int end = room;
		if (Character.isHighSurrogate(s.charAt(end - 1))) {
			end--;
		}
		return s.substring(0, end);
	}

	private void deleteSelection() {
		remove(selectionStart(), selectionEnd());
	}

	private void remove(int from, int to) {
		text = text.substring(0, from) + text.substring(to);
		caret = from;
		anchor = from;
	}

	private int previous(int index) {
		return index <= 0 ? 0 : text.offsetByCodePoints(index, -1);
	}

	private int next(int index) {
		return index >= text.length() ? text.length() : text.offsetByCodePoints(index, 1);
	}

	/** Back over spaces, then over the word before them. */
	private int wordLeft(int index) {
		int i = index;
		while (i > 0 && text.charAt(i - 1) == ' ') {
			i--;
		}
		while (i > 0 && text.charAt(i - 1) != ' ') {
			i--;
		}
		return i;
	}

	/** Forward over the word, then over the spaces after it. */
	private int wordRight(int index) {
		int i = index;
		while (i < text.length() && text.charAt(i) != ' ') {
			i++;
		}
		while (i < text.length() && text.charAt(i) == ' ') {
			i++;
		}
		return i;
	}

	private int clampToBoundary(int index) {
		int clamped = Math.max(0, Math.min(text.length(), index));
		if (clamped > 0 && clamped < text.length() && Character.isLowSurrogate(text.charAt(clamped)) && Character.isHighSurrogate(text.charAt(clamped - 1))) {
			clamped--;
		}
		return clamped;
	}
}
