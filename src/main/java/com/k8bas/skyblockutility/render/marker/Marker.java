package com.k8bas.skyblockutility.render.marker;

import java.util.Objects;

/**
 * One world marker: an anchor and what is drawn there, a label, a beacon beam and a ring, each
 * optional. This is where the see-through policy is enforced (REQ-MARK-02): a marker that is not at
 * fixed coordinates never shows through blocks, never shows a distance line and never gets a beam
 * (a beam rising from a hidden entity would show where it is, above every wall), whatever it asks for.
 * Rings are always depth-tested and flat, so they stay.
 *
 * @param label the label, or null for none
 * @param beam  the beam, or null for none
 * @param ring  the ring, or null for none
 */
public record Marker(MarkerAnchor anchor, MarkerLabel label, MarkerBeam beam, MarkerRing ring) {
	public Marker {
		Objects.requireNonNull(anchor, "anchor");
		if (!anchor.isFixed()) {
			if (label != null && (label.seeThrough() || label.distanceLine())) {
				label = label.depthTested();
			}
			beam = null;
		}
	}

	/** A marker with only a label. */
	public Marker(MarkerAnchor anchor, MarkerLabel label) {
		this(anchor, label, null, null);
	}
}
