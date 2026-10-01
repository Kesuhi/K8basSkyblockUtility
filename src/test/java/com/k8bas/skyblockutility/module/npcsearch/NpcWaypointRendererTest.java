package com.k8bas.skyblockutility.module.npcsearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** EC-PORT-06: the label pull is finite at every distance, including 0. */
class NpcWaypointRendererTest {
	@Test
	void noPullUpToTenBlocks() {
		assertEquals(0.0, NpcWaypointRenderer.pullFactor(0));
		assertEquals(0.0, NpcWaypointRenderer.pullFactor(5));
		assertEquals(0.0, NpcWaypointRenderer.pullFactor(9.999));
	}

	@Test
	void beyondTenBlocksTheLabelIsPulledToTenBlocks() {
		assertEquals(0.0, NpcWaypointRenderer.pullFactor(10), 0.0); // -0.0 at exactly 10, as in 1.0.1
		double distance = 30;
		assertEquals(10.0, distance * (1 + NpcWaypointRenderer.pullFactor(distance)), 1e-9);
	}

	@Test
	void neverNaN() {
		for (double distance : new double[]{0, 1e-12, 10, 1e6, Double.MIN_VALUE}) {
			assertFalse(Double.isNaN(NpcWaypointRenderer.pullFactor(distance)), "distance " + distance);
		}
	}
}
