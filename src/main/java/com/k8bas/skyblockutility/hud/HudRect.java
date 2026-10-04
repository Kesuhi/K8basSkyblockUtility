package com.k8bas.skyblockutility.hud;

/** A rectangle in GUI pixels. */
public record HudRect(double x, double y, double w, double h) {
	/**
	 * Where it is drawn (REQ-HUD-04): on whole GUI pixels, so text stays sharp, and fully inside the
	 * window, a fractional size included; in an axis where it is larger than the window, from the window's
	 * left or top edge. The stored position is never changed by this.
	 */
	public static HudRect onPixels(HudRect rect, double screenW, double screenH) {
		return new HudRect(pixel(rect.x, rect.w, screenW), pixel(rect.y, rect.h, screenH), rect.w, rect.h);
	}

	/** The pixel {@code at} is drawn on, held inside the screen. */
	static double pixel(double at, double size, double screen) {
		return size >= screen ? 0 : Math.max(0, Math.min(Math.round(at), Math.floor(screen - size)));
	}
}
