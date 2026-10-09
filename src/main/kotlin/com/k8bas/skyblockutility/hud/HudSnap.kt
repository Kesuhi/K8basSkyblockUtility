package com.k8bas.skyblockutility.hud

import kotlin.math.abs
import kotlin.math.floor

/**
 * Snapping in the HUD editor (REQ-HUD-14, T2.9c), pure: while an element is dragged, its edges and centre snap
 * within 4 px to the screen's centre lines, its edges to the screen's edges and to the other elements' edges; each
 * axis on its own, the nearest target winning (ties: the screen centre, then the screen edges, then the elements in
 * order). Works on the drawn rectangles (scaled, on whole pixels), so a snapped place is what is drawn and saved.
 */
object HudSnap {
	const val THRESHOLD: Double = 4.0

	/** How close a line must lie to a target for its guide to show (an odd width centres half a pixel off). */
	private const val ON_TARGET = 0.5 + 1e-9

	/** A 1 px guide line: a column at [at] when [vertical] (full height), else a row. */
	@JvmRecord
	data class Guide(val vertical: Boolean, val at: Int) {
		override fun toString(): String = "Guide[vertical=$vertical, at=$at]"
	}

	/** [rect] with each axis moved by the nearest target within [threshold], on a whole pixel, inside the screen. */
	@JvmStatic
	@JvmOverloads
	fun snap(rect: HudRect, screenW: Double, screenH: Double, others: List<HudRect>, threshold: Double = THRESHOLD): HudRect = HudRect(
		snapAxis(rect.x, rect.w, screenW, others, true, threshold),
		snapAxis(rect.y, rect.h, screenH, others, false, threshold),
		rect.w,
		rect.h,
	)

	/** One guide per target pixel [rect] lies on: vertical ones first, each column or row once. Asked once per drag event. */
	@JvmStatic
	fun guides(rect: HudRect, screenW: Double, screenH: Double, others: List<HudRect>): List<Guide> {
		val out = ArrayList<Guide>()
		axisGuides(out, rect.x, rect.w, screenW, others, true)
		axisGuides(out, rect.y, rect.h, screenH, others, false)
		return out
	}

	private fun snapAxis(start: Double, size: Double, screen: Double, others: List<HudRect>, horizontal: Boolean, threshold: Double): Double {
		// An axis larger than the screen is drawn at its edge (HudRect.onPixels): nothing to snap.
		if (size >= screen) {
			return start
		}
		var best = Double.MAX_VALUE
		var move = 0.0
		forEachPair(start, size, screen, others, horizontal) { line, target ->
			val distance = abs(target - line)
			if (distance <= threshold && distance < best) {
				best = distance
				move = target - line
			}
		}
		if (best == Double.MAX_VALUE) {
			return start
		}
		return maxOf(0.0, minOf(Math.round(start + move).toDouble(), floor(screen - size)))
	}

	private fun axisGuides(out: MutableList<Guide>, start: Double, size: Double, screen: Double, others: List<HudRect>, horizontal: Boolean) {
		if (size >= screen) {
			return
		}
		forEachPair(start, size, screen, others, horizontal) { line, target ->
			if (abs(target - line) <= ON_TARGET) {
				val guide = Guide(horizontal, floor(target).toInt().coerceIn(0, screen.toInt() - 1))
				if (guide !in out) {
					out.add(guide)
				}
			}
		}
	}

	/** Every (line of the element, target) pair, in rank order: the screen centre, the screen edges, then the elements. */
	private inline fun forEachPair(start: Double, size: Double, screen: Double, others: List<HudRect>, horizontal: Boolean, pair: (Double, Double) -> Unit) {
		val low = start
		val mid = start + size / 2
		val high = start + size
		val centre = screen / 2
		pair(low, centre)
		pair(mid, centre)
		pair(high, centre)
		pair(low, 0.0)
		pair(high, screen)
		for (other in others) {
			val otherLow = if (horizontal) other.x else other.y
			val otherHigh = otherLow + if (horizontal) other.w else other.h
			pair(low, otherLow)
			pair(low, otherHigh)
			pair(high, otherLow)
			pair(high, otherHigh)
		}
	}
}
