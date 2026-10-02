package com.k8bas.skyblockutility.render.marker;

import java.util.Objects;

/**
 * One world marker: an anchor and what is drawn there. This is where the see-through policy is
 * enforced (REQ-MARK-02): a marker that is not at fixed coordinates never shows through blocks and
 * never shows a distance line, whatever its label asks for. Beams and rings join it in T3.0n.
 *
 * @param label the label, or null for none
 */
public record Marker(MarkerAnchor anchor, MarkerLabel label) {
	public Marker {
		Objects.requireNonNull(anchor, "anchor");
		if (label != null && !anchor.isFixed() && (label.seeThrough() || label.distanceLine())) {
			label = label.depthTested();
		}
	}
}
