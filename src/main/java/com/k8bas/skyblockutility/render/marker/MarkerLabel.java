package com.k8bas.skyblockutility.render.marker;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * An in-world text label: 1–3 lines, each with its own colour, counting the optional distance line,
 * and an optional background (REQ-MARK-01). It always faces the camera. See-through and the distance
 * line are requests; {@link Marker} drops both for a marker that is not at fixed coordinates.
 *
 * @param backgroundColor ARGB of the plate behind each line, 0 for none
 * @param distanceColor   colour of the distance line, used when {@code distanceLine} is set
 * @param rise            how far above the marker's anchor the label sits, in blocks; the distance is
 *                        measured to the label (REQ-NPCWP-03), while a beam rises from the anchor
 */
public record MarkerLabel(List<Line> lines, int backgroundColor, boolean seeThrough, boolean distanceLine, int distanceColor, double rise) {
	public static final int MAX_LINES = 3;

	/** One line of text in an RGB or ARGB colour (drawn opaque). */
	public record Line(Component text, int color) {
		public Line {
			Objects.requireNonNull(text, "text");
		}
	}

	public MarkerLabel {
		lines = List.copyOf(lines);
		int count = lines.size() + (distanceLine ? 1 : 0);
		if (lines.isEmpty() || count > MAX_LINES) {
			throw new IllegalArgumentException("a label has 1 to " + MAX_LINES + " lines, got " + count);
		}
	}

	public static MarkerLabel of(Line... lines) {
		return new MarkerLabel(List.of(lines), 0, false, false, 0, 0);
	}

	public MarkerLabel withBackground(int argb) {
		return new MarkerLabel(lines, argb, seeThrough, distanceLine, distanceColor, rise);
	}

	/** Asks for the label to show through blocks; only kept on a fixed anchor (REQ-MARK-02). */
	public MarkerLabel asSeeThrough() {
		return new MarkerLabel(lines, backgroundColor, true, distanceLine, distanceColor, rise);
	}

	/** Adds a last line with the distance from the player; only kept on a fixed anchor (REQ-MARK-05). */
	public MarkerLabel withDistance(int color) {
		return new MarkerLabel(lines, backgroundColor, seeThrough, true, color, rise);
	}

	/** Draws the label this many blocks above the marker's anchor. */
	public MarkerLabel raisedBy(double blocks) {
		return new MarkerLabel(lines, backgroundColor, seeThrough, distanceLine, distanceColor, blocks);
	}

	/** The lines drawn, including the distance line. */
	public int lineCount() {
		return lines.size() + (distanceLine ? 1 : 0);
	}

	/** The same label, depth-tested and without a distance line. */
	MarkerLabel depthTested() {
		return new MarkerLabel(lines, backgroundColor, false, false, distanceColor, rise);
	}
}
