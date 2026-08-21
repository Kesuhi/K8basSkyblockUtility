package com.k8bas.skyblockutility.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.BiConsumer;

/**
 * A draggable hue/saturation wheel — angle around the center picks hue, distance from center
 * picks saturation. Brightness ("Value" in HSB) is controlled separately (see
 * ColorValueSliderWidget), since a 2D surface can only cleanly drive two of HSB's three axes.
 * Extends AbstractWidget (the same base every other interactive vanilla control uses, including
 * Cloth Config's own) rather than hand-rolling click/drag/focus plumbing.
 */
public final class ColorWheelWidget extends AbstractWidget {
	private float hue;
	private float saturation;
	private float value;
	private final BiConsumer<Float, Float> onChange;

	public ColorWheelWidget(int size, float hue, float saturation, float value, BiConsumer<Float, Float> onChange) {
		super(0, 0, size, size, Component.literal("Color wheel"));
		this.hue = hue;
		this.saturation = saturation;
		this.value = value;
		this.onChange = onChange;
	}

	public void setHueSaturation(float hue, float saturation) {
		this.hue = hue;
		this.saturation = saturation;
	}

	public void setValue(float value) {
		this.value = value;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		ColorWheelTexture.ensureValue(value);
		graphics.blit(RenderPipelines.GUI_TEXTURED, ColorWheelTexture.ID, getX(), getY(), 0F, 0F,
				getWidth(), getHeight(), ColorWheelTexture.SIZE, ColorWheelTexture.SIZE);

		float radius = getWidth() / 2F;
		float cx = getX() + radius;
		float cy = getY() + getHeight() / 2F;
		double angle = (hue - 0.5) * Math.PI * 2;
		int puckX = Math.round((float) (cx + Math.cos(angle) * saturation * radius));
		int puckY = Math.round((float) (cy + Math.sin(angle) * saturation * radius));

		int outer = 4;
		graphics.fill(puckX - outer, puckY - outer, puckX + outer, puckY + outer, 0xFFFFFFFF);
		int inner = 3;
		int puckColor = 0xFF000000 | (Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF);
		graphics.fill(puckX - inner, puckY - inner, puckX + inner, puckY + inner, puckColor);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		updateFromMouse(event.x(), event.y());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		updateFromMouse(event.x(), event.y());
	}

	private void updateFromMouse(double mouseX, double mouseY) {
		float radius = getWidth() / 2F;
		double cx = getX() + radius;
		double cy = getY() + getHeight() / 2F;
		double dx = mouseX - cx;
		double dy = mouseY - cy;
		double distance = Math.sqrt(dx * dx + dy * dy);
		saturation = (float) Math.min(1.0, distance / radius);
		if (distance > 0.0001) {
			hue = (float) ((Math.atan2(dy, dx) / (Math.PI * 2)) + 0.5);
		}
		onChange.accept(hue, saturation);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
