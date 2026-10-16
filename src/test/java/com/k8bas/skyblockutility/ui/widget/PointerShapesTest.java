package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiTime;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UI-44 [A] (REQ-UI-43, T3.33): the pointer shape each kind of widget asks for, and which widget's shape the screen takes. */
class PointerShapesTest {
	private UiTime previous;

	@BeforeEach
	void motionOff() {
		previous = UiClock.enter(UiTime.of(() -> 0L, () -> false));
	}

	@AfterEach
	void restore() {
		UiClock.leave(previous);
	}

	// --- Each kind ---

	@Test
	void clickableControlsShowTheHand() {
		boolean[] on = {false};
		ToggleSwitch toggle = new ToggleSwitch(Binding.of(() -> on[0], v -> on[0] = v), () -> { }, () -> 0L);
		toggle.setBounds(0, 0, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		assertEquals(PointerShape.HAND, toggle.cursorAt(5, 5), "a toggle");
		Button button = new Button("Save", Button.Style.PRIMARY, () -> { }, () -> { });
		button.setBounds(0, 0, 40, 16);
		assertEquals(PointerShape.HAND, button.cursorAt(20, 8), "a button");
		int[] colour = {0xFF8040};
		ColorSwatch swatch = new ColorSwatch(Binding.of(() -> colour[0], v -> colour[0] = v), false, "Colour", new FakeHost(), () -> { }, () -> { });
		swatch.setBounds(0, 0, 24, 14);
		assertEquals(PointerShape.HAND, swatch.cursorAt(12, 7), "a swatch");
		KeybindButton keybind = new KeybindButton(new FakeTarget(), List::of, name -> name, () -> { });
		keybind.setBounds(0, 0, 80, 16);
		assertEquals(PointerShape.HAND, keybind.cursorAt(40, 8), "a keybind button");
		FakeHost host = new FakeHost();
		Dropdown<String> dropdown = new Dropdown<>(channel(), host, () -> { });
		dropdown.setBounds(10, 20, 100, 16);
		assertEquals(PointerShape.HAND, dropdown.cursorAt(50, 28), "a dropdown");
		// Half scrolled out of its region a press does not open it, so it lets the widget below decide.
		dropdown.setClip(new com.k8bas.skyblockutility.ui.render.ClipRect(0, 0, 200, 30));
		assertTrue(!dropdown.press(50, 25, 0) && host.overlay == null, "half shown, a press does not open it");
		assertNull(dropdown.cursorAt(50, 25), "so it shows no hand");
	}

	@Test
	void aDropdownsEntriesShowTheHandAndItsDraggedThumbTheVerticalResize() {
		FakeHost host = new FakeHost();
		Dropdown<String> dropdown = new Dropdown<>(channel(), host, () -> { });
		dropdown.setBounds(10, 20, 100, 16);
		dropdown.press(50, 28, 0);
		assertNotNull(host.overlay);
		host.overlay.layout(320, 240);
		Widget rows = host.overlay.widgets().get(0);
		// The list starts under the box with 4 GUI px of padding; its first entry is 40..54.
		assertEquals(PointerShape.HAND, rows.cursorAt(30, 46), "an entry");
		assertEquals(PointerShape.DEFAULT, rows.cursorAt(30, rows.y() + 1), "the padding above the first entry");
		assertNull(rows.dragCursor(), "no drag");

		Dropdown<String> many = new Dropdown<>(Choice.of("t.many", "t.many", new OptionText("Many", "d", "", List.of()), "0",
				java.util.stream.IntStream.range(0, 30).mapToObj(Integer::toString).toList(), entry -> entry, Binding.of(() -> "0", v -> { })), host, () -> { });
		many.setBounds(10, 20, 100, 16);
		many.press(50, 28, 0);
		host.overlay.layout(320, 240);
		Widget list = host.overlay.widgets().get(0);
		int thumbX = list.x() + list.width() - 3;
		int thumbY = list.y() + 6;
		assertEquals(PointerShape.HAND, list.cursorAt(thumbX, thumbY), "its scrollbar");
		assertTrue(list.press(thumbX, thumbY, 0));
		assertEquals(PointerShape.RESIZE_VERTICAL, list.dragCursor(), "its thumb while dragged");
		list.release(thumbX, thumbY);
		assertNull(list.dragCursor(), "released");
	}

	@Test
	void aTextFieldShowsTheIBeam() {
		TextField field = new TextField(new TextEditModel(20, GameClipboardFake.INSTANCE), "", text -> { }, () -> 0L);
		field.setBounds(0, 0, 140, 16);
		assertEquals(PointerShape.TEXT, field.cursorAt(70, 8));
	}

	@Test
	void aSliderShowsTheHandAndWhileDraggedTheHorizontalResize() {
		long[] value = {0};
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> value[0] = v, () -> { }, Long::toString, () -> { });
		slider.setBounds(0, 0, 110, 12);
		assertEquals(PointerShape.HAND, slider.cursorAt(50, 6), "hovered, as the game's own sliders");
		assertNull(slider.dragCursor(), "not dragged");
		assertTrue(slider.press(50, 6, 0));
		assertEquals(PointerShape.RESIZE_HORIZONTAL, slider.dragCursor(), "dragged");
		slider.release(50, 6);
		assertNull(slider.dragCursor(), "released");
		float[] set = {0F};
		GradientSlider gradient = new GradientSlider(() -> set[0], v -> set[0] = v, step -> step, false);
		gradient.setBounds(0, 0, 100, 10);
		assertEquals(PointerShape.HAND, gradient.cursorAt(50, 5), "the colour picker's slider");
		assertTrue(gradient.press(50, 5, 0));
		assertEquals(PointerShape.RESIZE_HORIZONTAL, gradient.dragCursor(), "dragged");
		gradient.release(50, 5);
		assertNull(gradient.dragCursor());
	}

