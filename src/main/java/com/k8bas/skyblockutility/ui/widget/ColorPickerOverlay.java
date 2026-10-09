package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.color.ColorPickerState;
import com.k8bas.skyblockutility.ui.color.ColorPresets;
import com.k8bas.skyblockutility.ui.color.Hsv;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The colour picker modal (REQ-UI-13): a hue/saturation wheel, brightness, alpha only for colours that
 * store it, a hex field (an invalid value is marked and not applied), the 16 chat colours as presets,
 * and Cancel/Save. Nothing is applied before Save; Cancel, Esc and closing the screen discard.
 */
public final class ColorPickerOverlay extends Overlay {
	private static final int WIDTH = 224;
	private static final int WHEEL = 80;
	private static final int SWATCH = 12;

	private final ColorPickerState state;
	private final Binding<Integer> binding;
	private final String title;
	private final OverlayHost host;
	private final Runnable commit;
	private final ColorWheel wheel;
	private final GradientSlider brightness;
	private final GradientSlider alpha;
	private final TextField hex;
	private final List<PresetSwatch> presets = new ArrayList<>();
	private final Button cancel;
	private final Button save;
	private final List<Widget> widgets = new ArrayList<>();
	private int panelX;
	private int panelY;
	private int panelHeight;

	/**
	 * @param binding     the stored colour: 0xAARRGGBB if it stores alpha, else 0xRRGGBB
	 * @param commit      runs once after Save, to write the file (REQ-UI-15)
	 * @param clickSound  for the buttons and presets
	 */
	public ColorPickerOverlay(String title, Binding<Integer> binding, boolean storesAlpha, OverlayHost host, Runnable commit, Runnable clickSound) {
		this.title = title;
		this.binding = binding;
		this.host = host;
		this.commit = commit;
		this.state = new ColorPickerState(binding.get(), storesAlpha);
		this.wheel = add(new ColorWheel(state));
		this.brightness = add(new GradientSlider(() -> state.hsv().v(), state::setBrightness,
				step -> 0xFF000000 | new Hsv(state.hsv().h(), state.hsv().s(), step / 255F).toRgb(), false));
		this.alpha = storesAlpha ? add(new GradientSlider(state::alpha, state::setAlpha, step -> step << 24 | state.hsv().toRgb(), true)) : null;
		// Room for more than a colour, so an oversize paste is marked invalid instead of cut to some other colour.
		TextEditModel hexModel = new TextEditModel(16, GameClipboard.INSTANCE);
		hexModel.setText(state.hexText());
		this.hex = add(TextField.of(hexModel, storesAlpha ? "#AARRGGBB" : "#RRGGBB", state::typeHex));
		hex.markInvalidWhen(() -> !state.hexValid());
		hex.selectAllOnFocus(true);
		for (int rgb : ColorPresets.CHAT) {
			presets.add(add(new PresetSwatch(rgb, clickSound)));
		}
		this.cancel = add(new Button("Cancel", Button.Style.NORMAL, () -> host.close(this), clickSound));
		this.save = add(new Button("Save", Button.Style.PRIMARY, this::save, clickSound));
	}

	private <W extends Widget> W add(W widget) {
		widgets.add(widget);
		return widget;
	}

	/** The state while open, for tests. */
	public ColorPickerState state() {
		return state;
	}

	TextField hexField() {
		return hex;
	}

	Button saveButton() {
		return save;
	}

	Button cancelButton() {
		return cancel;
	}

	ColorWheel wheel() {
		return wheel;
	}

	Widget brightnessSlider() {
		return brightness;
	}

	Widget alphaSlider() {
		return alpha;
	}

	Widget preset(int index) {
		return presets.get(index);
	}

	/** Whether the picker shows an alpha control (only for colours that store alpha). */
	public boolean hasAlphaControl() {
		return alpha != null;
	}

	private void save() {
		binding.set(state.color());
		commit.run();
		host.close(this);
	}

	@Override
	public boolean modal() {
		return true;
	}

	@Override
	public List<Widget> widgets() {
		return widgets;
	}

