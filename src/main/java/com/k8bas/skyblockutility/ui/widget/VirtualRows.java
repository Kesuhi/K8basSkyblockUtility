package com.k8bas.skyblockutility.ui.widget;

/**
 * The rows of a long list that a viewport shows (REQ-UI-20): which ones to lay out, which one a y
 * position hits, and the scroll thumb. Everything is arithmetic on the scroll offset, so the cost does
 * not grow with the row count. Positions are in GUI pixels from the viewport's top.
 */
public final class VirtualRows {
	/** The shortest thumb, so a list of thousands of rows still has one to grab. */
	public static final int MIN_THUMB = 12;

	private final int rowHeight;
	private int count;
	private int viewport;
	private int scroll;

	/** Rows {@code first} (inclusive) to {@code end} (exclusive). */
	public record Range(int first, int end) {
		public int count() {
			return end - first;
		}
	}

	/** The thumb's offset along the track and its length. */
	public record Thumb(int offset, int length) {
	}

	public VirtualRows(int rowHeight) {
		if (rowHeight <= 0) {
			throw new IllegalArgumentException("rowHeight " + rowHeight);
		}
		this.rowHeight = rowHeight;
	}

	public int rowHeight() {
		return rowHeight;
	}

	public int count() {
		return count;
	}

	public void setCount(int count) {
		this.count = Math.max(0, count);
		setScroll(scroll);
	}

	public void setViewport(int viewport) {
		this.viewport = Math.max(0, viewport);
		setScroll(scroll);
	}

	public int scroll() {
		return scroll;
	}

	/** The scroll in pixels, held between 0 and {@link #maxScroll()}. */
	public void setScroll(int scroll) {
		this.scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	public int maxScroll() {
		return (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) count * rowHeight - viewport));
	}

	public boolean overflows() {
		return maxScroll() > 0;
	}

	/** The rows in view, at most ceil(viewport / rowHeight) + 1. */
	public Range visible() {
		if (count == 0 || viewport == 0) {
			return new Range(0, 0);
		}
		int first = scroll / rowHeight;
		int end = Math.min(count, (scroll + viewport - 1) / rowHeight + 1);
		return new Range(Math.min(first, end), end);
	}

	/** The row's top, from the viewport's top (negative when cut off above). */
	public int rowTop(int index) {
		return index * rowHeight - scroll;
	}

	/** The row at a y position in the viewport, or -1 outside the viewport or below the last row. */
	public int rowAt(double y) {
		if (y < 0 || y >= viewport) {
			return -1;
		}
		long index = ((long) scroll + (long) Math.floor(y)) / rowHeight;
		return index < count ? (int) index : -1;
	}

	/** Scrolls as little as needed to show the whole row. */
	public void ensureVisible(int index) {
		if (index < 0 || index >= count) {
			return;
		}
		int top = index * rowHeight;
		if (top < scroll) {
			setScroll(top);
		} else if (top + rowHeight > scroll + viewport) {
			setScroll(top + rowHeight - viewport);
		}
	}

	public void scrollRows(int rows) {
		setScroll((int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) scroll + (long) rows * rowHeight)));
	}

	/** The thumb on a track of the given length: as long as the share in view, at least {@link #MIN_THUMB}. */
	public Thumb thumb(int track) {
		int max = maxScroll();
		if (max == 0) {
			return new Thumb(0, track);
		}
		long content = (long) count * rowHeight;
		int length = (int) Math.min(track, Math.max(MIN_THUMB, (long) track * viewport / content));
		int offset = (int) ((long) (track - length) * scroll / max);
		return new Thumb(offset, length);
	}

	/** The scroll for the thumb at an offset along the track (held to the track). */
	public int scrollForThumb(int offset, int track) {
		int room = track - thumb(track).length();
		if (room <= 0) {
			return 0;
		}
		int clamped = Math.max(0, Math.min(offset, room));
		return (int) Math.round((double) clamped * maxScroll() / room);
	}
}