	@Test
	void aScrollbarShowsTheHandAndItsDraggedThumbTheVerticalResize() {
		ScrollArea area = new ScrollArea();
		area.setBounds(0, 0, 300, 200);
		area.setContentHeight(2000);
		assertNull(area.cursorAt(100, 100), "the region itself lets the pointer through, as it does presses");
		assertEquals(PointerShape.HAND, area.cursorAt(298, 2), "its scrollbar");
		assertTrue(area.press(298, 2, 0));
		assertEquals(PointerShape.RESIZE_VERTICAL, area.dragCursor(), "its thumb while dragged");
		area.release(298, 2);
		assertNull(area.dragCursor());
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, index -> { });
		list.setBounds(0, 0, 160, 200);
		assertEquals(PointerShape.HAND, list.cursorAt(50, 5), "a list's row");
		assertEquals(PointerShape.HAND, list.cursorAt(158, 2), "its scrollbar");
		assertTrue(list.press(158, 2, 0));
		assertEquals(PointerShape.RESIZE_VERTICAL, list.dragCursor(), "its thumb while dragged");
		list.release(158, 2);
		assertTrue(list.press(50, 2, 0));
		assertNull(list.dragCursor(), "a press on a row is no drag");
	}

	@Test
	void aListsRowsCanAskForTheirOwnShape() {
		VirtualList list = new VirtualList(20, () -> 3, (graphics, font, index, x, y, w, h, hovered) -> { }, index -> { });
		list.setBounds(10, 10, 100, 200);
		int[] asked = new int[5];
		list.setRowCursor((index, x, y, width, height) -> {
			asked[0] = index;
			asked[1] = (int) x;
			asked[2] = (int) y;
			asked[3] = width;
			asked[4] = height;
			return index == 1 ? PointerShape.HAND : PointerShape.DEFAULT;
		});
		assertEquals(PointerShape.HAND, list.cursorAt(40, 35), "the second row");
		assertEquals(1, asked[0]);
		assertEquals(30, asked[1], "x from the row's left");
		assertEquals(5, asked[2], "y from the row's top");
		assertEquals(100, asked[3]);
		assertEquals(20, asked[4]);
		assertEquals(PointerShape.DEFAULT, list.cursorAt(40, 15), "the first row");
		assertEquals(PointerShape.DEFAULT, list.cursorAt(40, 150), "below the rows");
	}

	@Test
	void theColourPickersControls() {
		int[] colour = {0xFF8040};
		ColorPickerOverlay picker = new ColorPickerOverlay("Colour", Binding.of(() -> colour[0], v -> colour[0] = v), true, new FakeHost(), () -> { },
				() -> { });
		picker.layout(640, 480);
		int checked = 0;
		for (Widget widget : picker.widgets()) {
			if (widget.width() <= 0 || widget.height() <= 0) {
				continue;
			}
			PointerShape expected = widget instanceof TextField ? PointerShape.TEXT : PointerShape.HAND;
			assertEquals(expected, widget.cursorAt(widget.x() + widget.width() / 2.0, widget.y() + widget.height() / 2.0),
					widget.getClass().getSimpleName());
			checked++;
		}
		assertTrue(checked >= 6, "wheel, sliders, presets, hex field and buttons: " + checked);
	}

	// --- Which widget decides ---

	@Test
	void theTopmostWidgetUnderThePointerDecides() {
		Probe below = probe(0, 0, 100, 100, PointerShape.TEXT);
		Probe above = probe(20, 20, 20, 20, PointerShape.HAND);
		List<Widget> widgets = List.of(below, above);
		assertEquals(PointerShape.HAND, PointerPick.pick(widgets, null, 30, 30), "the one on top");
		assertEquals(PointerShape.TEXT, PointerPick.pick(widgets, null, 5, 5), "only the one below there");
		assertEquals(PointerShape.DEFAULT, PointerPick.pick(widgets, null, 150, 150), "empty space");
		assertEquals(PointerShape.DEFAULT, PointerPick.pick(List.of(), null, 5, 5), "no widgets");
	}

	@Test
	void aDisabledControlShowsTheDefault() {
		Probe below = probe(0, 0, 100, 100, PointerShape.TEXT);
		Probe above = probe(20, 20, 20, 20, PointerShape.HAND);
		above.setEnabled(false);
		assertEquals(PointerShape.DEFAULT, PointerPick.pick(List.of(below, above), null, 30, 30));
		above.setDimmed(true);
		above.setEnabled(true);
		assertEquals(PointerShape.HAND, PointerPick.pick(List.of(below, above), null, 30, 30), "a dimmed one still works, so it keeps its shape");
	}

	@Test
	void aWidgetWithoutAShapeHereLetsTheOneBelowDecide() {
		Probe below = probe(0, 0, 100, 100, PointerShape.TEXT);
		Probe above = probe(20, 20, 20, 20, null);
		assertEquals(PointerShape.TEXT, PointerPick.pick(List.of(below, above), null, 30, 30));
	}

	@Test
	void outsideItsClipAWidgetIsNotThere() {
		Probe below = probe(0, 0, 100, 100, PointerShape.TEXT);
		Probe above = probe(20, 20, 20, 20, PointerShape.HAND);
		above.setClip(new com.k8bas.skyblockutility.ui.render.ClipRect(0, 0, 100, 25));
		assertEquals(PointerShape.HAND, PointerPick.pick(List.of(below, above), null, 30, 22), "inside the clip");
		assertEquals(PointerShape.TEXT, PointerPick.pick(List.of(below, above), null, 30, 30), "scrolled out of view there");
	}

	@Test
	void aHeldDragKeepsItsShapeAnywhere() {
		Probe dragged = probe(0, 0, 10, 10, PointerShape.HAND);
		dragged.drag = PointerShape.RESIZE_HORIZONTAL;
		Probe other = probe(50, 50, 20, 20, PointerShape.TEXT);
		assertEquals(PointerShape.RESIZE_HORIZONTAL, PointerPick.pick(List.of(dragged, other), dragged, 60, 60), "over another widget");
		assertEquals(PointerShape.RESIZE_HORIZONTAL, PointerPick.pick(List.of(dragged, other), dragged, 200, 200), "over nothing");
		dragged.drag = null;
		assertEquals(PointerShape.TEXT, PointerPick.pick(List.of(dragged, other), dragged, 60, 60), "a press that is no drag: what is under it");
	}

	// --- Helpers ---

	private static Probe probe(int x, int y, int width, int height, PointerShape shape) {
		Probe probe = new Probe(shape);
		probe.setBounds(x, y, width, height);
		return probe;
	}

	private static final class Probe extends Widget {
		private final PointerShape shape;
		PointerShape drag;

		Probe(PointerShape shape) {
			this.shape = shape;
		}

		@Override
		public PointerShape cursorAt(double mouseX, double mouseY) {
			return shape;
		}

		@Override
		public PointerShape dragCursor() {
			return drag;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		}
	}

	private static Choice<String> channel() {
		String[] value = {"Stable"};
		return Choice.of("updates.channel", "general.updateChannel", new OptionText("Channel", "d", "", List.of()), "Stable",
				List.of("Stable", "Beta", "Alpha"), entry -> entry, Binding.of(() -> value[0], v -> value[0] = v));
	}

	private enum GameClipboardFake implements TextEditModel.Clipboard {
		INSTANCE;

		@Override
		public String get() {
			return "";
		}

		@Override
		public void set(String text) {
		}
	}

	private static final class FakeHost implements OverlayHost {
		Overlay overlay;

		@Override
		public void open(Overlay opened) {
			overlay = opened;
		}

		@Override
		public void close(Overlay closed) {
			if (overlay == closed) {
				overlay = null;
			}
		}
	}

	private static final class FakeTarget implements KeyTarget {
		@Override
		public String mapping() {
			return "test";
		}

		@Override
		public String bound() {
			return "key.keyboard.k";
		}

		@Override
		public String defaultKey() {
			return "key.keyboard.j";
		}

		@Override
		public void bind(String keyName) {
		}
	}
}
