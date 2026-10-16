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
 * one sits on a checkerboard so transparency shows. Its track is a pill and its knob the slider's round
 * one, outlined so it shows on light colours (REQ-UI-40); the knob's centre runs inside the track, as a
 * slider's does. It lies on the picker's panel, whose colour cuts the track's round ends.
 */
final class GradientSlider extends Widget {
	private final DoubleSupplier get;
	private final Consumer<Float> set;
	/** The colour at a position 0..255 along the track. */
	private final IntUnaryOperator colourAt;
	private final boolean checkerboard;
	private boolean dragging;

	/** While dragged, the wheel goes nowhere (REQ-UI-37). */
	@Override
	public boolean blocksWheel() {
		return dragging;
	}

	GradientSlider(DoubleSupplier get, Consumer<Float> set, IntUnaryOperator colourAt, boolean checkerboard) {
		this.get = get;
		this.set = set;
		this.colourAt = colourAt;
		this.checkerboard = checkerboard;
	}

	/** The hand, as the game's own sliders show. */
	@Override
	public PointerShape cursorAt(double mouseX, double mouseY) {
		return PointerShape.HAND;
	}

	/** While dragged, the horizontal-resize pointer, wherever the mouse goes (REQ-UI-43). */
	@Override
	public PointerShape dragCursor() {
		return dragging ? PointerShape.RESIZE_HORIZONTAL : null;
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
		int knob = Slider.KNOB_SIZE;
		set.accept((float) Math.max(0, Math.min(1, (mouseX - x - knob / 2.0) / Math.max(1, width - knob))));
	}

	/** Where the knob's centre is drawn, in GUI px: from half a knob in at 0 to half a knob in from the right at 1. */
	float knobCentre() {
		int knob = Slider.KNOB_SIZE;
		return (float) (x + knob / 2.0 + get.getAsDouble() * (width - knob));
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (checkerboard) {
			for (int cx = 0; cx < width; cx += 4) {
				for (int cy = 0; cy < height; cy += 4) {
					boolean light = ((cx + cy) / 4) % 2 == 0;
					Shapes.plain(graphics, x + cx, y + cy, Math.min(x + width, x + cx + 4), Math.min(y + height, y + cy + 4), light ? 0xFF9A9A9A : 0xFF5E5E5E);
				}
			}
		}
		int knob = Slider.KNOB_SIZE;
		for (int column = 0; column < width; column++) {
			// Along the knob's track: the colour under the knob's centre is the one picked.
			float along = Math.max(0F, Math.min(1F, (column + 0.5F - knob / 2F) / Math.max(1, width - knob)));
			int colour = colourAt.applyAsInt(Math.round(along * 255F));
			Shapes.plain(graphics, x + column, y, x + column + 1, y + height, colour);
		}
		cutRoundEnds(graphics);
		Shapes.hairlineAround(graphics, x, y, width, height, height / 2, Theme.SEPARATOR);
		// The slider's round knob, on a dark disc one GUI px larger so it shows on light colours too.
		float centre = knobCentre();
		float top = y + height / 2F;
		Shapes.roundedRect(graphics, centre - knob / 2F - 1, top - knob / 2F - 1, knob + 2, knob + 2, knob / 2 + 1, 0xC0000000);
		Shapes.roundedRect(graphics, centre - knob / 2F, top - knob / 2F, knob, knob, knob / 2, Theme.TEXT_PRIMARY);
	}

	/**
	 * Covers what lies outside the track's round ends, half its height in radius, in the panel's colour, one screen pixel
	 * column at a time: the track shows as a pill.
	 */
	private void cutRoundEnds(GuiGraphicsExtractor graphics) {
		int scale = Shapes.guiScale();
		float radius = height * scale / 2F;
		int columns = Math.min(Math.round(radius), width * scale / 2);
		for (int column = 0; column < columns; column++) {
			float dx = radius - (column + 0.5F);
			float inset = radius - (float) Math.sqrt(Math.max(0F, radius * radius - dx * dx));
			int rows = Math.round(inset);
			if (rows <= 0) {
				continue;
			}
			for (float left : new float[] {x + (float) column / scale, x + width - (float) (column + 1) / scale}) {
				Shapes.fill(graphics, left, y, left + 1F / scale, y + (float) rows / scale, Theme.PANEL);
				Shapes.fill(graphics, left, y + height - (float) rows / scale, left + 1F / scale, y + height, Theme.PANEL);
			}
		}
	}
}
