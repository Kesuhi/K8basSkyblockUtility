package com.k8bas.skyblockutility.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.Consumer;

/** A horizontal brightness ("Value" in HSB) slider — the third color axis the wheel itself
 *  doesn't cover. Gradient runs from black to the current hue/saturation at full brightness. */
public final class ColorValueSliderWidget extends AbstractWidget {
	private float hue;
	private float saturation;
	private float value;
	private final Consumer<Float> onChange;

	public ColorValueSliderWidget(int width, int height, float hue, float saturation, float value, Consumer<Float> onChange) {
		super(0, 0, width, height, Component.literal("Brightness"));
		this.hue = hue;
		this.saturation = saturation;
		this.value = value;
		this.onChange = onChange;
	}

	public void setHueSaturation(float hue, float saturation) {
		this.hue = hue;
		this.saturation = saturation;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int width = getWidth();
		for (int i = 0; i < width; i++) {
			float v = width <= 1 ? value : i / (float) (width - 1);
			int rgb = 0xFF000000 | (Color.HSBtoRGB(hue, saturation, v) & 0xFFFFFF);
			int x = getX() + i;
			graphics.fill(x, getY(), x + 1, getY() + getHeight(), rgb);
		}
		int handleX = getX() + Math.round(value * (width - 1));
		graphics.fill(handleX - 1, getY() - 1, handleX + 2, getY() + getHeight() + 1, 0xFFFFFFFF);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		updateFromMouse(event.x());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		updateFromMouse(event.x());
	}

	private void updateFromMouse(double mouseX) {
		float fraction = (float) ((mouseX - getX()) / (double) getWidth());
		value = Math.max(0F, Math.min(1F, fraction));
		onChange.accept(value);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
