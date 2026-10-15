package com.k8bas.skyblockutility.module.hotspot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Fishing in" and the "hotspot gone" decision (T3.6b, REQ-HOT-06..REQ-HOT-09): AC-HOT-03 and AC-HOT-04 [A], on a
 * tick clock (20 ticks a second). The hotspot is at (0, surface 70.9, 0) with radius 3.0 (the Hub fallback).
 */
class HotspotGoneTest {
	private static final String HUB = "Hub";
	private final List<String> gone = new ArrayList<>();
	private final HotspotTracker tracker = new HotspotTracker(new HotspotText(t -> { }), (x, y, z) -> 70.9,
			spot -> gone.add(spot.getStandId() + "@" + spot.getBuff()));

	private static HotspotTracker.Stand stand(int id, double x, double z) {
		return new HotspotTracker.Stand(id, x, 72.0, z, "HOTSPOT", true);
	}

	private static HotspotTracker.Position at(double x, double y, double z) {
		return new HotspotTracker.Position(x, y, z);
	}

	/** Seen at tick 0, fished at [fished] ticks, the player at [player] when the stand goes at [removed]; ticks through the grace period. */
	private void run(long fished, long removed, HotspotTracker.Position player) {
		tracker.scan(List.of(stand(1, 0, 0)), 0, player);
		if (fished >= 0) {
			tracker.fished(0, 70.9, 0, fished, HUB);
		}
		tracker.scan(List.of(stand(1, 0, 0)), removed - 1, player);
		for (long tick = removed; tick <= removed + HotspotTracker.GRACE_TICKS; tick++) {
			tracker.scan(List.of(), tick, player);
		}
	}

	@Test
	void fishingInMeansTheOwnBobberInsideTheArea() {
		// AC-HOT-04: radius 3.0, so horizontal 3.5 with dy 2.9 counts; 3.6, or dy 3.0, does not.
		tracker.scan(List.of(stand(1, 0, 0)), 0, at(0, 70, 0));
		HotspotTracker.Hotspot spot = tracker.hotspots().getFirst();
		tracker.fished(3.6, 70.9, 0, 5, HUB);
		tracker.fished(0, 70.9 + 3.0, 0, 6, HUB);
		assertNull(spot.getFishedAt(), "3.6 away or 3.0 above is outside");
		tracker.fished(3.5, 70.9 + 2.9, 0, 7, HUB);
		assertEquals(7L, spot.getFishedAt());
		tracker.fished(0, 70.9 - 2.9, 3.5, 9, HUB);
		assertEquals(9L, spot.getFishedAt(), "and below the surface too");
	}

	@Test
	void withEveryConditionTrueExactlyOneWarning() {
		// AC-HOT-03: fished 30.0 s (600 ticks) before the removal, the player 40.0 blocks from the centre.
		run(100, 700, at(40.0, 70.9, 0));
		assertEquals(List.of("1@UNKNOWN"), gone);
		assertTrue(tracker.hotspots().isEmpty(), "gone after its grace period");
	}

	@Test
	void fishedTooLongAgoOrNeverGivesNone() {
		run(100, 701, at(0, 70.9, 0));
		assertEquals(List.of(), gone, "30.05 s ago: none");
		run(-1, 50, at(0, 70.9, 0));
		assertEquals(List.of(), gone, "never fished: none");
	}

	@Test
	void tooFarAtTheRemovalGivesNone() {
		run(100, 200, at(40.1, 70.9, 0));
		assertEquals(List.of(), gone, "40.1 blocks: none");
		run(100, 200, null);
		assertEquals(List.of(), gone, "no player: none");
	}

	@Test
	void aReSentStandWithinFourBlocksGivesNoneAndTheHotspotContinues() {
		tracker.scan(List.of(stand(1, 0, 0)), 0, at(0, 70, 0));
		tracker.fished(0, 70.9, 0, 10, HUB);
		HotspotTracker.Hotspot spot = tracker.hotspots().getFirst();
		tracker.scan(List.of(), 20, at(0, 70, 0));
		tracker.scan(List.of(stand(2, 3.9, 0)), 25, at(0, 70, 0));
		for (long tick = 26; tick < 60; tick++) {
			tracker.scan(List.of(stand(2, 3.9, 0)), tick, at(0, 70, 0));
		}
		assertEquals(List.of(), gone);
		assertEquals(10L, spot.getFishedAt(), "its fished-in time is kept");
	}

	@Test
	void aNewStandBeyondFourBlocksIsAMoveAndWarns() {
		// REQ-HOT-09: a hotspot that moves counts as gone; the new stand is a new hotspot.
		tracker.scan(List.of(stand(1, 0, 0)), 0, at(0, 70, 0));
		tracker.fished(0, 70.9, 0, 10, HUB);
		tracker.scan(List.of(stand(2, 4.1, 0)), 20, at(0, 70, 0));
		for (long tick = 21; tick <= 31; tick++) {
			tracker.scan(List.of(stand(2, 4.1, 0)), tick, at(0, 70, 0));
		}
		assertEquals(List.of("1@UNKNOWN"), gone);
		assertEquals(2, tracker.hotspots().getFirst().getStandId(), "the new one stays");
		assertNull(tracker.hotspots().getFirst().getFishedAt(), "never fished in");
	}

	@Test
	void aWorldChangeDuringTheGracePeriodGivesNoneAndClearsAll() {
		tracker.scan(List.of(stand(1, 0, 0), stand(2, 30, 0)), 0, at(0, 70, 0));
		tracker.fished(0, 70.9, 0, 10, HUB);
		tracker.scan(List.of(stand(2, 30, 0)), 20, at(0, 70, 0));
		tracker.clear();
		for (long tick = 21; tick < 40; tick++) {
			tracker.scan(List.of(), tick, at(0, 70, 0));
		}
		assertEquals(List.of(), gone);
		assertTrue(tracker.hotspots().isEmpty());
	}

	@Test
	void aSecondRemovalOfTheSameHotspotDoesNothing() {
		run(100, 200, at(0, 70.9, 0));
		for (long tick = 220; tick < 260; tick++) {
			tracker.scan(List.of(), tick, at(0, 70.9, 0));
		}
		assertEquals(1, gone.size(), gone.toString());
	}

	@Test
	void twoFishedHotspotsVanishingTogetherGiveOneWarningEach() {
		// EC-HOT-15.
		tracker.scan(List.of(stand(1, 0, 0), stand(2, 10, 0)), 0, at(5, 70.9, 0));
		tracker.fished(0, 70.9, 0, 10, HUB);
		tracker.fished(10, 70.9, 0, 12, HUB);
		for (long tick = 20; tick <= 30; tick++) {
			tracker.scan(List.of(), tick, at(5, 70.9, 0));
		}
		assertEquals(List.of("1@UNKNOWN", "2@UNKNOWN"), gone);
	}
}
