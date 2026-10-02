package com.k8bas.skyblockutility.render.marker;

/**
 * A horizontal ring around the marker's anchor: an outline, a filled disc, or both, in an ARGB colour
 * (REQ-MARK-01). Always depth-tested, also on an entity-derived anchor such as a hotspot (R2).
 *
 * @param radius in blocks
 */
public record MarkerRing(double radius, int argb, Style style) {
	public enum Style {
		OUTLINE, DISC, BOTH
	}

	public MarkerRing {
		if (!(radius > 0)) {
			throw new IllegalArgumentException("a ring needs a positive radius, got " + radius);
		}
	}
}
