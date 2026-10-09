package com.k8bas.skyblockutility.ui.widget

/**
 * Where a dropdown's list goes (REQ-UI-06): under its box if all rows fit, otherwise on the side with
 * more room, never off screen, at most `maxRows` rows. It is drawn over the cards either way.
 */
object DropdownPlacement {
	@JvmRecord
	data class Placement(val x: Int, val y: Int, val width: Int, val height: Int, val rows: Int, val upward: Boolean) {
		override fun toString(): String = "Placement[x=$x, y=$y, width=$width, height=$height, rows=$rows, upward=$upward]"
	}

	@JvmStatic
	fun place(
		anchorX: Int,
		anchorY: Int,
		anchorWidth: Int,
		anchorHeight: Int,
		count: Int,
		rowHeight: Int,
		maxRows: Int,
		screenWidth: Int,
		screenHeight: Int,
		margin: Int,
	): Placement {
		val wanted = maxOf(1, minOf(count, maxRows))
		val below = screenHeight - margin - (anchorY + anchorHeight)
		val above = anchorY - margin
		val upward = wanted * rowHeight > below && above > below
		val space = if (upward) above else below
		val rows = maxOf(1, minOf(wanted, space / rowHeight))
		val height = rows * rowHeight
		val width = minOf(anchorWidth, screenWidth - 2 * margin)
		val x = maxOf(margin, minOf(anchorX, screenWidth - margin - width))
		val y = (if (upward) anchorY - height else anchorY + anchorHeight).let { maxOf(margin, minOf(it, screenHeight - margin - height)) }
		return Placement(x, y, width, height, rows, upward)
	}
}
