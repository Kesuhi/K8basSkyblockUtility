package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * T2.3b's test screen, for gametests only: dropdowns (short, long and near the bottom), a toggle to
 * prove an outside click is not passed on, and keybind buttons in every state, plus one on the real
 * "Open settings" mapping.
 */
public final class AdvancedWidgetTestScreen extends WidgetScreen {
	private final List<Runnable> layout = new ArrayList<>();
	volatile String shortValue = "Stable";
	volatile String longValue = "Entry 1";
	volatile String bottomValue = "Top";
	volatile boolean toggled;
	final Dropdown<String> longDropdown;
	final Dropdown<String> bottomDropdown;
	final ToggleSwitch toggle;
	final KeybindButton liveKeybind;
	/** Bound to K with J as its default, to tell a reset from an unbind. */
	final KeybindButton resettable;
	private final FixedTarget resettableTarget = new FixedTarget("resettable", "key.keyboard.k", "key.keyboard.j");

	public AdvancedWidgetTestScreen() {
		super(Component.literal("Advanced widget test"));
		place(new Dropdown<>(choice("short", List.of("Stable", "Beta (pre-releases)", "Alpha"), () -> shortValue, v -> shortValue = v), this, () -> { }),
				8, 8, 120, 16);
		List<String> entries = new ArrayList<>();
		for (int i = 1; i <= 12; i++) {
			entries.add("Entry " + i);
		}
		longDropdown = place(new Dropdown<>(choice("long", entries, () -> longValue, v -> longValue = v), this, () -> { }), 140, 8, 120, 16);
		bottomDropdown = add(new Dropdown<>(choice("bottom", List.of("Top", "Middle", "Bottom", "Last"), () -> bottomValue, v -> bottomValue = v),
				this, () -> { }));
		// At the bottom of whatever the GUI size is, so its list has to open upward.
		layout.add(() -> bottomDropdown.setBounds(8, height - 30, 120, 16));
		toggle = place(new ToggleSwitch(Binding.of(() -> toggled, v -> toggled = v), () -> { }, () -> 0L), 280, 60, ToggleSwitch.WIDTH,
				ToggleSwitch.HEIGHT);

		place(keybind(new FixedTarget("normal", "key.keyboard.keypad.7")), 8, 100, 90, 16);
		KeybindButton armed = place(keybind(new FixedTarget("armed", "key.keyboard.keypad.7")), 104, 100, 90, 16);
		armed.forceArmed(true);
		place(keybind(new FixedTarget("conflict", "key.keyboard.e")), 8, 124, 90, 16);
		place(keybind(new FixedTarget("unbound", Keybind.UNBOUND)), 104, 124, 90, 16);
		liveKeybind = place(new KeybindButton(new KeyMappingTarget(SettingsKeybind.OPEN_SETTINGS_KEY), KeyMappingTarget::allKeys,
				KeyMappingTarget::displayName, () -> { }), 8, 160, 120, 16);
		resettable = place(keybind(resettableTarget), 8, 184, 120, 16);
	}

	String resettableBound() {
		return resettableTarget.bound();
	}

	private static Choice<String> choice(String id, List<String> values, java.util.function.Supplier<String> get,
			java.util.function.Consumer<String> set) {
		return Choice.of(id, "test." + id, new OptionText(id, "test", "", List.of()), values.get(0), values, value -> value, Binding.of(get, set));
	}

	private static KeybindButton keybind(KeyTarget target) {
		return new KeybindButton(target, KeyMappingTarget::allKeys, KeyMappingTarget::displayName, () -> { });
	}

	private <W extends Widget> W place(W widget, int x, int y, int w, int h) {
		add(widget);
		layout.add(() -> widget.setBounds(x, y, w, h));
		return widget;
	}

	@Override
	protected void layout() {
		layout.forEach(Runnable::run);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, Theme.PANEL);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		UiText.draw(graphics, font, "normal / armed / conflict / unbound", 200, 104, Theme.TEXT_SECONDARY);
		UiText.draw(graphics, font, "Open settings (live)", 134, 164, Theme.TEXT_SECONDARY);
		UiText.draw(graphics, font, "default J", 134, 188, Theme.TEXT_SECONDARY);
	}

	/** A widget's centre in window pixels. */
	int[] centre(Widget widget) {
		return new int[] {windowX(widget.x + widget.width / 2), windowY(widget.y + widget.height / 2)};
	}

	/** The centre of visible row {@code row} of this dropdown's open list, in window pixels. */
	int[] visibleRow(Dropdown<?> dropdown, int row) {
		if (!dropdown.isOpen()) {
			throw new AssertionError("FAILED: this dropdown's list is not open");
		}
		Widget rows = overlay().widgets().get(0);
		return new int[] {windowX(rows.x + rows.width / 2), windowY(rows.y + row * Dropdown.ROW_HEIGHT + Dropdown.ROW_HEIGHT / 2)};
	}

	boolean listOpen() {
		return overlay() != null;
	}

	private static int windowX(int guiX) {
		var window = Minecraft.getInstance().getWindow();
		return (int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth());
	}

	private static int windowY(int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return (int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight());
	}

	/** A binding that only shows a state. */
	private static final class FixedTarget implements KeyTarget {
		private final String name;
		private String bound;

		private final String defaultKey;

		FixedTarget(String name, String bound) {
			this(name, bound, Keybind.UNBOUND);
		}

		FixedTarget(String name, String bound, String defaultKey) {
			this.name = name;
			this.bound = bound;
			this.defaultKey = defaultKey;
		}

		@Override
		public String mapping() {
			return "test." + name;
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
