package com.k8bas.skyblockutility.hud

import kotlin.math.floor

/** A rectangle in GUI pixels. */
@JvmRecord
data class HudRect(val x: Double, val y: Double, val w: Double, val h: Double) {
	override fun toString(): String = "HudRect[x=$x, y=$y, w=$w, h=$h]"

	companion object {
		/**
		 * Where it is drawn (REQ-HUD-04): on whole GUI pixels, so text stays sharp, and fully inside the
		 * window, a fractional size included; in an axis where it is larger than the window, from the window's
		 * left or top edge. The stored position is never changed by this.
		 */
		@JvmStatic
		fun onPixels(rect: HudRect, screenW: Double, screenH: Double): HudRect =
			HudRect(pixel(rect.x, rect.w, screenW), pixel(rect.y, rect.h, screenH), rect.w, rect.h)

		/** The pixel [at] is drawn on, held inside the screen. */
		private fun pixel(at: Double, size: Double, screen: Double): Double =
			if (size >= screen) 0.0 else maxOf(0.0, minOf(Math.round(at).toDouble(), floor(screen - size)))
	}
}
