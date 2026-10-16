package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Clip;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * A list of many same-height rows that lays out, draws and hit-tests only the rows in view (REQ-UI-20),
 * for the database picker, long rule lists and the optimizer table. The wheel scrolls three rows a
 * notch (one with Shift); a thumb on the right edge can be dragged, and a press on its track jumps
 * there. The rows are drawn by a painter, so the list knows nothing about what it shows. The wheel and
 * {@link #ensureVisible} glide (REQ-UI-35, {@link SmoothScroll}); {@link #rows()} holds the whole-px offset of this
 * moment, which clicks meet, and the rest is drawn as a translation on the physical-pixel grid.
 */
public final class VirtualList extends Widget {
	static final int SCROLLBAR = ScrollbarPainter.HIT_WIDTH;
	private static final int WHEEL_ROWS = 3;

	/** Draws one row in its rectangle (clipped to the list). */
	@FunctionalInterface
	public interface RowPainter {
		void draw(GuiGraphicsExtractor graphics, Font font, int index, int x, int y, int width, int height, boolean hovered);
	}

	private final VirtualRows rows;
	private final SmoothScroll smooth = new SmoothScroll(UiClock.SOURCE);
	private final ScrollbarPainter scrollbar = new ScrollbarPainter();
	private final IntSupplier count;
	private final RowPainter painter;
	private final IntConsumer onClick;
	private KeyHandler keys;
	private IntSupplier focusRow;
	private IntFunction<String> rowTooltip = index -> "";
	/** A click that needs to know where in the row it fell (a button on the row); replaces the plain one. */
	private RowClick rowClick;
	/** While the thumb is dragged: where it was grabbed, from its top; else -1. */
	private int grab = -1;
	private RowCursor rowCursor;
	private VirtualRows.Range lastDrawn = new VirtualRows.Range(0, 0);

	public VirtualList(int rowHeight, IntSupplier count, RowPainter painter, IntConsumer onClick) {
		this.rows = new VirtualRows(rowHeight);
		this.count = count;
		this.painter = painter;
		this.onClick = onClick;
	}

	/** The rows, laid out at the scroll of this moment. */
	public VirtualRows rows() {
		sync();
		return rows;
	}

	/** Shows {@code scroll} at once (a new search shows the top). */
	public void setScroll(int scroll) {
		smooth.setMax(rows.maxScroll());
		smooth.jumpTo(scroll);
		sync();
	}

	/** Glides as little as needed to show row {@code index} whole (the sidebar's selected tab). */
	public void ensureVisible(int index) {
		if (index < 0 || index >= rows.count()) {
			return;
		}
		smooth.setMax(rows.maxScroll());
		int top = index * rows.rowHeight();
		if (top < smooth.target()) {
			smooth.glideTo(top);
		} else if (top + rows.rowHeight() > smooth.target() + height) {
			smooth.glideTo(top + rows.rowHeight() - height);
		}
	}

	private void sync() {
		smooth.setMax(rows.maxScroll());
		rows.setScroll(smooth.laidOut());
	}

	/** How far the rows are drawn below where they are laid out now, under 1 GUI px, on the physical-pixel grid at {@code scale}. */
	float drawShift(int scale) {
		sync();
		return rows.scroll() - smooth.drawn(scale);
	}

	/** While its thumb is dragged the wheel goes nowhere, over this list or any other region (EC-UI-25). */
	@Override
	public boolean blocksWheel() {
		return grab >= 0;
	}

	/** For tests: the GUI scale the list is drawn at, as a draw sets it. */
	void setScale(int scale) {
		smooth.setScale(scale);
	}

	/** A row click with its place in the row. */
	@FunctionalInterface
	public interface RowClick {
		/**
		 * @param x from the row's left edge
		 * @param y from the row's top edge
		 * @param width the row's width (without the scroll bar)
		 */
		void click(int index, double x, double y, int width, int height);
	}

	/** Clicks on rows go here, with where in the row they fell, instead of to the plain click. */
	public void onRowClick(RowClick rowClick) {
		this.rowClick = rowClick;
	}

	/** A row's pointer shape (REQ-UI-43), with where in the row the mouse is, as {@link RowClick} gets a click. */
	@FunctionalInterface
	public interface RowCursor {
		PointerShape at(int index, double x, double y, int width, int height);
	}

	/** The shape over each row, for rows only parts of which a click reaches; without one every row shows the hand. */
	public void setRowCursor(RowCursor rowCursor) {
		this.rowCursor = rowCursor;
	}

	/** Keyboard steps through the list (REQ-UI-18, T2.9b): true if the key was used. */
	@FunctionalInterface
	public interface KeyHandler {
		boolean key(NavKey key);
	}

	/** Makes the list one stop of keyboard navigation, its keys going to {@code keys} (the sidebar's tabs). */
	public void setKeys(KeyHandler keys) {
		this.keys = keys;
	}

	/** The row the focus ring marks while the list has the keyboard focus (the selected tab); -1 for none. */
	public void setFocusRow(IntSupplier focusRow) {
		this.focusRow = focusRow;
	}

	@Override
	public boolean focusable() {
		return keys != null && count.getAsInt() > 0;
	}

	@Override
	public boolean navKey(NavKey key, boolean fine) {
		return enabled && keys != null && keys.key(key);
	}

	/** The focus row, 1 px inside the list (the rows touch its edges); the whole list when no row is marked. */
	@Override
	public void focusRing(int[] out) {
		int row = focusRow == null ? -1 : focusRow.getAsInt();
		if (row < 0 || row >= count.getAsInt()) {
			super.focusRing(out);
			return;
		}
		int top = Math.max(y, y + rows.rowTop(row));
		int bottom = Math.min(y + height, y + rows.rowTop(row) + rows.rowHeight());
		out[0] = x + 1;
		out[1] = top + 1;
		out[2] = Math.max(0, rowWidth() - 2);
		out[3] = Math.max(0, bottom - top - 2);
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
		smooth.setMax(rows.maxScroll());
	}

	private int rowWidth() {
		return rows.overflows() ? width - SCROLLBAR : width;
	}

	private boolean onScrollbar(double mouseX) {
		return rows.overflows() && ScrollbarPainter.inHitArea(x, width, mouseX);
	}

	/** The row under the mouse, or -1. */
	public int rowAt(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY) || onScrollbar(mouseX)) {
			return -1;
		}
		return rows.rowAt(mouseY - y);
	}

	/** The hand over its scrollbar and its rows (a click picks one), or what the rows ask for; the arrow below them. */
	@Override
	public PointerShape cursorAt(double mouseX, double mouseY) {
		if (onScrollbar(mouseX)) {
			return PointerShape.HAND;
		}
		// The rows as a press would find them now.
		rows.setCount(count.getAsInt());
		int row = rows.rowAt(mouseY - y);
		if (row < 0) {
			return PointerShape.DEFAULT;
		}
		return rowCursor == null ? PointerShape.HAND : rowCursor.at(row, mouseX - x, mouseY - y - rows.rowTop(row), rowWidth(), rows.rowHeight());
	}

	/** While its thumb is dragged, the vertical-resize pointer (REQ-UI-43). */
	@Override
	public PointerShape dragCursor() {
		return grab >= 0 ? PointerShape.RESIZE_VERTICAL : null;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		// The rows may have changed since the last frame (a search typed in between): hit-test the current ones.
		rows.setCount(count.getAsInt());
		// A glide stops where the rows were laid out (EC-UI-25), so the pressed row stays under the pointer.
		smooth.setMax(rows.maxScroll());
		if (rows.scroll() != smooth.target()) {
			smooth.jumpTo(rows.scroll());
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
				setScroll(rows.scrollForThumb(along - grab, height));
			}
			return true;
		}
		int row = rows.rowAt(mouseY - y);
		if (row >= 0 && rowClick != null) {
			rowClick.click(row, mouseX - x, mouseY - y - rows.rowTop(row), rowWidth(), rows.rowHeight());
		} else if (row >= 0) {
			onClick.accept(row);
		}
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (grab >= 0) {
			setScroll(rows.scrollForThumb((int) Math.floor(mouseY - y) - grab, height));
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
			smooth.glideBy(-notches * (fine ? 1 : WHEEL_ROWS) * rows.rowHeight());
		}
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
		int scale = Shapes.guiScale();
		smooth.setScale(scale);
		sync();
		int hoveredRow = enabled && grab < 0 ? rowAt(mouseX, mouseY) : -1;
		int rowWidth = rowWidth();
		try (Clip clip = Clip.push(graphics, x, y, width, height)) {
			if (!clip.visible()) {
				return;
			}
			VirtualRows.Range visible = rows.visible();
			lastDrawn = visible;
			// The rows are laid out at whole GUI px; the rest of the glide moves them on the physical-pixel grid (REQ-UI-34), so a
			// row beyond each end of the laid-out ones can show in part.
			float shift = drawShift(scale);
			int first = shift > 0 ? Math.max(0, visible.first() - 1) : visible.first();
			int end = shift < 0 ? Math.min(rows.count(), visible.end() + 1) : visible.end();
			graphics.pose().pushMatrix();
			try {
				graphics.pose().translate(0F, shift);
				for (int index = first; index < end; index++) {
					painter.draw(graphics, font, index, x, y + rows.rowTop(index), rowWidth, rows.rowHeight(), index == hoveredRow);
				}
			} finally {
				graphics.pose().popMatrix();
			}
			if (rows.overflows()) {
				VirtualRows.Thumb thumb = rows.thumb(height);
				boolean hot = enabled && (grab >= 0 || onScrollbar(mouseX) && contains(mouseX, mouseY));
				scrollbar.draw(graphics, x, y, width, height, thumb.offset(), thumb.length(), hot, this::faded);
			}
		}
	}

	/** The rows laid out in the last draw (a row beyond either end may be drawn in part too), for tests. */
	VirtualRows.Range laidOut() {
		return lastDrawn;
	}
}
