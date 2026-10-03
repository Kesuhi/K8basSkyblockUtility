package com.k8bas.skyblockutility.ui.render;

/**
 * A clip region in GUI pixels whose size is never negative (REQ-UI-02): a region squeezed to nothing
 * by a tiny window is empty, so it draws nothing instead of failing.
 */
public record ClipRect(int x, int y, int width, int height) {
	public static ClipRect of(int x, int y, int width, int height) {
		return new ClipRect(x, y, Math.max(0, width), Math.max(0, height));
	}

	public boolean isEmpty() {
		return width == 0 || height == 0;
	}

	/** The right edge (exclusive), saturating instead of overflowing. */
	public int right() {
		return (int) Math.min(Integer.MAX_VALUE, (long) x + width);
	}

	/** The bottom edge (exclusive), saturating instead of overflowing. */
	public int bottom() {
		return (int) Math.min(Integer.MAX_VALUE, (long) y + height);
	}
}
