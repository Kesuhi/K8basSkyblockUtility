package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Corners;
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
 * writes the file at most once (REQ-UI-15). The wheel never changes it (REQ-UI-37): it goes on to the
 * page under it, and while the slider is dragged nowhere. The arrow keys step it while it has the
 * keyboard focus. A stored value outside the range is only replaced once the shown value really
 * changes (EC-UI-05). Its knob grows from {@link #KNOB_SIZE} to {@link #KNOB_GROWN} GUI px on hover and while dragged, and its
 * value shows in the accent colour while dragged (REQ-UI-46); its layout and where a press reaches it stay those of the
 * {@link #KNOB_SIZE} knob.
 */
public final class Slider extends Widget {
	public static final int TRACK_HEIGHT = 4;
	public static final int KNOB_SIZE = 10;
	/** The knob's drawn size on hover and while dragged (REQ-UI-46). */
	public static final int KNOB_GROWN = 12;

	private final SliderModel model;
	private final LongSupplier get;
	private final LongConsumer set;
	private final Runnable onCommit;
	private final LongFunction<String> label;
	private final Runnable clickSound;
	private boolean dragging;
	/** From the knob's centre to where the press grabbed it, so it does not jump to the pointer (REQ-UI-46); 0 on the track. */
	private double grabOffset;
	private long valueAtPress;
	private boolean forcedHot;
	/** The knob as drawn last, in screen pixels: left, top and size (for gametests). */
	private final int[] drawnKnob = new int[3];

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

	/** Where its value label starts: left-aligned 6 GUI px after the track's end, in the value column (REQ-UI-42). */
	public int labelX() {
		return x + width + 6;
	}

	/** The knob's left edge in whole GUI pixels, for the stored value: the layout. */
	public int knobX() {
		return x + (int) Math.round(model.fraction(get.getAsLong()) * (width - KNOB_SIZE));
	}

	/**
	 * Where the knob at its layout size, {@link #KNOB_SIZE}, is drawn (REQ-UI-34): its exact left edge on the physical-pixel grid
	 * at GUI scale {@code scale}. Grown, it is drawn about the same centre.
	 */
	public float drawnKnobX(int scale) {
		return Shapes.snap((float) (x + model.fraction(get.getAsLong()) * (width - KNOB_SIZE)), scale);
	}

	/** How hot it looks now, 0 to 1, eased (REQ-UI-17): hovered, dragged or forced. */
	private float hover(double mouseX, double mouseY) {
		if (forcedHot) {
			return 1F;
		}
		return hoverAmount(enabled && (dragging || contains(mouseX, mouseY)));
	}

	/** How big the knob is drawn now, {@link #KNOB_SIZE} to {@link #KNOB_GROWN} GUI px: grown on hover and while dragged (REQ-UI-46). */
	float knobSize(double mouseX, double mouseY) {
		return knobSizeFor(hover(mouseX, mouseY));
	}

	/** The knob's size for a hover amount from 0 to 1. */
	private static float knobSizeFor(float hover) {
		return KNOB_SIZE + (KNOB_GROWN - KNOB_SIZE) * hover;
	}

	/**
	 * The diameter in screen px of the disc mask the knob is drawn from at this hover and GUI scale (REQ-UI-47): the resting
	 * knob's at hover 0, else the grown knob's, also while it grows or shrinks, so the sizes in between add no masks to the cache.
	 */
	static int knobMaskPx(float hover, int scale) {
		return Math.round((hover <= 0F ? KNOB_SIZE : KNOB_GROWN) * scale);
	}

	/** The knob as drawn last, in screen pixels: left, top and size (for gametests). */
	public int[] drawnKnob() {
		return drawnKnob.clone();
	}

	/** The value label's colour: the accent while dragged (REQ-UI-46), else the normal text colour, or the disabled one. */
	int labelColor(Theme theme) {
		if (!enabled) {
			return Theme.TEXT_DISABLED;
		}
		return dragging ? theme.accent() : Theme.TEXT_PRIMARY;
	}

	/** Draws the hovered look regardless of the mouse, for screenshots of every state. */
	public void forceHot(boolean hot) {
		this.forcedHot = hot;
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
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		dragging = true;
		valueAtPress = get.getAsLong();
		// On the knob as drawn (grown on hover): it keeps where it was grabbed, and its value is left as it is until the drag
		// moves it. Elsewhere on the track: its centre goes to the pointer.
		double centre = x + KNOB_SIZE / 2.0 + model.fraction(valueAtPress) * (width - KNOB_SIZE);
		if (Math.abs(mouseX - centre) <= knobSize(mouseX, mouseY) / 2.0) {
			grabOffset = mouseX - centre;
		} else {
			grabOffset = 0;
			moveTo(mouseX);
		}
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (dragging) {
			moveTo(mouseX - grabOffset);
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
		// Never taken, in any state, so a page scrolls over it as over the rest of its card (REQ-UI-37).
		return false;
	}

	/** While dragged, the wheel goes nowhere: no value change and no scroll (REQ-UI-37, EC-UI-25). */
	@Override
	public boolean blocksWheel() {
		return dragging;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/**
	 * Right/Up a step up, Left/Down a step down (Shift: the fine step), Page Up/Down ten steps, Home/End the ends: live,
	 * written when the screen closes, and a stored value off the range is kept until a step changes it.
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

	/** Puts the knob's centre at {@code mouseX}; writes only when the shown value changes. */
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
		int scale = Shapes.guiScale();
		float hover = hover(mouseX, mouseY);
		// The knob grows about its centre (REQ-UI-46), its size in whole screen pixels and its box on their grid (REQ-UI-34).
		float centreX = (float) (x + KNOB_SIZE / 2.0 + model.fraction(value) * (width - KNOB_SIZE));
		// Concentric with the track's rows, as the layout's knob is, also for an odd height.
		float centreY = y + (height - KNOB_SIZE) / 2 + KNOB_SIZE / 2F;
		int size = Math.max(1, Math.round(knobSizeFor(hover) * scale));
		float knobX = Math.round(centreX * scale - size / 2F) / (float) scale;
		drawnKnob[0] = Math.round(knobX * scale);
		drawnKnob[1] = Math.round(centreY * scale - size / 2F);
		drawnKnob[2] = size;
		// REQ-UI-28: nothing that shows lies under the knob. The fill (the track's colour while disabled) ends, and the
		// rest of the track starts, where the knob's disc meets the track's top and bottom edges; the opaque knob covers
		// the sliver under its middle rows. A dimmed knob is translucent, so they stop at its box instead.
		float[] gap = trackGap(knobX, size / (float) scale, TRACK_HEIGHT, dimmed);
		int fill = enabled ? ColorMath.lerp(theme.accent(), theme.accentHover(), hover) : Theme.SEPARATOR;
		Shapes.bar(graphics, x, gap[0], trackY, TRACK_HEIGHT, Corners.LEFT, faded(fill));
		Shapes.bar(graphics, gap[1], x + width, trackY, TRACK_HEIGHT, Corners.RIGHT, faded(Theme.SEPARATOR));
		int knobColor = enabled ? ColorMath.lerp(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, hover) : Theme.TEXT_DISABLED;
		Shapes.disc(graphics, centreX, centreY, size / (float) scale, knobMaskPx(hover, scale), faded(knobColor));
		UiText.draw(graphics, font, label.apply(value), labelX(), y + (height - font.lineHeight) / 2 + 1, faded(labelColor(theme)));
	}

	/**
	 * Where the fill ends and the rest of the track starts (REQ-UI-28), in GUI pixels, for a knob {@code knob} GUI pixels wide
	 * at {@code knobX} over a track {@code trackHeight} high: knob centre ∓ sqrt(r² − (h/2)²) for an opaque knob, centre ∓ r
	 * for a dimmed one.
	 */
	static float[] trackGap(float knobX, float knob, int trackHeight, boolean dimmed) {
		float radius = knob / 2F;
		float half = trackHeight / 2F;
		float reach = dimmed ? radius : (float) Math.sqrt(radius * radius - half * half);
		float centre = knobX + radius;
		return new float[] {centre - reach, centre + reach};
	}
}
