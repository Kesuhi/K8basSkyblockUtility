package com.k8bas.skyblockutility.render.marker

import net.minecraft.network.chat.Component

/**
 * An in-world text label: 1–3 lines, each with its own colour, counting the optional distance line,
 * and an optional background (REQ-MARK-01). It always faces the camera. See-through and the distance
 * line are requests; [Marker] drops both for a marker that is not at fixed coordinates.
 *
 * A class rather than a data class: the lines are copied on construction, and a public copy() would be a
 * second way round the checks. Java sees the record-style accessors `lines()`, `backgroundColor()`, ...
 *
 * @param backgroundColor ARGB of the plate behind each line, 0 for none
 * @param distanceColor   colour of the distance line, used when [distanceLine] is set
 * @param rise            how far above the marker's anchor the label sits, in blocks; the distance is
 *                        measured to the label (REQ-NPCWP-03), while a beam rises from the anchor
 */
class MarkerLabel(
	lines: List<Line>,
	@get:JvmName("backgroundColor") val backgroundColor: Int,
	@get:JvmName("seeThrough") val seeThrough: Boolean,
	@get:JvmName("distanceLine") val distanceLine: Boolean,
	@get:JvmName("distanceColor") val distanceColor: Int,
	@get:JvmName("rise") val rise: Double,
) {
	@get:JvmName("lines")
	val lines: List<Line> = java.util.List.copyOf(lines)

	init {
		val count = this.lines.size + (if (distanceLine) 1 else 0)
		require(this.lines.isNotEmpty() && count <= MAX_LINES) { "a label has 1 to $MAX_LINES lines, got $count" }
	}

	/** One line of text in an RGB or ARGB colour (drawn opaque). */
	@JvmRecord
	data class Line(val text: Component, val color: Int) {
		override fun toString(): String = "Line[text=$text, color=$color]"
	}

	fun withBackground(argb: Int): MarkerLabel = MarkerLabel(lines, argb, seeThrough, distanceLine, distanceColor, rise)

	/** Asks for the label to show through blocks; only kept on a fixed anchor (REQ-MARK-02). */
	fun asSeeThrough(): MarkerLabel = MarkerLabel(lines, backgroundColor, true, distanceLine, distanceColor, rise)

	/** Adds a last line with the distance from the player; only kept on a fixed anchor (REQ-MARK-05). */
	fun withDistance(color: Int): MarkerLabel = MarkerLabel(lines, backgroundColor, seeThrough, true, color, rise)

	/** Draws the label this many blocks above the marker's anchor. */
	fun raisedBy(blocks: Double): MarkerLabel = MarkerLabel(lines, backgroundColor, seeThrough, distanceLine, distanceColor, blocks)

	/** The lines drawn, including the distance line. */
	fun lineCount(): Int = lines.size + (if (distanceLine) 1 else 0)

	/** The same label, depth-tested and without a distance line. */
	internal fun depthTested(): MarkerLabel = MarkerLabel(lines, backgroundColor, false, false, distanceColor, rise)

	/** As the record's: the double compared and hashed by its bits (`Double.compare`). */
	override fun equals(other: Any?): Boolean = other is MarkerLabel && lines == other.lines && backgroundColor == other.backgroundColor &&
		seeThrough == other.seeThrough && distanceLine == other.distanceLine && distanceColor == other.distanceColor &&
		java.lang.Double.compare(rise, other.rise) == 0

	override fun hashCode(): Int =
		((((lines.hashCode() * 31 + backgroundColor) * 31 + seeThrough.hashCode()) * 31 + distanceLine.hashCode()) * 31 + distanceColor) * 31 +
			java.lang.Double.hashCode(rise)

	override fun toString(): String = "MarkerLabel[lines=$lines, backgroundColor=$backgroundColor, seeThrough=$seeThrough, " +
		"distanceLine=$distanceLine, distanceColor=$distanceColor, rise=$rise]"

	companion object {
		const val MAX_LINES: Int = 3

		@JvmStatic
		fun of(vararg lines: Line): MarkerLabel = MarkerLabel(java.util.List.of(*lines), 0, false, false, 0, 0.0)
	}
}
