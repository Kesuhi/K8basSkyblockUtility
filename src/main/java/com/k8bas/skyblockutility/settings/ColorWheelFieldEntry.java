package com.k8bas.skyblockutility.settings;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A color preview swatch + hex field, with a draggable hue/saturation wheel and brightness slider
 * that expand below when the swatch is clicked (collapsed by default — matches the earlier plain
 * hex-only field's footprint until the user actually wants the wheel). Hue/saturation/value are
 * kept as this entry's own authoritative state (not re-derived from the packed RGB color on every
 * wheel/slider drag) — round-tripping through RGB and back loses precision at low
 * saturation/value (e.g. dragging saturation to 0 makes hue meaningless), which would make the
 * puck visibly jump around. Only typing a hex value re-derives hue/saturation/value from RGB,
 * since that's the one input path that only ever gives us RGB in the first place.
 *
 * The swatch + hex row is always rendered directly under the label, both collapsed and expanded,
 * rather than at the bottom of a (potentially very tall, once expanded) entry — keeping it at a
 * fixed, small offset avoids any risk of the hex field's clickable area landing outside whatever
 * region the list widget scissors/hit-tests for this row.
 *
 * Matches HexColorFieldEntry's old contract (a Consumer<String> hex saveConsumer) so it drops in
 * at the same call sites — the actual rule.color assignment still only happens on save(), same as
 * every other field in the rule editor, so Cancel still discards an in-progress pick.
 */
public final class ColorWheelFieldEntry extends AbstractConfigListEntry<String> {
	private static final int WHEEL_SIZE = 90;
	private static final int SLIDER_HEIGHT = 12;
	private static final int GAP = 4;
	private static final int LABEL_HEIGHT = 12;
	private static final int HEX_ROW_HEIGHT = 18;
	private static final int SWATCH_SIZE = HEX_ROW_HEIGHT - 4;

	private final ColorWheelWidget wheel;
	private final ColorValueSliderWidget slider;
	private final ColorSwatchWidget swatch;
	private final EditBox hexField;
	private final Consumer<String> saveConsumer;

	private float hue;
	private float saturation;
	private float value;
	private int currentColor;
	private boolean expanded = false;

	public ColorWheelFieldEntry(Component fieldName, int initialColor, Consumer<String> saveConsumer) {
		super(fieldName, false);
		this.saveConsumer = saveConsumer;
		this.currentColor = initialColor & 0xFFFFFF;

		float[] hsb = Color.RGBtoHSB((initialColor >> 16) & 0xFF, (initialColor >> 8) & 0xFF, initialColor & 0xFF, null);
		this.hue = hsb[0];
		this.saturation = hsb[1];
		this.value = hsb[2];

		this.wheel = new ColorWheelWidget(WHEEL_SIZE, hue, saturation, value, this::onHueSaturationChanged);
		this.slider = new ColorValueSliderWidget(WHEEL_SIZE, SLIDER_HEIGHT, hue, saturation, value, this::onValueChanged);
		this.swatch = new ColorSwatchWidget(SWATCH_SIZE, () -> currentColor, this::toggleExpanded);
		this.hexField = new EditBox(Minecraft.getInstance().font, 0, 0, 70, HEX_ROW_HEIGHT, Component.empty());
		this.hexField.setValue(String.format("%06X", currentColor));
		this.hexField.setResponder(this::onHexTyped);
	}

	private void toggleExpanded() {
		expanded = !expanded;
	}

	private void onHueSaturationChanged(float newHue, float newSaturation) {
		hue = newHue;
		saturation = newSaturation;
		applyHsb();
	}

	private void onValueChanged(float newValue) {
		value = newValue;
		applyHsb();
	}

	private void onHexTyped(String text) {
		if (!text.matches("[0-9A-Fa-f]{6}")) {
			return;
		}
		currentColor = Integer.parseInt(text, 16);
		float[] hsb = Color.RGBtoHSB((currentColor >> 16) & 0xFF, (currentColor >> 8) & 0xFF, currentColor & 0xFF, null);
		hue = hsb[0];
		saturation = hsb[1];
		value = hsb[2];
		wheel.setHueSaturation(hue, saturation);
		wheel.setValue(value);
		slider.setHueSaturation(hue, saturation);
	}

	private void applyHsb() {
		currentColor = Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF;
		wheel.setHueSaturation(hue, saturation);
		wheel.setValue(value);
		slider.setHueSaturation(hue, saturation);
		hexField.setValue(String.format("%06X", currentColor));
	}

	@Override
	public void save() {
		saveConsumer.accept(String.format("%06X", currentColor));
	}

	@Override
	public int getItemHeight() {
		int height = LABEL_HEIGHT + HEX_ROW_HEIGHT + 4;
		if (expanded) {
			height += GAP + WHEEL_SIZE + GAP + SLIDER_HEIGHT;
		}
		return height;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth, int entryHeight,
			int mouseX, int mouseY, boolean isHovered, float delta) {
		graphics.text(Minecraft.getInstance().font, getDisplayedFieldName(), x, y, getPreferredTextColor());

		int hexY = y + LABEL_HEIGHT;
		swatch.setX(x);
		swatch.setY(hexY + (HEX_ROW_HEIGHT - SWATCH_SIZE) / 2);
		swatch.extractRenderState(graphics, mouseX, mouseY, delta);

		hexField.setWidth(70);
		hexField.setX(x + SWATCH_SIZE + 6);
		hexField.setY(hexY);
		hexField.extractRenderState(graphics, mouseX, mouseY, delta);

		if (expanded) {
			int contentY = hexY + HEX_ROW_HEIGHT + GAP;
			wheel.setX(x);
			wheel.setY(contentY);
			wheel.extractRenderState(graphics, mouseX, mouseY, delta);

			int sliderY = contentY + WHEEL_SIZE + GAP;
			slider.setX(x);
			slider.setY(sliderY);
			slider.extractRenderState(graphics, mouseX, mouseY, delta);
		}

		super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
	}

	@Override
	public String getValue() {
		return hexField.getValue();
	}

	@Override
	public Optional<String> getDefaultValue() {
		return Optional.empty();
	}

	@Override
	public List<? extends GuiEventListener> children() {
		List<GuiEventListener> list = new ArrayList<>();
		list.add(swatch);
		list.add(hexField);
		if (expanded) {
			list.add(wheel);
			list.add(slider);
		}
		return list;
	}

	@Override
	public List<? extends NarratableEntry> narratables() {
		List<NarratableEntry> list = new ArrayList<>();
		list.add(swatch);
		list.add(hexField);
		if (expanded) {
			list.add(wheel);
			list.add(slider);
		}
		return list;
	}
}
