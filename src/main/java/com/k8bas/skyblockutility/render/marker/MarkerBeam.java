package com.k8bas.skyblockutility.render.marker;

import net.minecraft.util.ARGB;

/**
 * A beacon beam rising from the marker block to the build height, animated like a vanilla beacon,
 * wider with distance, with no beacon block (REQ-MARK-04). Always depth-tested, and only on a fixed
 * anchor (see {@link Marker}).
 *
 * @param color RGB or ARGB; the beam is always drawn opaque (EC-MARK-08)
 */
public record MarkerBeam(int color) {
	/** The colour the beam is drawn in. */
	public int argb() {
		return ARGB.opaque(color);
	}
}
