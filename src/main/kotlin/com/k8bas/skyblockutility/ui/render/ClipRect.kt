package com.k8bas.skyblockutility.ui.render

/**
 * A clip region in GUI pixels whose size is never negative (REQ-UI-02): a region squeezed to nothing
 * by a tiny window is empty, so it draws nothing instead of failing.
 */
@JvmRecord
data class ClipRect(val x: Int, val y: Int, val width: Int, val height: Int) {
	fun isEmpty(): Boolean = width == 0 || height == 0

	/** The right edge (exclusive), saturating instead of overflowing. */
	fun right(): Int = minOf(Int.MAX_VALUE.toLong(), x.toLong() + width).toInt()

	/** The bottom edge (exclusive), saturating instead of overflowing. */
	fun bottom(): Int = minOf(Int.MAX_VALUE.toLong(), y.toLong() + height).toInt()

	fun contains(px: Double, py: Double): Boolean = px >= x && py >= y && px < right() && py < bottom()

	/** Whether a rectangle shares at least one pixel with this region. */
	fun overlaps(ox: Int, oy: Int, ow: Int, oh: Int): Boolean =
		ow > 0 && oh > 0 && ox < right() && oy < bottom() && ox.toLong() + ow > x && oy.toLong() + oh > y

	override fun toString(): String = "ClipRect[x=$x, y=$y, width=$width, height=$height]"

	companion object {
		@JvmStatic
		fun of(x: Int, y: Int, width: Int, height: Int): ClipRect = ClipRect(x, y, maxOf(0, width), maxOf(0, height))
	}
}
