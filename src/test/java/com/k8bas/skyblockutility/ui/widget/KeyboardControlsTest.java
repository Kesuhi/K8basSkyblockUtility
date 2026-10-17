package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.OptionText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-18 (T2.9b): what Space/Enter and the arrows do to each focused control, and which controls are Tab stops. */
class KeyboardControlsTest {
	/**
	 * AC-UI-06 as amended (R46, your change of 2026-10-15): a 0.1-step slider at 1.00 takes no wheel, and with keyboard focus 3
	 * presses of the right arrow raise it to 1.30, Shift giving the fine step to 1.31.
	 */
	@Test
	void aDecimalSliderStepsByKeyAndNotByWheel() {
		long[] value = {100};
		Slider slider = new Slider(SliderModel.ofDecimal(0.5, 3.0, 0.1, 2), () -> value[0], v -> value[0] = v, () -> { }, Long::toString, () -> { });
		slider.setBounds(0, 0, 110, 12);
		assertFalse(slider.scroll(50, 6, 3, false), "the wheel is not taken");
		assertEquals(100, value[0]);
		for (int i = 0; i < 3; i++) {
			assertTrue(slider.navKey(NavKey.RIGHT, false));
		}
		assertEquals(130, value[0], "3 right arrows: 1.30");
		assertTrue(slider.navKey(NavKey.RIGHT, true));
		assertEquals(131, value[0], "Shift: the fine step, 1.31");
	}

	/**
	 * REQ-UI-40 (T3.30): the colour picker's slider has the slider's round knob, whose centre runs inside the track: a press at
	 * the knob's half width from either end is 0 or 1, and the middle is a half.
	 */
	@Test
	void aGradientSlidersKnobRunsInsideItsTrack() {
		float[] set = {0.5F};
		GradientSlider gradient = new GradientSlider(() -> set[0], v -> set[0] = v, step -> step, false);
		gradient.setBounds(0, 0, 110, 10);
		int half = Slider.KNOB_SIZE / 2;
		gradient.press(half, 5, 0);
		assertEquals(0F, set[0], 1e-6, "the left end");
		gradient.drag(110 - half, 5);
		assertEquals(1F, set[0], 1e-6, "the right end");
		gradient.drag(55, 5);
		assertEquals(0.5F, set[0], 1e-6, "the middle");
		gradient.release(55, 5);
		assertEquals(55F, gradient.knobCentre(), 1e-4, "its knob is drawn there");
	}

	/** REQ-UI-40: a region in a rounded panel's corner keeps its scrollbar's track short of the corner. */
	@Test
	void aRegionsTrackCanStopShortOfItsBottom() {
		ScrollArea area = new ScrollArea();
		area.setBounds(0, 0, 300, 200);
		area.setContentHeight(2000);
		area.setTrackInset(4);
		area.setScroll(area.maxScroll());
		var thumb = area.thumb();
		assertEquals(196, thumb.offset() + thumb.length(), "at the bottom the thumb ends 4 GUI px up");
		assertTrue(area.press(298, 199, 0), "a press below the track still takes the scrollbar");
		area.release(298, 199);
	}

