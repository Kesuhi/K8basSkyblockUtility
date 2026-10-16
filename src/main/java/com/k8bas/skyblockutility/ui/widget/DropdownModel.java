package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiTime;

/**
 * An open dropdown list: which entry is highlighted and which rows are in view. The scroll is a whole row; the list
 * glides there (REQ-UI-35, {@link SmoothScroll}, in rows), laid out at whole GUI px and drawn on the physical-pixel grid.
 */
public final class DropdownModel {
	private final int count;
	private final SmoothScroll smooth;
	private int visible;
	private int highlighted;
	private int scroll;

	public DropdownModel(int count, int visible) {
		this(count, visible, UiClock.SOURCE);
	}

	public DropdownModel(int count, int visible, UiTime time) {
		this.count = count;
		this.visible = Math.max(1, visible);
		this.smooth = new SmoothScroll(time);
		smooth.setMax(maxScroll());
	}

	/**
	 * The rows the placement left room for (called every frame). When that number changes, the
	 * highlighted entry is brought back into view; otherwise the scroll stays where the wheel put it.
	 */
	public void setVisible(int visible) {
		int rows = Math.max(1, visible);
		if (rows != this.visible) {
			this.visible = rows;
			keepInView();
			smooth.setMax(maxScroll());
			smooth.jumpTo(scroll);
		}
	}

	/** Opens on the current entry, scrolled into view at once. */
	public void open(int current) {
		highlighted = Math.max(0, Math.min(count - 1, current));
		scroll = 0;
		keepInView();
		smooth.jumpTo(scroll);
	}

	/** The arrow keys: the highlight moves, and the list glides to keep it in view. */
	public void move(int delta) {
		highlighted = Math.max(0, Math.min(count - 1, highlighted + delta));
		keepInView();
		smooth.glideTo(scroll);
	}

	/** The wheel: glides by whole rows. */
	public void scrollBy(int rows) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll + rows));
		smooth.glideTo(scroll);
	}

	/** Shows the list from row {@code rows} at once: its scrollbar's thumb dragged or its track pressed. */
	public void scrollTo(int rows) {
		scroll = Math.max(0, Math.min(maxScroll(), rows));
		smooth.jumpTo(scroll);
	}

	/** The entry at a y inside the list, or -1: where it is laid out now, at whole GUI px. */
	public int rowAt(double localY, int rowHeight) {
		if (localY < 0 || localY >= visible * rowHeight) {
			return -1;
		}
		int index = (int) Math.floor((localY + laidOutPx(rowHeight)) / rowHeight);
		return index < count ? index : -1;
	}

	/** The scroll the rows are laid out at now, in whole GUI px. */
	public int laidOutPx(int rowHeight) {
		return Math.round(smooth.shown() * rowHeight);
	}

	/** The scroll to draw the rows at, on the physical-pixel grid at GUI scale {@code scale}, within 1 GUI px of {@link #laidOutPx}. */
	public float drawnPx(int rowHeight, int scale) {
		float shown = smooth.shown() * rowHeight;
		float target = (float) scroll * rowHeight;
		return Math.abs(shown - target) < 0.5F / scale ? target : Shapes.snap(shown, scale);
	}

	/** How far the rows are drawn below where they are laid out now, under 1 GUI px, on the physical-pixel grid at {@code scale}. */
	public float drawShift(int rowHeight, int scale) {
		return laidOutPx(rowHeight) - drawnPx(rowHeight, scale);
	}

	/** The scroll shown now, in rows (the thumb's place). */
	public float shownRows() {
		return smooth.shown();
	}

	/** The GUI scale the list is drawn at; a change puts it at its scroll at once (EC-UI-18). */
	public void setScale(int scale) {
		smooth.setScale(scale);
	}

	public int highlighted() {
		return highlighted;
	}

	public void highlight(int index) {
		if (index >= 0 && index < count) {
			highlighted = index;
		}
	}

	public int scroll() {
		return scroll;
	}

	public int count() {
		return count;
	}

	private int maxScroll() {
		return Math.max(0, count - visible);
	}

	private void keepInView() {
		if (highlighted < scroll) {
			scroll = highlighted;
		} else if (highlighted >= scroll + visible) {
			scroll = highlighted - visible + 1;
		}
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
	}
}
