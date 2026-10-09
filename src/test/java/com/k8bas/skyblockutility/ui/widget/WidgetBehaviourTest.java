package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-03, REQ-UI-06, REQ-UI-15 (T2.3a): how the widgets react to the mouse, without drawing. */
class WidgetBehaviourTest {
	private final long[] value = {0};
	private final int[] commits = {0};
	private final int[] sounds = {0};

	private Slider slider() {
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> value[0] = v, () -> commits[0]++, Long::toString,
				() -> sounds[0]++);
		// Track from x 0 to 110: the knob centre runs from 5 to 105, one value per pixel.
		slider.setBounds(0, 0, 110, 12);
		return slider;
	}

	/** REQ-UI-15: a drag across 50 values writes at most once, on release. */
	@Test
	void aDragCommitsOnceOnRelease() {
		Slider slider = slider();
		assertTrue(slider.press(5, 6, 0));
		for (int x = 6; x <= 55; x++) {
			slider.drag(x, 6);
		}
		assertEquals(50, value[0], "the value follows the drag live");
		assertEquals(0, commits[0], "no commit while dragging");
		slider.release(55, 6);
		assertEquals(1, commits[0]);
		assertEquals(1, sounds[0], "a click sound on release");
		assertTrue(slider.press(55, 6, 0));
		slider.release(55, 6);
		assertEquals(1, commits[0], "a click that changes nothing commits nothing");
	}

	/** REQ-UI-03: only a press on the track counts, and only with the left button. */
	@Test
	void onlyAPressOnTheTrackStartsADrag() {
		Slider slider = slider();
		assertFalse(slider.press(120, 6, 0), "beside the track");
		assertFalse(slider.press(50, 20, 0), "below it");
		assertFalse(slider.press(50, 6, 1), "right button");
		slider.drag(80, 6);
		assertEquals(0, value[0]);
		slider.setEnabled(false);
		assertFalse(slider.press(50, 6, 0), "disabled");
	}

	@Test
	void theWheelStepsLiveWithoutACommit() {
		Slider slider = slider();
		value[0] = 10;
		assertTrue(slider.scroll(50, 6, 3, false));
		assertEquals(13, value[0]);
		assertTrue(slider.scroll(50, 6, -1, true));
		assertEquals(12, value[0]);
		assertEquals(0, commits[0]);
		assertFalse(slider.scroll(200, 6, 1, false), "not over the slider");
	}

	/** EC-UI-05: an out-of-range value is only replaced when the shown value changes. */
	@Test
	void anOutOfRangeValueSurvivesUntilItReallyChanges() {
		Slider slider = slider();
		value[0] = 200;
		assertTrue(slider.scroll(50, 6, 1, false));
		assertEquals(200, value[0], "up from the shown maximum changes nothing");
		assertTrue(slider.press(105, 6, 0), "on the knob, at the maximum");
		slider.release(105, 6);
		assertEquals(200, value[0], "a click on the knob writes nothing");
		assertEquals(0, commits[0]);
		assertTrue(slider.scroll(50, 6, -1, false));
		assertEquals(99, value[0], "a real change replaces it");
	}

	private static TextField field(String text) {
		TextEditModel model = new TextEditModel(200, new TextEditModel.Clipboard() {
			@Override
			public String get() {
				return "";
			}

			@Override
			public void set(String text) {
			}
		});
		model.setText(text);
		TextField field = new TextField(model, "", changed -> { }, () -> 0L);
		// 6 px per character; 40 px inside the padding: 6 characters fit (one pixel kept for the caret).
		field.setBounds(0, 0, 40 + 2 * TextField.PADDING, 16);
		field.useWidths(line -> line.codePointCount(0, line.length()) * 6);
		return field;
	}

	@Test
	void theFieldScrollsToTheCaretAndStaysFilled() {
		TextField field = field("abcdefghijklmnop");
		field.keepCaretVisible();
		assertEquals(10, field.scroll(), "the caret at the end is in view: the last 6 characters show");
		field.model().key(259, false, false);
		field.model().key(259, false, false);
		field.keepCaretVisible();
		assertEquals(8, field.scroll(), "deleting from the end scrolls back so the field stays filled");
		field.model().key(268, false, false);
		field.keepCaretVisible();
		assertEquals(0, field.scroll(), "Home shows the start");
	}

	@Test
	void aClickPutsTheCaretAtTheNearestBoundaryOfTheScrolledText() {
		TextField field = field("abcdefghijklmnop");
		field.keepCaretVisible();
		int scroll = field.scroll();
		assertEquals(scroll + 2, field.indexAt(TextField.PADDING + 12), "two characters into the view");
		assertEquals(scroll + 2, field.indexAt(TextField.PADDING + 14), "nearest boundary");
		assertEquals(scroll, field.indexAt(0));
	}

	/** An edit and a click in the same frame, before a draw: the old scroll must not reach past the text. */
	@Test
	void aShorterTextBeforeTheNextDrawIsSafe() {
		TextField field = field("abcdefghijklmnopqrstuvwxyz");
		field.keepCaretVisible();
		assertTrue(field.scroll() > 5);
		field.model().selectAll();
		field.model().key(259, false, false);
		assertTrue(field.press(10, 8, 0), "no exception");
		field.drag(20, 8);
		assertEquals(0, field.model().caret());
	}

	@Test
	void aDragPastTheLeftEdgeSelectsFurtherLeft() {
		TextField field = field("abcdefghijklmnop");
		field.keepCaretVisible();
		assertTrue(field.press(30, 8, 0));
		int before = field.model().caret();
		field.drag(-5, 8);
		field.keepCaretVisible();
		assertTrue(field.model().caret() < field.scroll() + 1 && field.model().caret() < before);
		assertTrue(field.model().hasSelection());
	}

	@Test
	void aToggleFlipsItsBindingWithAClick() {
		boolean[] on = {false};
		int[] clicks = {0};
		ToggleSwitch toggle = new ToggleSwitch(Binding.of(() -> on[0], v -> on[0] = v), () -> clicks[0]++, () -> 0L);
		toggle.setBounds(0, 0, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		assertTrue(toggle.press(5, 5, 0));
		assertTrue(on[0]);
		assertEquals(1, clicks[0]);
		assertFalse(toggle.press(50, 5, 0));
		toggle.setEnabled(false);
		assertFalse(toggle.press(5, 5, 0));
		assertTrue(on[0], "a disabled toggle ignores clicks");
	}

	@Test
	void aButtonActsOnReleaseInsideItself() {
		int[] actions = {0};
		Button button = new Button("Save", Button.Style.PRIMARY, () -> actions[0]++, () -> { });
		button.setBounds(0, 0, 60, 18);
		assertTrue(button.press(10, 5, 0));
		assertEquals(0, actions[0], "nothing on press");
		button.release(10, 5);
		assertEquals(1, actions[0]);
		button.press(10, 5, 0);
		button.release(100, 5);
		assertEquals(1, actions[0], "released outside: cancelled");
		button.release(10, 5);
		assertEquals(1, actions[0], "a release without a press does nothing");
	}
}
