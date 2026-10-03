package com.k8bas.skyblockutility.render.marker;

import java.util.Objects;

/**
 * A horizontal ring around the marker's anchor: an outline, a filled disc, or both (REQ-MARK-01). The
 * outline is always drawn opaque so it stays readable; the colour's alpha applies to the disc, so a
 * ring with a disc needs a non-zero alpha. Always depth-tested.
 * <p>
 * On an entity anchor a ring is allowed only for a hologram whose name tag vanilla already displays,
 * such as a fishing hotspot (REQ-XC-RULES-04/05, R2): a ring reaches past the entity's outline, so
 * around any other entity behind a low wall it would show where the entity is. The provider checks that.
 *
 * @param radius in blocks
 */
public record MarkerRing(double radius, int argb, Style style) {
	public enum Style {
		OUTLINE, DISC, BOTH
	}

	public MarkerRing {
		Objects.requireNonNull(style, "style");
		if (!(radius > 0) || !Double.isFinite(radius)) {
			throw new IllegalArgumentException("a ring needs a positive, finite radius, got " + radius);
		}
		if (style != Style.OUTLINE && (argb >>> 24) == 0) {
			throw new IllegalArgumentException("a ring with a disc needs an alpha above 0, got " + Integer.toHexString(argb));
		}
	}
}
