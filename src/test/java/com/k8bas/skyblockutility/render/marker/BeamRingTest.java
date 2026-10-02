package com.k8bas.skyblockutility.render.marker;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** REQ-MARK-01, -02, -04 (T3.0n): beams and rings. */
class BeamRingTest {
	private static final MarkerLabel LABEL = MarkerLabel.of(new MarkerLabel.Line(Component.literal("Croesus"), 0xFFFFFF));

	/** EC-MARK-08: a beam colour with alpha 0 is drawn opaque. */
	@Test
	void beamsAreAlwaysOpaque() {
		assertEquals(0xFFFF0000, new MarkerBeam(0x00FF0000).argb());
		assertEquals(0xFF0AA351, new MarkerBeam(0x800AA351).argb());
	}

	/** A beam rising from a live entity would show its position above every wall (P1): only fixed anchors get one. */
	@Test
	void onlyFixedAnchorsHaveABeam() {
		assertNotNull(new Marker(MarkerAnchor.fixed(0, 64, 0), LABEL, new MarkerBeam(0xFF0000), null).beam());
		assertNull(new Marker(new MarkerAnchor.OfEntity(null, 0), LABEL, new MarkerBeam(0xFF0000), null).beam());
	}

	/** Rings stay on entity-derived anchors (hotspots, R2): they are always depth-tested and flat. */
	@Test
	void ringsAreKeptOnEntityAnchors() {
		MarkerRing ring = new MarkerRing(3, 0x8029B6B2, MarkerRing.Style.BOTH);
		assertNotNull(new Marker(new MarkerAnchor.OfEntity(null, 0), null, null, ring).ring());
		assertThrows(IllegalArgumentException.class, () -> new MarkerRing(0, 0xFF000000, MarkerRing.Style.OUTLINE));
	}

	/** REQ-MARK-04 / EC-MARK-11: the beam rises from the marker block to the build height, clipped there. */
	@Test
	void beamsRunToTheBuildHeightAndAreClipped() {
		// World from y -64 to 319 (the top block): the beam ends at the top face of 319, so y 320.
		assertEquals(new WorldMarkers.BeamSpan(64, 256), WorldMarkers.beamSpan(64.7, -64, 319));
		assertEquals(new WorldMarkers.BeamSpan(-64, 384), WorldMarkers.beamSpan(-90, -64, 319), "below the world it starts at its floor");
		assertNull(WorldMarkers.beamSpan(320, -64, 319), "at or above the build height there is no beam");
		assertEquals(new WorldMarkers.BeamSpan(319, 1), WorldMarkers.beamSpan(319.5, -64, 319));
	}

	/** REQ-MARK-04: the beam widens with horizontal distance, as a vanilla beacon's does (never below 1). */
	@Test
	void beamsWidenWithDistance() {
		assertEquals(1.0F, WorldMarkers.beamRadiusScale(10));
		assertEquals(1.0F, WorldMarkers.beamRadiusScale(96));
		assertEquals(2.0F, WorldMarkers.beamRadiusScale(192));
	}
}
