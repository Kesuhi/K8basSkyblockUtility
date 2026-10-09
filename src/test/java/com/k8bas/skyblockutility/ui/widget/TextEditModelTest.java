package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UI-05 [A] (T2.3a): the text field's edit model. */
class TextEditModelTest {
	private static final int BACKSPACE = 259, DELETE = 261, RIGHT = 262, LEFT = 263, HOME = 268, END = 269;
	private static final int A = 65, C = 67, V = 86, X = 88;

	private final String[] clipboard = {""};
	private final TextEditModel.Clipboard fakeClipboard = new TextEditModel.Clipboard() {
		@Override
		public String get() {
			return clipboard[0];
		}

		@Override
		public void set(String text) {
			clipboard[0] = text;
		}
	};

	private TextEditModel model(int maxLength, String text) {
		TextEditModel model = new TextEditModel(maxLength, fakeClipboard);
		model.setText(text);
		return model;
	}

	@Test
	void insertAndDeleteWithAndWithoutCtrl() {
		TextEditModel model = model(100, "");
		model.insert("abc");
		assertEquals("abc", model.text());
		assertEquals(3, model.caret());
		model.setText("hello world");
		model.key(BACKSPACE, false, false);
		assertEquals("hello worl", model.text());
		model.key(BACKSPACE, true, false);
		assertEquals("hello ", model.text(), "Ctrl+Backspace deletes the word before the caret");
		model.setText("hello world");
		model.key(HOME, false, false);
		model.key(DELETE, false, false);
		assertEquals("ello world", model.text());
		model.key(DELETE, true, false);
		assertEquals("world", model.text(), "Ctrl+Delete deletes to the start of the next word");
	}

	@Test
	void aSelectionIsReplacedOrDeletedFirst() {
		TextEditModel model = model(100, "hello world");
		model.key(LEFT, false, true);
		model.key(LEFT, false, true);
		assertTrue(model.hasSelection());
		assertEquals("ld", model.selected());
		model.key(BACKSPACE, false, false);
		assertEquals("hello wor", model.text());
		model.key(LEFT, false, true);
		model.insert("X");
		assertEquals("hello woX", model.text());
	}

	@Test
	void selectAllCopyCutPaste() {
		TextEditModel model = model(100, "hello");
		assertTrue(model.key(A, true, false));
		assertEquals("hello", model.selected());
		model.key(C, true, false);
		assertEquals("hello", clipboard[0]);
		model.key(X, true, false);
		assertEquals("", model.text());
		assertEquals("hello", clipboard[0]);
		model.key(V, true, false);
		model.key(V, true, false);
		assertEquals("hellohello", model.text());
		clipboard[0] = "kept";
		model.key(LEFT, false, false);
		model.key(C, true, false);
		assertEquals("kept", clipboard[0], "copy with no selection leaves the clipboard alone");
	}

	@Test
	void shiftArrowsHomeAndEndSelect() {
		TextEditModel model = model(100, "abcdef");
		model.key(HOME, false, true);
		assertEquals("abcdef", model.selected(), "Shift+Home from the end");
		model.key(END, false, false);
		assertFalse(model.hasSelection());
		assertEquals(6, model.caret());
		model.key(HOME, false, false);
		model.key(END, false, true);
		assertEquals("abcdef", model.selected());
		model.key(LEFT, false, false);
		assertFalse(model.hasSelection());
		assertEquals(0, model.caret(), "Left collapses a selection to its start");
	}

	@Test
	void ctrlArrowsJumpWords() {
		TextEditModel model = model(100, "foo  bar baz");
		model.key(HOME, false, false);
		model.key(RIGHT, true, false);
		assertEquals(5, model.caret(), "past foo and its spaces");
		model.key(RIGHT, true, false);
		assertEquals(9, model.caret());
		model.key(RIGHT, true, false);
		assertEquals(12, model.caret());
		model.key(LEFT, true, false);
		assertEquals(9, model.caret());
		model.key(LEFT, true, false);
		assertEquals(5, model.caret());
		model.key(LEFT, true, false);
		assertEquals(0, model.caret());
		model.key(RIGHT, true, true);
		assertEquals("foo  ", model.selected(), "Ctrl+Shift selects word-wise");
	}

	/** AC-UI-05: paste keeps only the first line, with tabs turned into spaces. */
	@Test
	void pasteKeepsTheFirstLineWithTabsAsSpaces() {
		TextEditModel model = model(100, "");
		clipboard[0] = "a\tb\nsecond";
		model.paste();
		assertEquals("a b", model.text());
		model.setText("");
		clipboard[0] = "\r\nx";
		model.paste();
		assertEquals("", model.text());
		assertEquals("ab", TextEditModel.sanitize("a§b"), "no formatting codes");
		assertEquals("ab", TextEditModel.sanitize("a\u0007b"), "no control characters");
	}

	/** AC-UI-05: the maximum length (EC-UI-10). */
	@Test
	void theMaximumLengthHolds() {
		TextEditModel model = model(5, "");
		model.insert("abcdefg");
		assertEquals("abcde", model.text());
		assertFalse(model.typed('x'), "a typed character past the limit is ignored");
		assertEquals("abcde", model.text());
		model.key(LEFT, false, true);
		model.key(LEFT, false, true);
		clipboard[0] = "XYZ";
		model.paste();
		assertEquals("abcXY", model.text(), "a paste fills the room the selection frees");
		TextEditModel emoji = model(3, "ab");
		emoji.insert("😀");
		assertEquals("ab", emoji.text(), "a surrogate pair is never split at the limit");
		model.setText("abcdefghij");
		assertEquals("abcde", model.text(), "setText is held to the limit too");
	}

	/** Vanilla's rule: Ctrl+Shift+A/C/X/V are no shortcuts (AltGr is filtered out before the model, in WidgetScreen). */
	@Test
	void shortcutsNeedCtrlWithoutShift() {
		TextEditModel model = model(100, "hello");
		assertFalse(model.key(A, true, true));
		assertFalse(model.hasSelection());
		assertFalse(model.key(A, false, false));
		clipboard[0] = "x";
		assertFalse(model.key(V, true, true));
		assertEquals("hello", model.text());
	}

	@Test
	void insertReportsADeletedSelection() {
		TextEditModel model = model(100, "hello");
		model.selectAll();
		assertTrue(model.insert("\n"), "the selection went, though nothing was inserted");
		assertEquals("", model.text());
		assertFalse(model.insert(""), "nothing at all changed");
	}

	@Test
	void unicodeLineBreaksAndC1ControlsCount() {
		assertEquals("a", TextEditModel.sanitize("a b"));
		assertEquals("a", TextEditModel.sanitize("a\u0085b"));
		assertEquals("ab", TextEditModel.sanitize("a\u009Bb"));
		assertEquals("aé", TextEditModel.sanitize("aé"), "Latin-1 letters stay");
	}

	@Test
	void typingRejectsWhatCannotBeShown() {
		TextEditModel model = model(100, "");
		assertTrue(model.typed('a'));
		assertFalse(model.typed('§'));
		assertFalse(model.typed('\n'));
		assertEquals("a", model.text());
		assertTrue(model.typed(0x1F600), "a code point beyond the BMP");
		assertEquals("a😀", model.text());
		model.key(BACKSPACE, false, false);
		assertEquals("a", model.text(), "Backspace removes the whole pair");
	}
}
