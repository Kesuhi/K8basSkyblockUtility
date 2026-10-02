package com.k8bas.skyblockutility.render.marker;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-MARK-01, -02, -05 (T3.0b): what a marker may do depends on its anchor. */
class MarkerTest {
	private static final MarkerLabel SEE_THROUGH_WITH_DISTANCE = MarkerLabel.of(new MarkerLabel.Line(Component.literal("Croesus"), 0xFFFFFF))
			.withBackground(0x70202020).asSeeThrough().withDistance(0xFFFF55);

	@Test
	void aFixedAnchorKeepsSeeThroughAndTheDistanceLine() {
		Marker marker = new Marker(MarkerAnchor.fixed(0.5, 64, 0.5), SEE_THROUGH_WITH_DISTANCE);
		assertTrue(marker.label().seeThrough());
		assertTrue(marker.label().distanceLine());
	}

	/** AC-MARK-02 [A]: a see-through request for an entity-anchored marker is refused, so it renders depth-tested. */
	@Test
	void anEntityAnchorRefusesSeeThroughAndTheDistanceLine() {
		Marker marker = new Marker(new MarkerAnchor.OfEntity(null, 2.0), SEE_THROUGH_WITH_DISTANCE);
		assertFalse(marker.label().seeThrough(), "entity anchors are always depth-tested");
		assertFalse(marker.label().distanceLine(), "distance is shown only to fixed coordinates (P4)");
		// The rest of the label is kept.
		assertEquals(SEE_THROUGH_WITH_DISTANCE.lines(), marker.label().lines());
		assertEquals(0x70202020, marker.label().backgroundColor());
	}

	@Test
	void aLabelHasOneToThreeLinesIncludingTheDistanceLine() {
		MarkerLabel.Line line = new MarkerLabel.Line(Component.literal("x"), 0xFFFFFF);
		assertThrows(IllegalArgumentException.class, () -> MarkerLabel.of());
		assertEquals(3, MarkerLabel.of(line, line).withDistance(0xFFFFFF).lineCount());
		assertThrows(IllegalArgumentException.class, () -> MarkerLabel.of(line, line, line).withDistance(0xFFFFFF));
		assertThrows(IllegalArgumentException.class, () -> new MarkerLabel(List.of(line, line, line, line), 0, false, false, 0));
	}

	@Test
	void nullTextAndNullEntitiesAreRejected() {
		assertThrows(NullPointerException.class, () -> new MarkerLabel.Line(null, 0xFFFFFF));
		assertThrows(NullPointerException.class, () -> MarkerAnchor.entity(null, 0));
	}

	/**
	 * AC-MARK-04 [A]: the distance line's text, whole metres from the given position. That the player's
	 * position (not the camera's) is passed is checked in a real third-person frame by MarkerToolkitGameTest.
	 */
	@Test
	void theDistanceTextIsWholeMetresFromThePlayer() {
		Vec3 player = new Vec3(0, 64, 0);
		assertEquals("100m", WorldMarkers.distanceText(player, new Vec3(0, 64, 100)));
		assertEquals("0m", WorldMarkers.distanceText(player, player), "EC-MARK-01");
		assertEquals("3m", WorldMarkers.distanceText(player, new Vec3(0, 64, 2.5)), "rounded half-up");
	}
}
