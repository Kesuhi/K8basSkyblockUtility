package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.DoubleSupplier;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/**
 * A 0..1 slider drawn as a colour gradient (the picker's brightness and alpha, REQ-UI-13); the alpha
 * one sits on a checkerboard so transparency shows.
 */
final class GradientSlider extends Widget {
	private final DoubleSupplier get;
	private final Consumer<Float> set;
	/** The colour at a position 0..255 along the track. */
	private final IntUnaryOperator colourAt;
	private final boolean checkerboard;
	private boolean dragging;

	GradientSlider(DoubleSupplier get, Consumer<Float> set, IntUnaryOperator colourAt, boolean checkerboard) {
		this.get = get;
		this.set = set;
		this.colourAt = colourAt;
		this.checkerboard = checkerboard;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		dragging = true;
		moveTo(mouseX);
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (dragging) {
			moveTo(mouseX);
		}
	}

	@Override
	public void release(double mouseX, double mouseY) {
		dragging = false;
	}

	private void moveTo(double mouseX) {
		set.accept((float) Math.max(0, Math.min(1, (mouseX - x) / Math.max(1, width - 1))));
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (checkerboard) {
			for (int cx = 0; cx < width; cx += 4) {
				for (int cy = 0; cy < height; cy += 4) {
					boolean light = ((cx + cy) / 4) % 2 == 0;
					graphics.fill(x + cx, y + cy, Math.min(x + width, x + cx + 4), Math.min(y + height, y + cy + 4), light ? 0xFF9A9A9A : 0xFF5E5E5E);
				}
			}
		}
		for (int column = 0; column < width; column++) {
			int colour = colourAt.applyAsInt(Math.round(column * 255F / Math.max(1, width - 1)));
			graphics.fill(x + column, y, x + column + 1, y + height, colour);
		}
		Shapes.roundedOutline(graphics, x - 1, y - 1, width + 2, height + 2, 2, 1, Theme.SEPARATOR);
		int handle = x + (int) Math.round(get.getAsDouble() * (width - 1));
		graphics.fill(handle - 2, y - 2, handle + 1, y + height + 2, 0xFF000000);
		graphics.fill(handle - 1, y - 1, handle, y + height + 1, 0xFFFFFFFF);
	}
}
