package com.k8bas.skyblockutility.hud;

/**
 * Where an element is (REQ-HUD-03): an anchor, an offset in GUI pixels from it, and a scale. The offset
 * moves the element's own anchor point (its right edge for a right anchor) away from the screen's, so an
 * element grows away from its anchor and keeps its place when the window or GUI scale changes. Pure.
 */
public record HudPosition(HudAnchor anchor, int x, int y, double scale) {
	public HudPosition {
		scale = HudScale.clamp(scale);
	}

	/** The element's rectangle for its scaled size {@code w}×{@code h} on a {@code screenW}×{@code screenH} screen. */
	public HudRect place(double screenW, double screenH, double w, double h) {
		return new HudRect(anchor.fx() * (screenW - w) + x, anchor.fy() * (screenH - h) + y, w, h);
	}

	/**
	 * After a drag (REQ-HUD-03): anchored to the point of the screen third, in each axis, that holds the
	 * element's centre, with the offset that keeps it on the pixel it is drawn on ({@link HudRect#onPixels}).
	 */
	public HudPosition reanchored(double screenW, double screenH, double w, double h) {
		HudRect rect = place(screenW, screenH, w, h);
		HudRect drawn = HudRect.onPixels(rect, screenW, screenH);
		HudAnchor to = HudAnchor.of(third(rect.x() + w / 2, screenW), third(rect.y() + h / 2, screenH));
		return new HudPosition(to, offsetFor(drawn.x(), to.fx() * (screenW - w)), offsetFor(drawn.y(), to.fy() * (screenH - h)), scale);
	}

	/** The whole offset from {@code base} that rounds to {@code pixel}, as {@link HudRect#onPixels} rounds. */
	private static int offsetFor(double pixel, double base) {
		return (int) Math.ceil(pixel - 0.5 - base);
	}

	public HudPosition withScale(double newScale) {
		return new HudPosition(anchor, x, y, newScale);
	}

	private static int third(double centre, double screen) {
		return Math.max(0, Math.min(2, (int) Math.floor(centre * 3 / screen)));
	}
}
