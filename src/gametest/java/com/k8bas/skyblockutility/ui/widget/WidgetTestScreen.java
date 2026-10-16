package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * T2.3a's test screen, for gametests only: every widget in every state, in a grid that fits a
 * 320×240 GUI, plus a live decimal slider and a live text field the tests drive with real input.
 */
public final class WidgetTestScreen extends WidgetScreen {
	private static final TextEditModel.Clipboard GAME_CLIPBOARD = new TextEditModel.Clipboard() {
		@Override
		public String get() {
			return Minecraft.getInstance().keyboardHandler.getClipboard();
		}

		@Override
		public void set(String text) {
			Minecraft.getInstance().keyboardHandler.setClipboard(text);
		}
	};

	private final List<Runnable> layout = new ArrayList<>();
	private final SliderModel decimalModel = SliderModel.ofDecimal(0.5, 3.0, 0.1, 2);
	volatile long decimalValue = decimalModel.units(1.0);
	volatile int decimalCommits;
	private final TextEditModel probeModel = new TextEditModel(64, GAME_CLIPBOARD);
	private final TextField probeField;
	private final Slider liveSlider;
	private Slider middleSlider;
	/** The sliders AC-UI-28 looks at (T3.16): 0, 64 and 128, the hovered one and the dimmed one. */
	private final List<Slider> knobSliders = new ArrayList<>();
	/** The dimmed slider's card shows its hovered colour (T3.16). */
	volatile boolean dimmedCardHover;

