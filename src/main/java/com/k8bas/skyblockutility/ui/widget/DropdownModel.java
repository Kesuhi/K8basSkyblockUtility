package com.k8bas.skyblockutility.ui.widget;

/** An open dropdown list: which entry is highlighted and which rows are in view. */
public final class DropdownModel {
	private final int count;
	private int visible;
	private int highlighted;
	private int scroll;

	public DropdownModel(int count, int visible) {
		this.count = count;
		this.visible = Math.max(1, visible);
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
		}
	}

	/** Opens on the current entry, scrolled into view. */
	public void open(int current) {
		highlighted = Math.max(0, Math.min(count - 1, current));
		scroll = 0;
		keepInView();
	}

	public void move(int delta) {
		highlighted = Math.max(0, Math.min(count - 1, highlighted + delta));
		keepInView();
	}

	public void scrollBy(int rows) {
		scroll = Math.max(0, Math.min(Math.max(0, count - visible), scroll + rows));
	}

	/** The entry at a y inside the list, or -1. */
	public int rowAt(double localY, int rowHeight) {
		if (localY < 0) {
			return -1;
		}
		int row = (int) (localY / rowHeight);
		if (row >= visible) {
			return -1;
		}
		int index = scroll + row;
		return index < count ? index : -1;
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

	private void keepInView() {
		if (highlighted < scroll) {
			scroll = highlighted;
		} else if (highlighted >= scroll + visible) {
			scroll = highlighted - visible + 1;
		}
		scrollBy(0);
	}
}
