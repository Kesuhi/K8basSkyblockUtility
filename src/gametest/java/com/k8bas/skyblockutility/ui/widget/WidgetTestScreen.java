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
	/** AC-UI-38 (T3.28): a region behind the live slider that scrolls both ways; the slider moves with it. */
	private final ScrollArea page = new ScrollArea();
	/** Where the region starts, so it can scroll both ways and the slider is at its usual place. */
	static final int PAGE_START = 75;
	private static final com.k8bas.skyblockutility.ui.render.ClipRect PAGE_BOX = new com.k8bas.skyblockutility.ui.render.ClipRect(4, 40, 128, 26);
	private boolean pageStarted;
	/** The live slider's top in the region's content: by default where it shows at its usual place at {@link #PAGE_START}. */
	private int sliderTop = PAGE_START + 8;
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
		add(page);
		layout.add(() -> {
			page.setBounds(PAGE_BOX.x(), PAGE_BOX.y(), PAGE_BOX.width(), PAGE_BOX.height());
			// 3 notches (72 GUI px) fit both ways from the start.
			page.setContentHeight(PAGE_BOX.height() + 150);
			if (!pageStarted) {
				pageStarted = true;
				page.setScroll(PAGE_START);
			}
		});
		liveSlider = add(new Slider(decimalModel, () -> decimalValue, v -> decimalValue = v, () -> decimalCommits++, decimalModel::format, () -> { }));
		liveSlider.setClip(PAGE_BOX);
		layout.add(() -> liveSlider.setBounds(8, PAGE_BOX.y() + sliderTop - page.scroll(), 80, 12));
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
		// T3.36: a dimmed knob grown as on hover, so its translucent disc is checked against the fill and track around it.
		long[] dimmedHot = {64};
		Slider dimmedHotSlider = place(new Slider(range, () -> dimmedHot[0], v -> dimmedHot[0] = v, () -> { }, range::format, () -> { }), 140, 214,
				80, 12);
		dimmedHotSlider.setDimmed(true);
		dimmedHotSlider.forceHot(true);
		knobSliders.add(dimmedHotSlider);

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

	/** Per AC-UI-28 slider, as {@link KnobProbe#of} reads it: its geometry, and its knob as drawn (T3.25, T3.36). */
	int[][] knobSliders() {
		int[][] out = new int[knobSliders.size()][];
		for (int i = 0; i < out.length; i++) {
			Slider s = knobSliders.get(i);
			// The knob as drawn (grown when hot, T3.36): its left, top and size in screen pixels.
			out[i] = KnobProbe.of(s);
		}
		return out;
	}

	boolean sliderDragging() {
		return liveSlider.isDragging();
	}

	int pageScroll() {
		return page.scroll();
	}

	int pageMax() {
		return page.maxScroll();
	}

	void setPageScroll(int scroll) {
		page.setScroll(scroll);
	}

	/** A point over the region's empty space, right of the slider's value and left of its widened scrollbar, in window pixels. */
	int[] pageEmpty() {
		return new int[] {windowX(120), windowY(page.y + 15)};
	}

	/** Places the live slider in the region's content, so it shows at the region's top or bottom too; negative for its usual place. */
	void setSliderTop(int top) {
		sliderTop = top < 0 ? PAGE_START + 8 : top;
	}

	/** Whether the live slider lies whole inside the region now (it is clipped to it). */
	boolean sliderInRegion() {
		return liveSlider.y >= page.y && liveSlider.y + liveSlider.height <= page.y + page.height;
	}

	/** The live slider's knob centre, in window pixels. */
	int[] liveKnob() {
		return new int[] {windowX(liveSlider.knobX() + Slider.KNOB_SIZE / 2), windowY(liveSlider.y + liveSlider.height / 2)};
	}

	/** Sets the live slider's value, in its units (1.00 is 100). */
	void setDecimal(long units) {
		decimalValue = units;
	}
}
