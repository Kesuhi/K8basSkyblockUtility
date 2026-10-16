package com.k8bas.skyblockutility.ui.render;

import java.awt.image.BufferedImage;

/**
 * AC-UI-41 [C] (REQ-UI-40, T3.30), for gametests only: a shape's top-left corner radius read from a screenshot. Down the
 * shape's left edge column from its top, the first row that is more the shape's edge colour (taken on the straight part of
 * that edge) than the pixel just outside it lies, for a circle of radius R tangent to both edges, at
 * k(R) = ceil(R - 1/2 - sqrt(R^2 - (R - 1/2)^2)); the probe accepts the radii within 1 physical px of the expected one.
 */
public final class RadiusProbe {
	private RadiusProbe() {
	}

	/** The first row k whose pixel is nearer the edge colour than the outside, as written above. */
	public static int expectedRow(double radius) {
		if (radius <= 0.5) {
			return 0;
		}
		return (int) Math.ceil(radius - 0.5 - Math.sqrt(radius * radius - (radius - 0.5) * (radius - 0.5)) - 1e-9);
	}

	/**
	 * @param x the shape's left edge, physical px
	 * @param y its top edge, physical px
	 * @param height its height, physical px (the edge colour is read at its middle)
	 * @param radius the expected radius, physical px
	 * @return "" when the measured corner fits a radius within 1 physical px of {@code radius}, else what does not
	 */
	public static String check(BufferedImage image, String what, int x, int y, int height, int radius) {
		int edge = image.getRGB(x, y + height / 2);
		int row = 0;
		while (row < height / 2 && !nearer(image.getRGB(x, y + row), edge, image.getRGB(x - 1, y + row))) {
			row++;
		}
		for (int r = Math.max(0, radius - 1); r <= radius + 1; r++) {
			if (expectedRow(r) == row) {
				return "";
			}
		}
		return what + ": the corner's first covered row is " + row + ", which fits a radius of about " + radiusFor(row) + " px, not " + radius
				+ " (row " + expectedRow(radius) + "); ";
	}

	/**
	 * As {@link #check}, along the shape's top edge row from its left end instead: for a shape whose left edge carries a mark
	 * (a selected tab's accent bar). The edge colour is read at the top edge's middle, the outside just above.
	 */
	public static String checkTop(BufferedImage image, String what, int x, int y, int width, int radius) {
		int edge = image.getRGB(x + width / 2, y);
		int column = 0;
		while (column < width / 2 && !nearer(image.getRGB(x + column, y), edge, image.getRGB(x + column, y - 1))) {
			column++;
		}
		for (int r = Math.max(0, radius - 1); r <= radius + 1; r++) {
			if (expectedRow(r) == column) {
				return "";
			}
		}
		return what + ": the corner's first covered column is " + column + ", which fits a radius of about " + radiusFor(column) + " px, not "
				+ radius + " (column " + expectedRow(radius) + "); ";
	}

	/**
	 * As {@link #check} for any corner: from the corner pixel (x, y) of the shape, down ({@code down}) or up its edge column,
	 * which lies on the shape's left ({@code left}) or right; {@code edge} is the shape's colour on that edge.
	 */
	public static String corner(BufferedImage image, String what, int x, int y, boolean left, boolean down, int length, int edge, int radius) {
		int outsideX = left ? x - 1 : x + 1;
		int step = down ? 1 : -1;
		int row = 0;
		while (row < length && !nearer(image.getRGB(x, y + step * row), edge, image.getRGB(outsideX, y + step * row))) {
			row++;
		}
		for (int r = Math.max(0, radius - 1); r <= radius + 1; r++) {
			if (expectedRow(r) == row) {
				return "";
			}
		}
		return what + ": the corner's first covered row is " + row + ", which fits a radius of about " + radiusFor(row) + " px, not " + radius
				+ " (row " + expectedRow(radius) + "); ";
	}

	private static int radiusFor(int row) {
		int r = 0;
		while (expectedRow(r + 1) <= row && r < 200) {
			r++;
		}
		return r;
	}

	private static boolean nearer(int pixel, int edge, int outside) {
		return distance(pixel, edge) < distance(pixel, outside);
	}

	private static int distance(int a, int b) {
		int d = 0;
		for (int shift = 0; shift <= 16; shift += 8) {
			d += Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF));
		}
		return d;
	}
}
