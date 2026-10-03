package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A region that scrolls content of any height by pixels (REQ-UI-02): the wheel over it scrolls, a thumb
 * on its right edge can be dragged, and a press on the track jumps there. It sits under the widgets it
 * scrolls, which are laid out from {@link #scroll()} and clipped to it; a press elsewhere is not taken.
 */
public final class ScrollArea extends Widget {
	public static final int SCROLLBAR = 6;
	public static final int WHEEL_STEP = 24;
	public static final int FINE_STEP = 6;

	private final VirtualRows rows = new VirtualRows(1);
	private int grab = -1;

	/** The content's height, set by the layout each frame; the scroll is held to it (EC-UI-01). */
	public void setContentHeight(int contentHeight) {
		rows.setCount(contentHeight);
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		rows.setViewport(height);
	}

	public int scroll() {
		return rows.scroll();
	}

	public void setScroll(int scroll) {
		rows.setScroll(scroll);
	}

	public int maxScroll() {
		return rows.maxScroll();
	}

	/** Scrolls as little as needed to show the span from {@code top} to {@code bottom} (content pixels). */
	public void ensureVisible(int top, int bottom) {
		if (top < rows.scroll()) {
			rows.setScroll(top);
		} else if (bottom > rows.scroll() + height) {
			rows.setScroll(bottom - height);
		}
	}

	private boolean onScrollbar(double mouseX) {
		return rows.overflows() && mouseX >= x + width - SCROLLBAR;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		grab = -1;
		if (!enabled || button != 0 || !contains(mouseX, mouseY) || !onScrollbar(mouseX)) {
			return false;
		}
		VirtualRows.Thumb thumb = rows.thumb(height);
		int along = (int) Math.floor(mouseY - y);
		if (along >= thumb.offset() && along < thumb.offset() + thumb.length()) {
			grab = along - thumb.offset();
		} else {
			grab = thumb.length() / 2;
			rows.setScroll(rows.scrollForThumb(along - grab, height));
		}
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (grab >= 0) {
			rows.setScroll(rows.scrollForThumb((int) Math.floor(mouseY - y) - grab, height));
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
		rows.scrollRows(-notches * (fine ? FINE_STEP : WHEEL_STEP));
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (!rows.overflows()) {
			return;
		}
		VirtualRows.Thumb thumb = rows.thumb(height);
		boolean hot = grab >= 0 || contains(mouseX, mouseY) && onScrollbar(mouseX);
		graphics.fill(x + width - SCROLLBAR + 2, y + thumb.offset() + 1, x + width - 1, y + thumb.offset() + thumb.length() - 1,
				hot ? Theme.TEXT_SECONDARY : Theme.TEXT_DISABLED);
	}
}
