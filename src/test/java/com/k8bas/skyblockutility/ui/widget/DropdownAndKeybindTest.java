package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.OptionText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-06, REQ-UI-14, EC-UI-11, R26, R27 (T2.3b): the dropdown and keybind capture, without drawing. */
class DropdownAndKeybindTest {
	// --- Dropdown placement and list model ---

	@Test
	void theListOpensBelowWhenItFitsAndUpwardNearTheBottom() {
		DropdownPlacement.Placement below = DropdownPlacement.place(10, 20, 100, 16, 5, 14, 8, 320, 240, 4);
		assertFalse(below.upward());
		assertEquals(36, below.y(), "right under the box");
		assertEquals(5, below.rows());
		assertEquals(5 * 14, below.height());
		DropdownPlacement.Placement up = DropdownPlacement.place(10, 200, 100, 16, 5, 14, 8, 320, 240, 4);
		assertTrue(up.upward(), "no room below");
		assertEquals(200 - 5 * 14, up.y());
		DropdownPlacement.Placement many = DropdownPlacement.place(10, 20, 100, 16, 20, 14, 8, 320, 240, 4);
		assertEquals(8, many.rows(), "at most 8 rows show");
		DropdownPlacement.Placement squeezed = DropdownPlacement.place(10, 110, 100, 16, 20, 14, 8, 320, 240, 4);
		assertTrue(squeezed.rows() >= 1 && squeezed.y() >= 4 && squeezed.y() + squeezed.height() <= 236, "never off screen: " + squeezed);
		DropdownPlacement.Placement right = DropdownPlacement.place(300, 20, 100, 16, 3, 14, 8, 320, 240, 4);
		assertEquals(320 - 4 - 100, right.x(), "kept inside the right edge");
	}

	@Test
	void theListModelScrollsToAndMovesTheHighlight() {
		DropdownModel model = new DropdownModel(20, 8);
		model.open(11);
		assertEquals(11, model.highlighted());
		assertTrue(model.scroll() <= 11 && model.scroll() + 8 > 11, "the current entry is in view");
		model.move(-100);
		assertEquals(0, model.highlighted());
		assertEquals(0, model.scroll());
		model.move(100);
		assertEquals(19, model.highlighted());
		assertEquals(12, model.scroll());
		assertEquals(12 + 2, model.rowAt(2 * 14 + 3, 14));
		assertEquals(-1, model.rowAt(-1, 14));
		assertEquals(-1, model.rowAt(8 * 14, 14), "below the visible rows");
		model.scrollBy(-3);
		assertEquals(9, model.scroll());
		model.scrollBy(-100);
		assertEquals(0, model.scroll());
	}

	/** The placement may leave fewer rows than the 8 the list opened with; the current entry stays in view. */
	@Test
	void fewerRowsKeepTheCurrentEntryInView() {
		DropdownModel model = new DropdownModel(12, 8);
		model.open(7);
		model.setVisible(3);
		assertTrue(model.scroll() <= 7 && 7 < model.scroll() + 3, "scroll " + model.scroll());
		// Called every frame with the same rows, it must not undo the wheel.
		model.scrollBy(2);
		int scrolled = model.scroll();
		model.setVisible(3);
		assertEquals(scrolled, model.scroll());
	}

	@Test
	void aRowPickApplesTheValueAndClosesTheList() {
		String[] value = {"Stable"};
		Choice<String> choice = Choice.of("updates.channel", "general.updateChannel", new OptionText("Channel", "d", "", List.of()), "Stable",
				List.of("Stable", "Beta", "Alpha"), entry -> entry, Binding.of(() -> value[0], v -> value[0] = v));
		FakeHost host = new FakeHost();
		Dropdown<String> dropdown = new Dropdown<>(choice, host, () -> { });
		dropdown.setBounds(10, 20, 100, 16);
		assertTrue(dropdown.press(20, 25, 0));
		Overlay list = host.overlay;
		assertNotNull(list, "a press opens the list");
		list.layout(320, 240);
		Widget rows = list.widgets().get(0);
		// Rows start right under the box (y 36); the third row is 64..78.
		assertTrue(rows.press(20, 70, 0));
		assertEquals("Alpha", value[0]);
		assertNull(host.overlay, "a pick closes the list");
		assertFalse(dropdown.press(20, 25, 1), "only the left button opens it");
	}

	// --- Keybind conflicts and capture ---

	private static KeyConflicts.KeyState state(String mapping, String bound, String defaultKey) {
		return new KeyConflicts.KeyState(mapping, bound, defaultKey);
	}

