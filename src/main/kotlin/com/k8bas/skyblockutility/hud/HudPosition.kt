package com.k8bas.skyblockutility.hud

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Where an element is (REQ-HUD-03): an anchor, an offset in GUI pixels from it, and a scale. The offset
 * moves the element's own anchor point (its right edge for a right anchor) away from the screen's, so an
 * element grows away from its anchor and keeps its place when the window or GUI scale changes. Pure.
 *
 * A class rather than a data class: the scale is held to its range on construction, which a data class's
 * generated `copy` would skip. Java sees the record-style accessors `anchor()`, `x()`, `y()`, `scale()`.
 */
class HudPosition(
	@get:JvmName("anchor") val anchor: HudAnchor,
	@get:JvmName("x") val x: Int,
	@get:JvmName("y") val y: Int,
	scale: Double,
) {
	@get:JvmName("scale")
	val scale: Double = HudScale.clamp(scale)

	/** The element's rectangle for its scaled size [w]×[h] on a [screenW]×[screenH] screen. */
	fun place(screenW: Double, screenH: Double, w: Double, h: Double): HudRect =
		HudRect(anchor.fx() * (screenW - w) + x, anchor.fy() * (screenH - h) + y, w, h)

	/**
	 * After a drag (REQ-HUD-03): anchored to the point of the screen third, in each axis, that holds the
	 * element's centre, with the offset that keeps it on the pixel it is drawn on ([HudRect.onPixels]).
	 */
	fun reanchored(screenW: Double, screenH: Double, w: Double, h: Double): HudPosition {
		val rect = place(screenW, screenH, w, h)
		val drawn = HudRect.onPixels(rect, screenW, screenH)
		val to = HudAnchor.of(third(rect.x + w / 2, screenW), third(rect.y + h / 2, screenH))
		return HudPosition(to, offsetFor(drawn.x, to.fx() * (screenW - w)), offsetFor(drawn.y, to.fy() * (screenH - h)), scale)
	}

	/** The same anchor and scale, with the offset that draws it with its top-left on pixel ([left], [top]), rounded. */
	fun movedTo(left: Double, top: Double, screenW: Double, screenH: Double, w: Double, h: Double): HudPosition = HudPosition(
		anchor,
		offsetFor(Math.round(left).toDouble(), anchor.fx() * (screenW - w)),
		offsetFor(Math.round(top).toDouble(), anchor.fy() * (screenH - h)),
		scale,
	)

	fun withScale(newScale: Double): HudPosition = HudPosition(anchor, x, y, newScale)

	override fun equals(other: Any?): Boolean =
		other is HudPosition && anchor == other.anchor && x == other.x && y == other.y && java.lang.Double.compare(scale, other.scale) == 0

	override fun hashCode(): Int = ((anchor.hashCode() * 31 + x) * 31 + y) * 31 + scale.hashCode()

	override fun toString(): String = "HudPosition[anchor=$anchor, x=$x, y=$y, scale=$scale]"

	private companion object {
		/** The whole offset from [base] that rounds to [pixel], as [HudRect.onPixels] rounds. */
		fun offsetFor(pixel: Double, base: Double): Int = ceil(pixel - 0.5 - base).toInt()

		fun third(centre: Double, screen: Double): Int = floor(centre * 3 / screen).toInt().coerceIn(0, 2)
	}
}
