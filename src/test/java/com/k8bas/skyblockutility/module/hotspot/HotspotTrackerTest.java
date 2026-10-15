package com.k8bas.skyblockutility.module.hotspot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hotspot tracker (T3.6, REQ-HOT-01..REQ-HOT-04, REQ-HOT-06; EC-HOT-03/05/08/12/16/17) on a tick clock it is given. */
class HotspotTrackerTest {
	private final List<String> unknown = new ArrayList<>();
	private Double surface = 70.9;
	private final HotspotTracker tracker = new HotspotTracker(new HotspotText(unknown::add), (x, y, z) -> surface);

	private static HotspotTracker.Stand hotspot(int id, double x, double z) {
		return new HotspotTracker.Stand(id, x, 72.0, z, "§d§lHOTSPOT", true);
	}

	private static HotspotTracker.Stand buff(int id, double x, double z, String text) {
		return new HotspotTracker.Stand(id, x, 71.7, z, text, true);
	}

	@Test
	void aShownHotspotStandWithItsBuffLineIsAHotspotAtTheSurface() {
		tracker.scan(List.of(hotspot(1, 10.5, 20.5), buff(2, 10.6, 20.4, "+5α Sea Creature Chance")), 0);
		HotspotTracker.Hotspot spot = tracker.hotspots().getFirst();
		assertEquals(1, spot.getStandId());
		assertEquals(HotspotText.Buff.SEA_CREATURE_CHANCE, spot.getBuff());
		assertEquals(70.9, spot.surfaceY());
		assertEquals(10.5, spot.getX());
		assertEquals(20.5, spot.getZ());
	}

	@Test
	void aStandWhoseNameIsNotDisplayedIsNoHotspot() {
		// EC-HOT-17, REQ-HOT-01 (R2).
		tracker.scan(List.of(new HotspotTracker.Stand(1, 0, 72, 0, "HOTSPOT", false)), 0);
		assertTrue(tracker.hotspots().isEmpty());
	}

	@Test
	void theBuffLineIsTheNearbyNameTagAndMayComeLater() {
		// REQ-HOT-02: same X/Z within 0.5, up to 1 block below; EC-HOT-08: Unknown (white) until it arrives.
		tracker.scan(List.of(hotspot(1, 0, 0), buff(2, 0.6, 0, "+15☂ Fishing Speed"), new HotspotTracker.Stand(3, 0, 70.5, 0, "+2⚓ Double Hook Chance", true)), 0);
		assertEquals(HotspotText.Buff.UNKNOWN, tracker.hotspots().getFirst().getBuff(), "0.6 away and 1.5 below are not its buff line");
		tracker.scan(List.of(hotspot(1, 0, 0), buff(2, 0.4, 0, "+15☂ Fishing Speed")), 1);
		assertEquals(HotspotText.Buff.FISHING_SPEED, tracker.hotspots().getFirst().getBuff());
		tracker.scan(List.of(hotspot(1, 0, 0), buff(2, 0.4, 0, "+5♔ Trophy Fish Chance")), 2);
		assertEquals(HotspotText.Buff.TROPHY_FISH_CHANCE, tracker.hotspots().getFirst().getBuff(), "a changed line updates the type");
		assertEquals(List.of(), unknown, "no buff line is no unknown text");
	}

	@Test
	void aRemovedStandStaysThroughTheGracePeriod() {
		// REQ-HOT-06: 10 ticks.
		tracker.scan(List.of(hotspot(1, 0, 0)), 0);
		tracker.scan(List.of(), 5);
		HotspotTracker.Hotspot spot = tracker.hotspots().getFirst();
		assertEquals(5L, spot.getRemovedAt());
		tracker.scan(List.of(), 14);
		assertEquals(1, tracker.hotspots().size(), "still in its grace period at 9 ticks");
		tracker.scan(List.of(), 15);
		assertTrue(tracker.hotspots().isEmpty(), "gone after 10 ticks");
	}

	@Test
	void aReSentStandWithinFourBlocksContinuesTheSameHotspot() {
		// EC-HOT-03; at 3.9 the same hotspot, its radius samples kept; at 4.1 a new one.
		tracker.scan(List.of(hotspot(1, 0, 0)), 0);
		tracker.particle(2.5, 70.9, 0);
		HotspotTracker.Hotspot spot = tracker.hotspots().getFirst();
		tracker.scan(List.of(), 1);
		tracker.scan(List.of(hotspot(7, 3.9, 0)), 4);
		assertEquals(1, tracker.hotspots().size());
		assertSame(spot, tracker.hotspots().getFirst());
		assertEquals(7, spot.getStandId());
		assertNull(spot.getRemovedAt());
		assertEquals(1, spot.sampleCount());

		tracker.scan(List.of(), 5);
		tracker.scan(List.of(hotspot(8, 3.9 + 4.1, 0)), 6);
		assertEquals(2, tracker.hotspots().size(), "4.1 away: a new hotspot beside the removed one");
	}

	@Test
	void aStandSentAgainInTheSameTickContinuesItsHotspot() {
		// EC-HOT-03: removed and re-added between two client ticks, so one scan sees only the new stand.
		tracker.scan(List.of(hotspot(5, 0, 0)), 0);
		tracker.particle(2.5, 70.9, 0);
		HotspotTracker.Hotspot spot = tracker.hotspots().getFirst();
		tracker.scan(List.of(hotspot(9, 0, 0)), 1);
		assertEquals(1, tracker.hotspots().size(), "no second hotspot: " + tracker.hotspots());
		assertSame(spot, tracker.hotspots().getFirst());
		assertEquals(9, spot.getStandId());
		assertNull(spot.getRemovedAt());
		assertEquals(1, spot.sampleCount(), "its radius samples kept");
	}

