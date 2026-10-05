package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

/**
 * A whole-number or decimal slider (REQ-UI-06). Only a press on its track starts a drag (REQ-UI-03);
 * the value follows the drag live, and {@code onCommit} runs once on release if it changed, so a drag
 * writes the file at most once (REQ-UI-15). The wheel steps live; Shift gives the fine step. A stored
 * value outside the range is only replaced once the shown value really changes (EC-UI-05).
 */
public final class Slider extends Widget {
	public static final int TRACK_HEIGHT = 4;
	public static final int KNOB_SIZE = 10;

	private final SliderModel model;
	private final LongSupplier get;
	private final LongConsumer set;
	private final Runnable onCommit;
	private final LongFunction<String> label;
	private final Runnable clickSound;
	private boolean dragging;
	private long valueAtPress;
	private boolean forcedHot;

	/**
	 * @param get        the stored value in model units
	 * @param set        stores a value at once (live apply)
	 * @param onCommit   runs once after a drag that changed the value
	 * @param label      the value as shown next to the track
	 * @param clickSound played when a press on the track is released
	 */
	public Slider(SliderModel model, LongSupplier get, LongConsumer set, Runnable onCommit, LongFunction<String> label, Runnable clickSound) {
		this.model = model;
		this.get = get;
		this.set = set;
		this.onCommit = onCommit;
		this.label = label;
		this.clickSound = clickSound;
	}

	public boolean isDragging() {
		return dragging;
	}

	/** Draws the hovered look regardless of the mouse, for screenshots of every state. */
	public void forceHot(boolean hot) {
		this.forcedHot = hot;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		dragging = true;
		valueAtPress = get.getAsLong();
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
		if (!dragging) {
			return;
		}
		dragging = false;
		clickSound.run();
		if (get.getAsLong() != valueAtPress) {
			onCommit.run();
		}
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, int notches, boolean fine) {
		if (!enabled || notches == 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		long stored = get.getAsLong();
		long after = model.stepFrom(stored, notches, fine);
		if (after != model.shown(stored)) {
			set.accept(after);
		}
		return true;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/**
	 * Right/Up a step up, Left/Down a step down (Shift: the fine step), Page Up/Down ten steps, Home/End the ends. Like
	 * the wheel: live, written when the screen closes, and a stored value off the range is kept until a step changes it.
	 */
	@Override
	public boolean navKey(NavKey key, boolean fine) {
		if (!enabled || dragging) {
			return false;
		}
		long stored = get.getAsLong();
		long after = switch (key) {
			case RIGHT, UP -> model.stepFrom(stored, 1, fine);
			case LEFT, DOWN -> model.stepFrom(stored, -1, fine);
			case PAGE_UP -> model.stepFrom(stored, 10, fine);
			case PAGE_DOWN -> model.stepFrom(stored, -10, fine);
			case HOME -> model.min();
			case END -> model.max();
			default -> Long.MIN_VALUE;
		};
		if (after == Long.MIN_VALUE) {
			return false;
		}
		if (after != model.shown(stored)) {
			set.accept(after);
		}
		return true;
	}

	/** Follows the pointer; writes only when the shown value changes. */
	private void moveTo(double mouseX) {
		double fraction = width <= KNOB_SIZE ? 0 : (mouseX - x - KNOB_SIZE / 2.0) / (width - KNOB_SIZE);
		long value = model.fromTrack(fraction, false);
		if (value != model.shown(get.getAsLong())) {
			set.accept(value);
		}
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Theme theme = Theme.current();
		long value = get.getAsLong();
		int trackY = y + (height - TRACK_HEIGHT) / 2;
		int knobX = x + (int) Math.round(model.fraction(value) * (width - KNOB_SIZE));
		boolean hot = enabled && (forcedHot || dragging || contains(mouseX, mouseY));
		float hover = forcedHot ? 1F : hoverAmount(hot);
		Shapes.roundedRect(graphics, x, trackY, width, TRACK_HEIGHT, TRACK_HEIGHT / 2, faded(Theme.SEPARATOR));
		int filled = knobX - x + KNOB_SIZE / 2;
		if (enabled) {
			Shapes.roundedRect(graphics, x, trackY, filled, TRACK_HEIGHT, TRACK_HEIGHT / 2, faded(ColorMath.lerp(theme.accent(), theme.accentHover(), hover)));
		}
		int knobColor = enabled ? ColorMath.lerp(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, hover) : Theme.TEXT_DISABLED;
		Shapes.roundedRect(graphics, knobX, y + (height - KNOB_SIZE) / 2, KNOB_SIZE, KNOB_SIZE, KNOB_SIZE / 2, faded(knobColor));
		UiText.draw(graphics, font, label.apply(value), x + width + 6, y + (height - font.lineHeight) / 2 + 1,
				faded(enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED));
	}
}
