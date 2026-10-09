package com.k8bas.skyblockutility.render.marker;

import java.util.function.Consumer;

/**
 * A feature's markers (NPC waypoints, hotspots). Each provider follows its own toggle and island, so
 * turning one feature off never hides another's markers (REQ-MARK-06). Called on the render thread.
 */
public interface MarkerProvider {
	/** False while the feature is off: then none of its markers are drawn. */
	boolean isActive();

	/** This frame's markers. A provider that gates by island does it here. */
	void collect(Consumer<Marker> out);

	/**
	 * World change, server switch or disconnect: forget markers that belong to the old world
	 * (REQ-MARK-08). Every provider decides: one whose markers come from settings, not from a world,
	 * may keep them and gate them each frame instead.
	 */
	void reset();
}