	/** REQ-UI-37, EC-UI-25: while dragged, the colour picker's slider and a region's thumb block the wheel, and not otherwise. */
	@Test
	void dragsBlockTheWheel() {
		float[] set = {0F};
		GradientSlider gradient = new GradientSlider(() -> set[0], v -> set[0] = v, step -> step, false);
		gradient.setBounds(0, 0, 100, 10);
		assertFalse(gradient.blocksWheel());
		assertTrue(gradient.press(50, 5, 0));
		assertTrue(gradient.blocksWheel(), "the gradient slider while dragged");
		gradient.release(50, 5);
		assertFalse(gradient.blocksWheel());
		ScrollArea area = new ScrollArea();
		area.setBounds(0, 0, 300, 200);
		area.setContentHeight(2000);
		assertTrue(area.press(298, 2, 0), "the thumb");
		assertTrue(area.blocksWheel(), "a region while its thumb is dragged");
		area.release(298, 2);
		assertFalse(area.blocksWheel());
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, index -> { });
		list.setBounds(0, 0, 160, 200);
		assertTrue(list.press(158, 2, 0));
		assertTrue(list.blocksWheel(), "a list while its thumb is dragged");
		list.release(158, 2);
		assertFalse(list.blocksWheel());
		assertTrue(list.press(50, 2, 0), "a row");
		assertFalse(list.blocksWheel(), "a press on a row does not");
	}
	private int clicks;

	// --- Toggle ---

	@Test
	void activateFlipsAToggleWithItsSound() {
		boolean[] on = {false};
		ToggleSwitch toggle = new ToggleSwitch(Binding.of(() -> on[0], v -> on[0] = v), () -> clicks++, () -> 0L);
		assertTrue(toggle.focusable());
		assertTrue(toggle.activate());
		assertTrue(on[0]);
		assertEquals(1, clicks);
	}

	@Test
	void leftAndRightSetAToggleOffAndOnWithoutASoundWhenAlreadySo() {
		boolean[] on = {false};
		ToggleSwitch toggle = new ToggleSwitch(Binding.of(() -> on[0], v -> on[0] = v), () -> clicks++, () -> 0L);
		assertTrue(toggle.navKey(NavKey.LEFT, false));
		assertFalse(on[0]);
		assertEquals(0, clicks, "already off");
		assertTrue(toggle.navKey(NavKey.RIGHT, false));
		assertTrue(on[0]);
		assertEquals(1, clicks);
		assertFalse(toggle.navKey(NavKey.DOWN, false), "not a toggle key");
	}

	@Test
	void aDisabledToggleIgnoresKeys() {
		boolean[] on = {false};
		ToggleSwitch toggle = new ToggleSwitch(Binding.of(() -> on[0], v -> on[0] = v), () -> clicks++, () -> 0L);
		toggle.setEnabled(false);
		assertFalse(toggle.activate());
		assertFalse(toggle.navKey(NavKey.RIGHT, false));
		assertFalse(on[0]);
	}

	// --- Slider ---

	@Test
	void arrowsStepASliderLiveWithoutACommit() {
		long[] value = {10};
		int[] commits = {0};
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> value[0] = v, () -> commits[0]++, Long::toString,
				() -> clicks++);
		assertTrue(slider.focusable());
		assertFalse(slider.activate(), "Space and Enter do nothing on a slider");
		assertTrue(slider.navKey(NavKey.RIGHT, false));
		assertEquals(11, value[0]);
		assertTrue(slider.navKey(NavKey.LEFT, false));
		assertEquals(10, value[0]);
		assertTrue(slider.navKey(NavKey.UP, false));
		assertEquals(11, value[0]);
		assertTrue(slider.navKey(NavKey.DOWN, false));
		assertEquals(10, value[0]);
		assertEquals(0, commits[0], "like the wheel: written when the screen closes");
		assertEquals(0, clicks);
	}

	@Test
	void homeEndAndPageKeysJump() {
		long[] value = {50};
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> value[0] = v, () -> { }, Long::toString, () -> { });
		slider.navKey(NavKey.PAGE_UP, false);
		assertEquals(60, value[0]);
		slider.navKey(NavKey.PAGE_DOWN, false);
		assertEquals(50, value[0]);
		slider.navKey(NavKey.HOME, false);
		assertEquals(0, value[0]);
		slider.navKey(NavKey.END, false);
		assertEquals(100, value[0]);
	}

	/** EC-UI-05, as for the wheel: a stored value off the range is kept until a key really changes it. */
	@Test
	void anOutOfRangeValueIsKeptUntilAStepChangesIt() {
		long[] value = {200};
		int[] writes = {0};
		Slider slider = new Slider(SliderModel.ofInt(0, 128, 1), () -> value[0], v -> {
			value[0] = v;
			writes[0]++;
		}, () -> { }, Long::toString, () -> { });
		slider.navKey(NavKey.RIGHT, false);
		assertEquals(200, value[0], "at the top already: shown 128, a step up stays 128");
		assertEquals(0, writes[0]);
		slider.navKey(NavKey.LEFT, false);
		assertEquals(127, value[0]);
	}

	@Test
	void aDisabledSliderIgnoresKeys() {
		long[] value = {10};
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> value[0] = v, () -> { }, Long::toString, () -> { });
		slider.setEnabled(false);
		assertFalse(slider.navKey(NavKey.RIGHT, false));
		assertEquals(10, value[0]);
	}

	// --- Button ---

	@Test
	void activateRunsAButtonOnceWithItsSound() {
		int[] actions = {0};
		Button button = new Button("Save", Button.Style.PRIMARY, () -> actions[0]++, () -> clicks++);
		assertTrue(button.focusable());
		assertTrue(button.activate());
		assertEquals(1, actions[0]);
		assertEquals(1, clicks);
		button.setEnabled(false);
		assertFalse(button.activate());
		assertEquals(1, actions[0]);
	}

	// --- Dropdown ---

	private Choice<String> channel(String[] value) {
		return Choice.of("updates.channel", "general.updateChannel", new OptionText("Channel", "d", "", List.of()), "Stable",
				List.of("Stable", "Beta", "Alpha"), entry -> entry, Binding.of(() -> value[0], v -> value[0] = v));
	}

	@Test
	void activateOpensADropdownsListThroughTheHost() {
		String[] value = {"Stable"};
		RecordingHost host = new RecordingHost();
		Dropdown<String> dropdown = new Dropdown<>(channel(value), host, () -> clicks++);
		assertTrue(dropdown.focusable());
		assertTrue(dropdown.activate());
		assertNotNull(host.opened);
		assertTrue(dropdown.isOpen());
		assertEquals(1, clicks);
	}

	@Test
	void arrowsPickTheNeighbourAtOnceAndStopAtTheEnds() {
		String[] value = {"Stable"};
		Dropdown<String> dropdown = new Dropdown<>(channel(value), new RecordingHost(), () -> clicks++);
		assertTrue(dropdown.navKey(NavKey.DOWN, false));
		assertEquals("Beta", value[0]);
		dropdown.navKey(NavKey.END, false);
		assertEquals("Alpha", value[0]);
		int before = clicks;
		assertTrue(dropdown.navKey(NavKey.DOWN, false));
		assertEquals("Alpha", value[0]);
		assertEquals(before, clicks, "no sound at the end");
		dropdown.navKey(NavKey.HOME, false);
		assertEquals("Stable", value[0]);
		assertFalse(dropdown.navKey(NavKey.ACTIVATE, false), "Enter is activate(), not a list step");
	}

	// --- Keybind ---

	@Test
	void activateArmsAKeybindAndDeleteResetsIt() {
		Target target = new Target("key.keyboard.k", "key.keyboard.j");
		KeybindButton button = new KeybindButton(target, List::of, name -> name, () -> clicks++);
		assertTrue(button.focusable());
		assertTrue(button.activate());
		assertTrue(button.wantsKeyboard(), "armed: it wants the next key");
		button.captureKey("key.keyboard.f7", false);
		assertEquals("key.keyboard.f7", target.bound);
		assertTrue(button.navKey(NavKey.DELETE, false));
		assertEquals("key.keyboard.j", target.bound, "Delete resets to the default, as a right click does");
	}

	// --- Text field ---

	@Test
	void aTextFieldIsATabStopButDoesNotActivateItself() {
		TextField field = TextField.of(new TextEditModel(20, new TextEditModel.Clipboard() {
			@Override
			public String get() {
				return "";
			}

			@Override
			public void set(String text) {
			}
		}), "Search", text -> { });
		assertTrue(field.focusable());
		assertFalse(field.activate());
	}

	// --- Defaults ---

	@Test
	void plainWidgetsAreNotTabStops() {
		Widget plain = new Widget() {
			@Override
			public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			}
		};
		assertFalse(plain.focusable());
		assertFalse(plain.activate());
		assertFalse(plain.navKey(NavKey.RIGHT, false));
		assertFalse(new ScrollArea().focusable());
	}

	@Test
	void theFocusRingIsTwoPixelsOutside() {
		Button button = new Button("Save", Button.Style.PRIMARY, () -> { }, () -> { });
		button.setBounds(10, 20, 30, 12);
		int[] ring = new int[4];
		button.focusRing(ring);
		assertArrayEquals(new int[] {8, 18, 34, 16}, ring);
	}

	// --- Sidebar list ---

	@Test
	void aListIsATabStopOnlyWithKeysAndRows() {
		int[] count = {0};
		VirtualList list = new VirtualList(24, () -> count[0], (graphics, font, index, x, y, width, height, hovered) -> { }, row -> { });
		assertFalse(list.focusable(), "no keys");
		list.setKeys(key -> true);
		assertFalse(list.focusable(), "no rows");
		count[0] = 3;
		list.setBounds(0, 0, 100, 200);
		assertTrue(list.focusable());
		assertTrue(list.navKey(NavKey.DOWN, false));
	}

	@Test
	void aListsRingIsItsFocusRowInsideIt() {
		VirtualList list = new VirtualList(24, () -> 3, (graphics, font, index, x, y, width, height, hovered) -> { }, row -> { });
		list.setKeys(key -> true);
		list.setFocusRow(() -> 1);
		list.setBounds(10, 100, 120, 200);
		int[] ring = new int[4];
		list.focusRing(ring);
		assertArrayEquals(new int[] {11, 125, 118, 22}, ring, "the second row, 1 px inside the list");
	}

	private static final class RecordingHost implements OverlayHost {
		Overlay opened;

		@Override
		public void open(Overlay overlay) {
			opened = overlay;
		}

		@Override
		public void close(Overlay overlay) {
			if (opened == overlay) {
				opened = null;
			}
		}
	}

	private static final class Target implements KeyTarget {
		String bound;
		final String defaultKey;

		Target(String bound, String defaultKey) {
			this.bound = bound;
			this.defaultKey = defaultKey;
		}

		@Override
		public String mapping() {
			return "test";
		}

		@Override
		public String bound() {
			return bound;
		}

		@Override
		public String defaultKey() {
			return defaultKey;
		}

		@Override
		public void bind(String keyName) {
			bound = keyName;
		}
	}
}
