package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Clip;
import com.k8bas.skyblockutility.ui.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * A list of many same-height rows that lays out, draws and hit-tests only the rows in view (REQ-UI-20),
 * for the database picker, long rule lists and the optimizer table. The wheel scrolls three rows a
 * notch (one with Shift); a thumb on the right edge can be dragged, and a press on its track jumps
 * there. The rows are drawn by a painter, so the list knows nothing about what it shows.
 */
public final class VirtualList extends Widget {
	static final int SCROLLBAR = 6;
	private static final int WHEEL_ROWS = 3;

	/** Draws one row in its rectangle (clipped to the list). */
	@FunctionalInterface
	public interface RowPainter {
		void draw(GuiGraphicsExtractor graphics, Font font, int index, int x, int y, int width, int height, boolean hovered);
	}

	private final VirtualRows rows;
	private final IntSupplier count;
	private final RowPainter painter;
	private final IntConsumer onClick;
	private IntFunction<String> rowTooltip = index -> "";
	/** While the thumb is dragged: where it was grabbed, from its top; else -1. */
	private int grab = -1;
	private VirtualRows.Range lastDrawn = new VirtualRows.Range(0, 0);

	public VirtualList(int rowHeight, IntSupplier count, RowPainter painter, IntConsumer onClick) {
		this.rows = new VirtualRows(rowHeight);
		this.count = count;
		this.painter = painter;
		this.onClick = onClick;
	}

	public VirtualRows rows() {
		return rows;
	}

	/** A tooltip per row, e.g. the whole text of a cut entry; null or "" for none. */
	public void setRowTooltip(IntFunction<String> rowTooltip) {
		this.rowTooltip = rowTooltip == null ? index -> "" : rowTooltip;
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		rows.setViewport(height);
		rows.setCount(count.getAsInt());
	}

	private int rowWidth() {
		return rows.overflows() ? width - SCROLLBAR : width;
	}

	private boolean onScrollbar(double mouseX) {
		return rows.overflows() && mouseX >= x + width - SCROLLBAR;
	}

	/** The row under the mouse, or -1. */
	public int rowAt(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY) || onScrollbar(mouseX)) {
			return -1;
		}
		return rows.rowAt(mouseY - y);
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		// A grab whose release never came (the press was taken over, or an overlay closed) ends here.
		grab = -1;
		if (onScrollbar(mouseX)) {
			VirtualRows.Thumb thumb = rows.thumb(height);
			int along = (int) Math.floor(mouseY - y);
			if (along >= thumb.offset() && along < thumb.offset() + thumb.length()) {
				grab = along - thumb.offset();
			} else {
				// On the track: the thumb's middle jumps to the pointer, and the drag carries on from there.
				grab = thumb.length() / 2;
				rows.setScroll(rows.scrollForThumb(along - grab, height));
			}
			return true;
		}
		int row = rows.rowAt(mouseY - y);
		if (row >= 0) {
			onClick.accept(row);
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
		rows.scrollRows(-notches * (fine ? 1 : WHEEL_ROWS));
		return true;
	}

	@Override
	public String tooltipAt(double mouseX, double mouseY) {
		int row = rowAt(mouseX, mouseY);
		if (row < 0) {
			return super.tooltipAt(mouseX, mouseY);
		}
		String text = rowTooltip.apply(row);
		return text == null ? "" : text;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		rows.setCount(count.getAsInt());
		int hoveredRow = enabled && grab < 0 ? rowAt(mouseX, mouseY) : -1;
		int rowWidth = rowWidth();
		try (Clip clip = Clip.push(graphics, x, y, width, height)) {
			if (!clip.visible()) {
				return;
			}
			VirtualRows.Range visible = rows.visible();
			lastDrawn = visible;
			for (int index = visible.first(); index < visible.end(); index++) {
				painter.draw(graphics, font, index, x, y + rows.rowTop(index), rowWidth, rows.rowHeight(), index == hoveredRow);
			}
			if (rows.overflows()) {
				VirtualRows.Thumb thumb = rows.thumb(height);
				boolean hot = grab >= 0 || onScrollbar(mouseX) && contains(mouseX, mouseY);
				graphics.fill(x + width - SCROLLBAR + 2, y + thumb.offset() + 1, x + width - 1, y + thumb.offset() + thumb.length() - 1,
						faded(hot ? Theme.TEXT_SECONDARY : Theme.TEXT_DISABLED));
			}
		}
	}

	/** The rows drawn in the last draw, for tests. */
	VirtualRows.Range laidOut() {
		return lastDrawn;
	}
}