	/** EC-UI-11 with the vanilla rule: same key, not both on their defaults, never unbound or itself. */
	@Test
	void conflictsFollowTheVanillaRule() {
		KeyConflicts.KeyState mine = state("open", "key.keyboard.e", Keybind.UNBOUND);
		List<KeyConflicts.KeyState> all = List.of(mine, state("inventory", "key.keyboard.e", "key.keyboard.e"),
				state("other", "key.keyboard.f7", Keybind.UNBOUND));
		assertEquals(List.of("inventory"), KeyConflicts.of(mine, all));
		KeyConflicts.KeyState unbound = state("open", Keybind.UNBOUND, Keybind.UNBOUND);
		assertTrue(KeyConflicts.of(unbound, List.of(unbound, state("x", Keybind.UNBOUND, Keybind.UNBOUND))).isEmpty(), "unbound never clashes");
		KeyConflicts.KeyState onDefault = state("a", "key.keyboard.g", "key.keyboard.g");
		assertTrue(KeyConflicts.of(onDefault, List.of(onDefault, state("b", "key.keyboard.g", "key.keyboard.g"))).isEmpty(),
				"two mappings that both sit on their own defaults are vanilla's business");
		assertEquals(List.of("b"), KeyConflicts.of(onDefault, List.of(onDefault, state("b", "key.keyboard.g", "key.keyboard.h"))));
	}

	/** REQ-UI-14 and R27: the next key binds, Esc unbinds, left click cancels, right click resets, other buttons bind. */
	@Test
	void captureBindsUnbindsCancelsAndResets() {
		FakeTarget target = new FakeTarget("key.keyboard.k", "key.keyboard.j");
		KeyCapture capture = new KeyCapture(target);
		capture.arm();
		assertTrue(capture.armed());
		capture.onKey("key.keyboard.f7", false);
		assertEquals("key.keyboard.f7", target.bound);
		assertFalse(capture.armed());
		capture.arm();
		capture.onKey("key.keyboard.escape", true);
		assertEquals(Keybind.UNBOUND, target.bound, "Esc unbinds");
		capture.arm();
		capture.onMouse(0, "key.mouse.left");
		assertEquals(Keybind.UNBOUND, target.bound, "a left click cancels without binding attack");
		assertFalse(capture.armed());
		capture.arm();
		capture.onMouse(1, "key.mouse.right");
		assertEquals("key.keyboard.j", target.bound, "a right click resets to the default");
		capture.arm();
		capture.onMouse(3, "key.mouse.4");
		assertEquals("key.mouse.4", target.bound, "a side button binds");
		assertEquals(4, target.binds);
	}

	@Test
	void theKeybindButtonArmsWithALeftClickAndResetsWithARightClick() {
		FakeTarget target = new FakeTarget("key.keyboard.k", "key.keyboard.j");
		KeybindButton button = new KeybindButton(target, () -> List.of(), name -> name, () -> { });
		button.setBounds(0, 0, 80, 16);
		assertTrue(button.press(10, 8, 0));
		assertTrue(button.wantsKeyboard(), "armed: it wants the next key");
		assertFalse(button.usesTextInput(), "not a text box: the input method stays off");
		button.captureKey("key.keyboard.f8", false);
		assertEquals("key.keyboard.f8", target.bound);
		assertFalse(button.wantsKeyboard());
		assertTrue(button.press(10, 8, 1));
		assertEquals("key.keyboard.j", target.bound, "right click resets to the default, not to unbound");
		assertFalse(button.wantsKeyboard(), "and does not arm");
		button.press(10, 8, 0);
		button.setFocused(false);
		assertFalse(button.wantsKeyboard(), "losing the focus cancels the capture");
	}

	@Test
	void theKeybindButtonKnowsItsConflicts() {
		FakeTarget target = new FakeTarget("key.keyboard.e", Keybind.UNBOUND);
		List<KeyConflicts.KeyState> all = new ArrayList<>();
		all.add(state("test", "key.keyboard.e", Keybind.UNBOUND));
		all.add(state("inventory", "key.keyboard.e", "key.keyboard.e"));
		KeybindButton button = new KeybindButton(target, () -> all, name -> name, () -> { });
		assertEquals(List.of("inventory"), button.conflicts());
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
		String bound;
		final String defaultKey;
		int binds;

		FakeTarget(String bound, String defaultKey) {
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
			binds++;
		}
	}
}
