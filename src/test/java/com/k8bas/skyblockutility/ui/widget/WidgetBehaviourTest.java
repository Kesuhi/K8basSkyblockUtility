package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.render.IconShapes;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiTime;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-03, REQ-UI-06, REQ-UI-15 (T2.3a): how the widgets react to the mouse, without drawing. */
class WidgetBehaviourTest {
	/** AC-UI-40 (REQ-UI-39, T3.24): hover eases on the UI clock source, and with its motion off it is at its end at once. */
	@Test
	void hoverFollowsTheUiClockSource() {
		long[] now = {0};
		boolean[] motion = {true};
		Widget widget = new Widget() {
			@Override
			public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			}
		};
		UiTime previous = UiClock.enter(UiTime.of(() -> now[0], () -> motion[0]));
		try {
			assertEquals(0F, widget.hoverAmount(false));
			now[0] += 1;
			assertTrue(widget.hoverAmount(true) < 0.1F, "motion on: it starts easing");
			now[0] += 50;
			float halfway = widget.hoverAmount(true);
			assertTrue(halfway > 0F && halfway < 1F, "on the source's clock: " + halfway);
			motion[0] = false;
			assertEquals(1F, widget.hoverAmount(true), "motion off: at its end at once");
			assertEquals(0F, widget.hoverAmount(false), "and back at once");
		} finally {
			UiClock.leave(previous);
		}
	}

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

	/** AC-UI-47 [A] (REQ-UI-46, T3.36): a press on the knob keeps the pointer where it grabbed the knob; the knob does not jump. */
	@Test
	void aPressOnTheKnobKeepsTheGrabOffset() {
		Slider slider = slider();
		value[0] = 50;
		// The knob's centre is at 5 + 50 = 55: pressed 3 GUI px left of it.
		assertTrue(slider.press(52, 6, 0));
		assertEquals(50, value[0], "no jump on the press");
		slider.drag(72, 6);
		assertEquals(70, value[0], "20 GUI px further: the knob's centre moved 20 GUI px, to 75, and the value is that place's");
		assertEquals(75, slider.knobX() + Slider.KNOB_SIZE / 2, "the knob's centre");
		slider.release(72, 6);
	}

	/** AC-UI-47 [A], AC-UI-14: a press on the knob of a value off the step grid leaves it as it is and writes nothing. */
	@Test
	void aPressOnTheKnobChangesNothingByItself() {
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 5), () -> value[0], v -> value[0] = v, () -> commits[0]++, Long::toString,
				() -> sounds[0]++);
		slider.setBounds(0, 0, 110, 12);
		// 37 (a fine step, or a value edited in the file): the knob's centre at 5 + 37 = 42.
		value[0] = 37;
		assertTrue(slider.press(42, 6, 0));
		assertEquals(37, value[0], "not snapped to the step by the press");
		slider.release(42, 6);
		assertEquals(37, value[0]);
		assertEquals(0, commits[0], "nothing written");
	}

	/** AC-UI-47 [A]: on a grown knob, a press on its outer ring is a press on the knob too. */
	@Test
	void aPressOnTheGrownKnobsRimKeepsTheGrabOffset() {
		Slider slider = slider();
		slider.forceHot(true);
		value[0] = 50;
		// The knob's centre is at 55 and it is drawn 12 GUI px across: 5.5 GUI px right of the centre is on it.
		assertTrue(slider.press(60.5, 6, 0));
		assertEquals(50, value[0], "no jump");
		slider.release(60.5, 6);
	}

	/** AC-UI-47 [A]: a press on the track away from the knob still moves the knob there (REQ-UI-03). */
	@Test
	void aPressOnTheTrackAwayFromTheKnobJumpsThere() {
		Slider slider = slider();
		value[0] = 50;
		assertTrue(slider.press(85, 6, 0), "30 GUI px right of the knob's centre");
		assertEquals(80, value[0], "the knob's centre is under the pointer: 85");
		slider.drag(90, 6);
		assertEquals(85, value[0], "and follows it from there");
		slider.release(90, 6);
	}

	/** AC-UI-47 [A]: the knob's drawn size eases from 10 to 12 GUI px on hover and while dragged, and back after it. */
	@Test
	void theKnobGrowsOnHoverAndWhileDragged() {
		long[] now = {0};
		UiTime previous = UiClock.enter(UiTime.of(() -> now[0], () -> true));
		try {
			Slider slider = slider();
			value[0] = 50;
			assertEquals(10F, slider.knobSize(-50, -50), 1e-6, "idle");
			assertEquals(10F, slider.knobSize(55, 6), 1e-6, "the pointer just arrived");
			now[0] = 75;
			float growing = slider.knobSize(55, 6);
			assertTrue(growing > 10F && growing < 12F, "75 ms on: " + growing);
			now[0] = 150;
			assertEquals(12F, slider.knobSize(55, 6), 1e-6, "12 GUI px at 150 ms");
			assertTrue(slider.press(55, 6, 0));
			now[0] = 400;
			assertEquals(12F, slider.knobSize(300, 300), 1e-6, "dragged with the pointer far away: still grown");
			slider.release(300, 300);
			now[0] = 401;
			assertTrue(slider.knobSize(300, 300) <= 12F);
			now[0] = 551;
			assertEquals(10F, slider.knobSize(300, 300), 1e-6, "150 ms after the release and away: back to 10");
		} finally {
			UiClock.leave(previous);
		}
	}

	/** AC-UI-47 [A]: grown to 12 GUI px, the knob changes neither the slider's layout nor where a press reaches it. */
	@Test
	void theGrownKnobChangesNeitherLayoutNorHits() {
		Slider slider = slider();
		slider.forceHot(true);
		assertEquals(12F, slider.knobSize(-50, -50), 1e-6, "grown");
		value[0] = 0;
		assertEquals(0, slider.knobX(), "its layout at the track's start, as at 10 GUI px");
		// The grown knob is drawn 1 GUI px past each end of the track; a press there reaches no slider.
		assertFalse(slider.press(-1, 6, 0), "1 GUI px left of the layout");
		value[0] = 100;
		assertEquals(100, slider.knobX(), "at the track's end, as at 10 GUI px");
		assertFalse(slider.press(110, 6, 0), "1 GUI px right of it");
		assertFalse(slider.press(50, -1, 0), "1 GUI px above it");
		assertFalse(slider.press(50, 12, 0), "1 GUI px below it");
	}

	/** AC-UI-47 [A]: the value label is in the accent colour while dragged, and its normal colour after the release. */
	@Test
	void theValueLabelIsAccentWhileDragged() {
		com.k8bas.skyblockutility.ui.render.Theme theme = com.k8bas.skyblockutility.ui.render.Theme.of(new com.k8bas.skyblockutility.config.GeneralConfig());
		Slider slider = slider();
		assertEquals(com.k8bas.skyblockutility.ui.render.Theme.TEXT_PRIMARY, slider.labelColor(theme), "idle");
		assertTrue(slider.press(50, 6, 0));
		assertEquals(theme.accent(), slider.labelColor(theme), "dragged");
		slider.release(50, 6);
		assertEquals(com.k8bas.skyblockutility.ui.render.Theme.TEXT_PRIMARY, slider.labelColor(theme), "released");
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

	/**
	 * REQ-UI-37 (T3.28, your change of 2026-10-15): the wheel never changes a slider and is never taken by it, so it goes on to
	 * the page; idle, after a press and release on the knob and on the track, during a drag, with or without Shift. Only a drag
	 * blocks the wheel, so a held slider does not scroll the page either.
	 */
	@Test
	void theWheelNeverChangesASlider() {
		Slider slider = slider();
		value[0] = 10;
		for (int notches : new int[] {3, -3}) {
			for (boolean fine : new boolean[] {false, true}) {
				assertFalse(slider.scroll(50, 6, notches, fine), "idle: not taken (" + notches + ", fine " + fine + ")");
			}
		}
		assertEquals(10, value[0]);
		assertFalse(slider.blocksWheel(), "idle, it does not block the wheel");
		int knob = slider.knobX() + Slider.KNOB_SIZE / 2;
		assertTrue(slider.press(knob, 6, 0), "a press on the knob");
		slider.release(knob, 6);
		assertFalse(slider.scroll(knob, 6, 3, false), "after a press on the knob: not taken");
		assertTrue(slider.press(90, 6, 0), "a press on the track");
		assertTrue(slider.blocksWheel(), "while dragged, the wheel goes nowhere");
		long dragged = value[0];
		assertFalse(slider.scroll(90, 6, 3, false), "during the drag: not taken");
		assertFalse(slider.scroll(90, 6, -3, true));
		assertEquals(dragged, value[0], "and the value follows only the pointer");
		slider.release(90, 6);
		assertFalse(slider.blocksWheel());
		assertFalse(slider.scroll(90, 6, 3, false), "after a press on the track: not taken");
		assertEquals(dragged, value[0]);
		assertEquals(1, commits[0], "one commit, for the press on the track");
	}

	/** EC-UI-05: an out-of-range value is only replaced when the shown value changes. */
	@Test
	void anOutOfRangeValueSurvivesUntilItReallyChanges() {
		Slider slider = slider();
		value[0] = 200;
		assertTrue(slider.navKey(NavKey.RIGHT, false));
		assertEquals(200, value[0], "up from the shown maximum changes nothing");
		assertTrue(slider.press(105, 6, 0), "on the knob, at the maximum");
		slider.release(105, 6);
		assertEquals(200, value[0], "a click on the knob writes nothing");
		assertEquals(0, commits[0]);
		assertTrue(slider.navKey(NavKey.LEFT, false));
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

	/** T3.21 (REQ-UI-32): an icon at the left end (the search glass) moves the text and its click positions right by its room. */
	@Test
	void aLeftIconKeepsItsRoomFreeOfText() {
		TextField field = field("abcdefghijklmnop");
		field.leftIcon(IconShapes.Icon.SEARCH);
		field.setBounds(0, 0, 40 + 2 * TextField.PADDING + TextField.LEFT_ICON, 16);
		field.keepCaretVisible();
		assertEquals(10, field.scroll(), "the same 6 characters fit beside the icon");
		int scroll = field.scroll();
		int textLeft = TextField.PADDING + TextField.LEFT_ICON;
		assertEquals(scroll + 2, field.indexAt(textLeft + 12), "two characters into the view, after the icon");
		assertEquals(scroll, field.indexAt(TextField.PADDING), "over the icon: the view's start");
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
