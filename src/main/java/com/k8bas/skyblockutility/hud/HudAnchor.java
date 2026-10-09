package com.k8bas.skyblockutility.hud;

/**
 * The 9 screen points an element is placed against (REQ-HUD-03): the corners, the edge midpoints and the
 * centre. {@code fx}/{@code fy} are where the point lies across the screen (0, ½ or 1), and also which
 * point of the element sits on it, so an element grows away from its anchor.
 */
public enum HudAnchor {
	TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
	CENTER_LEFT(0, 1), CENTER(1, 1), CENTER_RIGHT(2, 1),
	BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

	private final int col;
	private final int row;

	HudAnchor(int col, int row) {
		this.col = col;
		this.row = row;
	}

	public double fx() {
		return col / 2.0;
	}

	public double fy() {
		return row / 2.0;
	}

	/** The anchor of screen third ({@code col}, {@code row}), each 0-2. */
	public static HudAnchor of(int col, int row) {
		return values()[row * 3 + col];
	}

	/** The anchor with this stored name, or null. */
	public static HudAnchor named(String name) {
		for (HudAnchor anchor : values()) {
			if (anchor.name().equals(name)) {
				return anchor;
			}
		}
		return null;
	}
}