	@Override
	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= panelX && mouseY >= panelY && mouseX < panelX + WIDTH && mouseY < panelY + panelHeight;
	}

	@Override
	public void layout(int screenWidth, int screenHeight) {
		panelHeight = state.storesAlpha() ? 182 : 166;
		panelX = Math.max(4, (screenWidth - WIDTH) / 2);
		panelY = Math.max(4, (screenHeight - panelHeight) / 2);
		int column = panelX + 8 + WHEEL + 12;
		wheel.setBounds(panelX + 8, panelY + 24, WHEEL, WHEEL);
		hex.setBounds(column, panelY + 46, WIDTH - (column - panelX) - 8, 16);
		for (int i = 0; i < presets.size(); i++) {
			presets.get(i).setBounds(column + (i % 8) * (SWATCH + 2), panelY + 68 + (i / 8) * (SWATCH + 2), SWATCH, SWATCH);
		}
		brightness.setBounds(panelX + 8, panelY + 114, WIDTH - 16, 10);
		if (alpha != null) {
			alpha.setBounds(panelX + 8, panelY + 130, WIDTH - 16, 10);
		}
		int buttonsY = panelY + panelHeight - 24;
		save.setBounds(panelX + WIDTH - 8 - 64, buttonsY, 64, 16);
		cancel.setBounds(panelX + WIDTH - 8 - 64 - 6 - 64, buttonsY, 64, 16);
		// While typed in, the field keeps the player's text (#RRGGBB may be the start of #AARRGGBB) and only
		// follows the wheel, sliders and presets; once left, it shows the colour normalised, or the invalid text.
		boolean external = state.takeExternalChange();
		if (!hex.isFocused()) {
			String wanted = state.hexValid() ? state.normalizedHex() : state.hexText();
			if (!hex.model().text().equals(wanted)) {
				hex.model().setText(wanted);
			}
		} else if (external) {
			hex.model().setText(state.normalizedHex());
		}
	}

	@Override
	public void drawFrame(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Shapes.roundedRect(graphics, panelX, panelY, WIDTH, panelHeight, Shapes.RADIUS_CARD, Theme.PANEL);
		Shapes.roundedOutline(graphics, panelX, panelY, WIDTH, panelHeight, Shapes.RADIUS_CARD, 1, Theme.SEPARATOR);
		UiText.draw(graphics, font, title, panelX + 8, panelY + 8, Theme.TEXT_PRIMARY);
		int column = panelX + 8 + WHEEL + 12;
		int previewWidth = WIDTH - (column - panelX) - 8;
		// Before | after.
		drawColour(graphics, column, panelY + 24, previewWidth / 2, 16, state.initial());
		drawColour(graphics, column + previewWidth / 2, panelY + 24, previewWidth - previewWidth / 2, 16, state.color());
		Shapes.roundedOutline(graphics, column - 1, panelY + 23, previewWidth + 2, 18, 2, 1, Theme.SEPARATOR);
	}

	/** A colour box; one with alpha shows a checkerboard behind it. */
	private void drawColour(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
		if (state.storesAlpha()) {
			for (int cx = 0; cx < w; cx += 4) {
				for (int cy = 0; cy < h; cy += 4) {
					graphics.fill(x + cx, y + cy, Math.min(x + w, x + cx + 4), Math.min(y + h, y + cy + 4),
							((cx + cy) / 4) % 2 == 0 ? 0xFF9A9A9A : 0xFF5E5E5E);
				}
			}
			graphics.fill(x, y, x + w, y + h, colour);
		} else {
			graphics.fill(x, y, x + w, y + h, 0xFF000000 | colour);
		}
	}

	/** One preset colour: a click picks it. */
	private final class PresetSwatch extends Widget {
		private final int rgb;
		private final Runnable clickSound;

		PresetSwatch(int rgb, Runnable clickSound) {
			this.rgb = rgb;
			this.clickSound = clickSound;
		}

		@Override
		public boolean press(double mouseX, double mouseY, int button) {
			if (button != 0 || !contains(mouseX, mouseY)) {
				return false;
			}
			state.pickPreset(rgb);
			clickSound.run();
			return true;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			graphics.fill(x, y, x + width, y + height, 0xFF000000 | rgb);
			boolean current = (state.color() & 0xFFFFFF) == rgb;
			int outline = current ? Theme.TEXT_PRIMARY : contains(mouseX, mouseY) ? Theme.TEXT_SECONDARY : Theme.SEPARATOR;
			Shapes.roundedOutline(graphics, x - 1, y - 1, width + 2, height + 2, 1, 1, outline);
		}
	}
}