	@Test
	void theJustRemovedHotspotWinsOverAnOlderOneFurtherAway() {
		// EC-HOT-05: the nearest removed hotspot, also when one was removed earlier within 4 blocks.
		tracker.scan(List.of(hotspot(1, 3.9, 0), hotspot(2, 0, 0)), 0);
		HotspotTracker.Hotspot near = tracker.hotspots().get(1);
		tracker.scan(List.of(hotspot(2, 0, 0)), 1);
		tracker.scan(List.of(hotspot(9, 0, 0)), 2);
		assertSame(near, tracker.hotspots().stream().filter(h -> h.getRemovedAt() == null).findFirst().orElseThrow());
	}

	@Test
	void aRecognisedBuffLineWinsOverANearerNameTag() {
		tracker.scan(List.of(hotspot(1, 0, 0), buff(2, 0.1, 0, "§eSome Player"), buff(3, 0.3, 0, "+5α Sea Creature Chance")), 0);
		assertEquals(HotspotText.Buff.SEA_CREATURE_CHANCE, tracker.hotspots().getFirst().getBuff());
		assertEquals(List.of(), unknown, "the passing name is not reported");
	}

	@Test
	void aReSentStandContinuesTheNearestRemovedHotspot() {
		// EC-HOT-05.
		tracker.scan(List.of(hotspot(1, 0, 0), hotspot(2, 3, 0)), 0);
		HotspotTracker.Hotspot second = tracker.hotspots().get(1);
		tracker.scan(List.of(), 1);
		tracker.scan(List.of(hotspot(9, 2.5, 0)), 2);
		assertSame(second, tracker.hotspots().stream().filter(h -> h.getRemovedAt() == null).findFirst().orElseThrow());
		assertEquals(9, second.getStandId());
	}

	@Test
	void aRingParticleIsWithinTheEstimatedRadiusOfAnActiveHotspot() {
		// REQ-HOT-14 (T3.6c, R41 (b), review): only inside the area, not the wider sampling zone.
		assertNull(tracker.ringParticleOf(2.5, 71.0, 0, "Hub"), "no hotspot yet");
		tracker.scan(List.of(hotspot(1, 0, 0)), 0);
		HotspotTracker.Hotspot spot = tracker.hotspots().getFirst();
		// The Hub fallback radius is 3 until particles are seen: the area reaches 3.5 blocks.
		assertSame(spot, tracker.ringParticleOf(2.5, 71.0, 0, "Hub"));
		assertSame(spot, tracker.ringParticleOf(0, 70.9, 3.5, "Hub"));
		assertNull(tracker.ringParticleOf(0, 70.9, 3.6, "Hub"), "outside the area");
		assertNull(tracker.ringParticleOf(0, 70.9, 5.5, "Hub"), "inside the sampling zone, outside the ring");
		assertSame(spot, tracker.ringParticleOf(0, 70.9, 5.5, "Crimson Isle"), "a wider island cap");
		assertNull(tracker.ringParticleOf(2.5, 73.5, 0, "Hub"), "more than 2 blocks above the surface");
		tracker.scan(List.of(), 1);
		assertNull(tracker.ringParticleOf(2.5, 71.0, 0, "Hub"), "a removed stand's hotspot in its grace period hides nothing");
	}

	@Test
	void particlesNearAHotspotGiveItsRadius() {
		// REQ-HOT-04: the horizontal distance to the nearest hotspot within the cap and a block, near its surface.
		tracker.scan(List.of(hotspot(1, 0, 0)), 0);
		for (int i = 0; i < 20; i++) {
			double angle = i * Math.PI / 10;
			tracker.particle(2.5 * Math.cos(angle), 71.0, 2.5 * Math.sin(angle));
		}
		tracker.particle(3.9, 71.0, 0);
		tracker.particle(1.0, 75.0, 0);
		tracker.particle(9.0, 71.0, 0);
		HotspotTracker.Hotspot spot = tracker.hotspots().getFirst();
		assertEquals(21, spot.sampleCount(), "too high and too far are not its particles");
		assertEquals(2.5, spot.radius("Hub"));
		assertEquals(3.0, new HotspotTracker(new HotspotText(t -> { }), (x, y, z) -> 70.0).radiusOrFallback(null, "Hub"),
				"no hotspot: the island fallback");
	}

	@Test
	void theSurfaceIsRetriedUntilFoundAndTheFootIsUsedMeanwhile() {
		// REQ-HOT-03.
		surface = null;
		tracker.scan(List.of(hotspot(1, 0, 0)), 0);
		assertEquals(72.0, tracker.hotspots().getFirst().surfaceY(), "the stand's foot until the liquid is found");
		surface = 70.9;
		tracker.scan(List.of(hotspot(1, 0, 0)), 1);
		assertEquals(70.9, tracker.hotspots().getFirst().surfaceY());
	}

	@Test
	void aWorldChangeClearsEverything() {
		// REQ-HOT-06, EC-HOT-16.
		tracker.scan(List.of(hotspot(1, 0, 0), hotspot(2, 30, 0)), 0);
		tracker.scan(List.of(hotspot(2, 30, 0)), 1);
		tracker.clear();
		assertTrue(tracker.hotspots().isEmpty());
	}
}
