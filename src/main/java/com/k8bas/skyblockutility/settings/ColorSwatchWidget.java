package com.k8bas.skyblockutility.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/**
 * A small clickable color preview square, used to expand/collapse the wheel+slider picker below
 * a ColorWheelFieldEntry — the field starts collapsed to just this swatch and the hex box, since
 * the full wheel isn't needed to see or read the current color.
 */
public final class ColorSwatchWidget extends AbstractWidget {
	private final IntSupplier colorSupplier;
	private final Runnable onToggle;

	public ColorSwatchWidget(int size, IntSupplier colorSupplier, Runnable onToggle) {
		super(0, 0, size, size, Component.literal("Color"));
		this.colorSupplier = colorSupplier;
		this.onToggle = onToggle;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFFFFFFFF);
		graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1,
				0xFF000000 | colorSupplier.getAsInt());
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		onToggle.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