	public WidgetTestScreen() {
		super(Component.literal("Widget test"));
		boolean[] on = {true};
		boolean[] off = {false};
		place(new ToggleSwitch(flag(on), () -> { }, () -> 0L), 8, 8, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		place(new ToggleSwitch(flag(off), () -> { }, () -> 0L), 40, 8, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		place(new ToggleSwitch(flag(new boolean[] {true}), () -> { }, () -> 0L), 72, 8, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT).setEnabled(false);
		place(new ToggleSwitch(flag(new boolean[] {true}), () -> { }, () -> 0L), 104, 8, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT).setDimmed(true);

		SliderModel range = SliderModel.ofInt(0, 128, 1);
		long[] low = {0}, middle = {64}, high = {128};
		for (long[] value : List.of(low, middle, high)) {
			int column = value == low ? 0 : value == middle ? 1 : 2;
			Slider slider = place(new Slider(range, () -> value[0], v -> value[0] = v, () -> { }, range::format, () -> { }), 8 + column * 102, 28, 50, 12);
			if (value == middle) {
				middleSlider = slider;
			}
			knobSliders.add(slider);
		}
		liveSlider = place(new Slider(decimalModel, () -> decimalValue, v -> decimalValue = v, () -> decimalCommits++, decimalModel::format, () -> { }),
				8, 48, 80, 12);
		long[] disabled = {32};
		place(new Slider(range, () -> disabled[0], v -> disabled[0] = v, () -> { }, range::format, () -> { }), 140, 48, 60, 12).setEnabled(false);
		long[] hot = {96};
		Slider hotSlider = place(new Slider(range, () -> hot[0], v -> hot[0] = v, () -> { }, range::format, () -> { }), 232, 48, 50, 12);
		hotSlider.forceHot(true);
		knobSliders.add(hotSlider);
		long[] dimmed = {48};
		Slider dimmedSlider = place(new Slider(range, () -> dimmed[0], v -> dimmed[0] = v, () -> { }, range::format, () -> { }), 8, 214, 80, 12);
		dimmedSlider.setDimmed(true);
		knobSliders.add(dimmedSlider);

		int row = 0;
		for (Button.Style style : Button.Style.values()) {
			int y = 68 + row++ * 20;
			String label = style == Button.Style.NORMAL ? "Cancel" : style == Button.Style.PRIMARY ? "Save" : "Delete";
			place(new Button(label, style, () -> { }, () -> { }), 8, y, 46, 16);
			place(new Button(label, style, () -> { }, () -> { }), 58, y, 46, 16).setLook(Button.Look.HOVER);
			place(new Button(label, style, () -> { }, () -> { }), 108, y, 46, 16).setLook(Button.Look.PRESSED);
			place(new Button(label, style, () -> { }, () -> { }), 158, y, 46, 16).setEnabled(false);
			place(new Button(label, style, () -> { }, () -> { }), 208, y, 46, 16).setDimmed(true);
		}

		place(new TextField(new TextEditModel(64, GAME_CLIPBOARD), "Search settings...", text -> { }, () -> 0L), 8, 132, 140, 16);
		TextField caret = place(new TextField(model("Zealot"), "", text -> { }, () -> 0L), 160, 132, 140, 16);
		caret.setFocused(true);
		TextField selection = place(new TextField(model("Special Zealot"), "", text -> { }, () -> 0L), 8, 152, 140, 16);
		selection.model().moveTo(8, true);
		selection.setFocused(true);
		place(new TextField(model("a very long pattern that does not fit into the field at all"), "", text -> { }, () -> 0L), 160, 152, 140, 16)
				.setFocused(true);
		probeField = place(TextField.of(probeModel, "Type here", text -> { }), 8, 174, 140, 16);
		place(new TextField(model("Disabled"), "", text -> { }, () -> 0L), 160, 174, 140, 16).setEnabled(false);
		place(new TextField(model("Dimmed"), "", text -> { }, () -> 0L), 8, 194, 140, 16).setDimmed(true);
		place(new TextField(model("Hovered"), "", text -> { }, () -> 0L), 160, 194, 140, 16).forceHot(true);
	}

	private static Binding<Boolean> flag(boolean[] holder) {
		return Binding.of(() -> holder[0], value -> holder[0] = value);
	}

	private static TextEditModel model(String text) {
		TextEditModel model = new TextEditModel(64, GAME_CLIPBOARD);
		model.setText(text);
		return model;
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
		// The dimmed slider sits on a card, idle or hovered, as on a settings page (T3.16).
		Shapes.roundedRect(graphics, 4, 210, 120, 20, Shapes.RADIUS_CARD, dimmedCardHover ? Theme.CARD_HOVER : Theme.CARD);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		UiText.draw(graphics, font, "on / off / disabled / dimmed", 136, 10, Theme.TEXT_SECONDARY);
	}

	/** The centres of the live decimal slider and of the probe field, in window pixels, from their own bounds. */
	int[] liveCentres() {
		return new int[] {windowX(liveSlider.x + liveSlider.width / 2), windowY(liveSlider.y + liveSlider.height / 2),
				windowX(probeField.x + probeField.width / 2), windowY(probeField.y + probeField.height / 2)};
	}

	/** A point on the live slider's track, 0 (left end) to 1 (right end), in window pixels. */
	int[] sliderPoint(double fraction) {
		int gx = liveSlider.x + Slider.KNOB_SIZE / 2 + (int) Math.round(fraction * (liveSlider.width - Slider.KNOB_SIZE));
		return new int[] {windowX(gx), windowY(liveSlider.y + liveSlider.height / 2)};
	}

	private static int windowX(int guiX) {
		var window = Minecraft.getInstance().getWindow();
		return (int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth());
	}

	private static int windowY(int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return (int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight());
	}

	String probeText() {
		return probeModel.text();
	}

	String probeSelection() {
		return probeModel.selected();
	}

	boolean probeFocused() {
		return keyboardFocus() == probeField;
	}

	/** The knob of the 0-128 slider at 64, in GUI pixels: its left and top edge (T3.15). */
	int[] middleKnob() {
		int knobX = middleSlider.x + (int) Math.round(0.5 * (middleSlider.width - Slider.KNOB_SIZE));
		return new int[] {knobX, middleSlider.y + (middleSlider.height - Slider.KNOB_SIZE) / 2};
	}

	/**
	 * Per AC-UI-28 slider: its x, track top, width, knob left (laid out) and top edge, 1 if dimmed (GUI pixels), and the knob's
	 * left edge as drawn, on the physical-pixel grid (REQ-UI-34), in physical pixels.
	 */
	int[][] knobSliders() {
		int scale = Shapes.guiScale();
		int[][] out = new int[knobSliders.size()][];
		for (int i = 0; i < out.length; i++) {
			Slider s = knobSliders.get(i);
			out[i] = new int[] {s.x, s.y + (s.height - Slider.TRACK_HEIGHT) / 2, s.width, s.knobX(), s.y + (s.height - Slider.KNOB_SIZE) / 2,
					s.isDimmed() ? 1 : 0, Math.round(s.drawnKnobX(scale) * scale)};
		}
		return out;
	}

	boolean sliderDragging() {
		return liveSlider.isDragging();
	}
}
