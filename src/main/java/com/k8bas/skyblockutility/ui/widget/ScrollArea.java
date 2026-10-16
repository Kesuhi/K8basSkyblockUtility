package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.UiClock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A region that scrolls content of any height by pixels (REQ-UI-02): the wheel over it scrolls, a thumb
 * on its right edge can be dragged, and a press on the track jumps there. It sits under the widgets it
 * scrolls, which are laid out from {@link #scroll()} and clipped to it; a press elsewhere is not taken.
 * The wheel and {@link #ensureVisible} glide (REQ-UI-35, {@link SmoothScroll}); {@link #scroll()} is the whole-px
 * offset of this moment, and {@link #drawnScroll} the one to draw at.
 */
public final class ScrollArea extends Widget {
	public static final int SCROLLBAR = ScrollbarPainter.HIT_WIDTH;
	public static final int WHEEL_STEP = 24;
	public static final int FINE_STEP = 6;

	private final VirtualRows rows = new VirtualRows(1);
	private final SmoothScroll smooth = new SmoothScroll(UiClock.SOURCE);
	private final ScrollbarPainter scrollbar = new ScrollbarPainter();
	private int grab = -1;
	/** How far short of the region's bottom its scrollbar's track ends: clear of a rounded panel's corner (REQ-UI-40). */
	private int trackInset;

	/** The content's height, set by the layout each frame; the scroll is held to it (EC-UI-01). */
	public void setContentHeight(int contentHeight) {
		rows.setCount(contentHeight);
		smooth.setMax(rows.maxScroll());
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		rows.setViewport(height);
		smooth.setMax(rows.maxScroll());
	}

	/** The scroll the content is laid out at now, in whole GUI px: where clicks meet it. */
	public int scroll() {
		sync();
		return rows.scroll();
	}

	/** Where the scroll is heading: the wheel's and {@link #ensureVisible}'s target. */
	public int target() {
		return smooth.target();
	}

	/** The scroll to draw the content at, on the physical-pixel grid at GUI scale {@code scale}, under 1 GUI px from {@link #scroll()}. */
	public float drawnScroll(int scale) {
		return smooth.drawn(scale);
	}

	/**
	 * The GUI scale the content is drawn at, set by the layout before it reads {@link #scroll()}: a change puts the scroll on
	 * its target at once (EC-UI-18), before anything of the frame is laid out or drawn.
	 */
	public void setScale(int scale) {
		smooth.setScale(scale);
	}

	/** Shows {@code scroll} at once: a restored scroll, or the rest of a slide. */
	public void setScroll(int scroll) {
		smooth.setMax(rows.maxScroll());
		smooth.jumpTo(scroll);
		sync();
	}

	/** The thumb as laid out now (its offset from the region's top, and its length), for tests. */
	public VirtualRows.Thumb thumb() {
		sync();
		return rows.thumb(track());
	}

	/** Ends the scrollbar's track {@code inset} GUI px short of the region's bottom; the region itself is unchanged. */
	public void setTrackInset(int inset) {
		trackInset = Math.max(0, inset);
	}

	private int track() {
		return Math.max(1, height - trackInset);
	}

	public int maxScroll() {
		return rows.maxScroll();
	}

	/** Glides as little as needed to show the span from {@code top} to {@code bottom} (content pixels). */
	public void ensureVisible(int top, int bottom) {
		smooth.setMax(rows.maxScroll());
		if (top < smooth.target()) {
			smooth.glideTo(top);
		} else if (bottom > smooth.target() + height) {
			smooth.glideTo(bottom - height);
		}
	}

	/**
	 * Ends a glide where the content was last laid out (EC-UI-25): a press on what was drawn there stays on it, and the
	 * page does not move away under a drag.
	 */
	public void stopGlide() {
		if (rows.scroll() != smooth.target()) {
			smooth.jumpTo(rows.scroll());
		}
	}

	/** While its thumb is dragged the wheel goes nowhere, over this region or any other (EC-UI-25). */
	@Override
	public boolean blocksWheel() {
		return grab >= 0;
	}

	/** A press elsewhere ends a thumb drag whose release went to another widget, so the wheel is not left ignored. */
	public void endDrag() {
		grab = -1;
	}

	private void sync() {
		smooth.setMax(rows.maxScroll());
		rows.setScroll(smooth.laidOut());
	}

	private boolean onScrollbar(double mouseX) {
		return rows.overflows() && ScrollbarPainter.inHitArea(x, width, mouseX);
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		grab = -1;
		if (!enabled || button != 0 || !contains(mouseX, mouseY) || !onScrollbar(mouseX)) {
			return false;
		}
		// The thumb is where it was drawn; a glide stops there, and the thumb or the track takes over.
		stopGlide();
		VirtualRows.Thumb thumb = rows.thumb(track());
		int along = Math.min(track() - 1, (int) Math.floor(mouseY - y));
		if (along >= thumb.offset() && along < thumb.offset() + thumb.length()) {
			grab = along - thumb.offset();
		} else {
			grab = thumb.length() / 2;
			setScroll(rows.scrollForThumb(along - grab, track()));
		}
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (grab >= 0) {
			setScroll(rows.scrollForThumb((int) Math.floor(mouseY - y) - grab, track()));
		}
	}

	@Override
	public void release(double mouseX, double mouseY) {
		grab = -1;
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, int notches, boolean fine) {
		if (!enabled || !contains(mouseX, mouseY) || !rows.overflows()) {
			return false;
		}
		// While the thumb is dragged it follows the pointer, and the wheel is ignored (EC-UI-25).
		// The layout follows on the next frame, so a press before it meets what was drawn.
		if (grab < 0) {
			smooth.glideBy(-notches * (fine ? FINE_STEP : WHEEL_STEP));
		}
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		sync();
		if (!rows.overflows()) {
			return;
		}
		VirtualRows.Thumb thumb = rows.thumb(track());
		boolean hot = grab >= 0 || contains(mouseX, mouseY) && onScrollbar(mouseX);
		scrollbar.draw(graphics, x, y, width, track(), thumb.offset(), thumb.length(), hot, argb -> argb);
	}
}
