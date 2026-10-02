package com.k8bas.skyblockutility.module.npcsearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The NPC waypoint label colours (R21). The label size math moved to WorldMarkersTest (T3.0b). */
class NpcWaypointMarkersTest {
	/** R21: labels are white unless the player switches "White waypoint labels" off. */
	@Test
	void labelsAreWhiteUnlessSwitchedToTheRuleColour() {
		assertEquals(0xFFFFFFFF, NpcWaypointMarkers.labelColor(0x0AA351, true));
		assertEquals(0xFF0AA351, NpcWaypointMarkers.labelColor(0x0AA351, false));
		// A colour stored with an alpha of 0 is still drawn opaque.
		assertEquals(0xFFFF5555, NpcWaypointMarkers.labelColor(0x00FF5555, false));
	}

	@Test
	void whiteLabelsAreTheDefault() {
		assertEquals(true, new NpcSearchConfig().whiteWaypointLabels);
	}
}
