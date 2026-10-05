package com.k8bas.skyblockutility.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-HUD-14, AC-HUD-14 [A] (T2.9c): edges and centres snap within 4 px to the screen's centre lines and edges and to other elements' edges. */
class HudSnapTest {
	private static final double W = 960;
	private static final double H = 540;
	private static final List<HudRect> NONE = List.of();

	private static HudRect snap(HudRect rect, List<HudRect> others) {
		return HudSnap.snap(rect, W, H, others);
	}

	@Test
	void theCentreSnapsToTheVerticalCentreLine() {
		HudRect snapped = snap(new HudRect(447, 200, 60, 30), NONE);
		assertEquals(new HudRect(450, 200, 60, 30), snapped, "centre 477 to 480");
		assertEquals(List.of(new HudSnap.Guide(true, 480)), HudSnap.guides(snapped, W, H, NONE));
	}

	@Test
	void itSnapsAtExactly4PxButNotAt5() {
		assertEquals(450, snap(new HudRect(446, 200, 60, 30), NONE).x(), "centre 476: 4 px");
		HudRect away = snap(new HudRect(445, 200, 60, 30), NONE);
		assertEquals(445, away.x(), "centre 475: 5 px");
		assertTrue(HudSnap.guides(away, W, H, NONE).isEmpty());
	}

	@Test
	void theCentreSnapsToTheHorizontalCentreLine() {
		HudRect snapped = snap(new HudRect(100, 252, 60, 30), NONE);
		assertEquals(255, snapped.y(), "centre 267 to 270");
		assertEquals(List.of(new HudSnap.Guide(false, 270)), HudSnap.guides(snapped, W, H, NONE));
	}

	@Test
	void edgesSnapToTheScreenEdges() {
		HudRect left = snap(new HudRect(3, 100, 60, 30), NONE);
		assertEquals(0, left.x());
		assertEquals(List.of(new HudSnap.Guide(true, 0)), HudSnap.guides(left, W, H, NONE));
		HudRect right = snap(new HudRect(897, 100, 60, 30), NONE);
		assertEquals(900, right.x(), "right edge 957 to 960");
		assertEquals(List.of(new HudSnap.Guide(true, 959)), HudSnap.guides(right, W, H, NONE), "the last column");
		HudRect bottom = snap(new HudRect(100, 507, 60, 30), NONE);
		assertEquals(510, bottom.y());
		assertEquals(List.of(new HudSnap.Guide(false, 539)), HudSnap.guides(bottom, W, H, NONE));
	}

	@Test
	void edgesSnapToTheCentreLines() {
		assertEquals(480, snap(new HudRect(478, 100, 100, 30), NONE).x(), "left edge 478 to 480");
		assertEquals(380, snap(new HudRect(383, 100, 100, 30), NONE).x(), "right edge 483 to 480");
	}

	@Test
	void edgesSnapToOtherElementsEdges() {
		HudRect other = new HudRect(300, 100, 80, 20);
		HudRect touching = snap(new HudRect(382, 400, 60, 30), List.of(other));
		assertEquals(380, touching.x(), "left edge to its right edge");
		assertEquals(List.of(new HudSnap.Guide(true, 380)), HudSnap.guides(touching, W, H, List.of(other)));
		assertEquals(300, snap(new HudRect(302, 400, 60, 30), List.of(other)).x(), "left edge to its left edge");
		HudRect before = snap(new HudRect(237, 400, 60, 30), List.of(other));
		assertEquals(240, before.x(), "right edge 297 to its left edge 300");
		assertEquals(List.of(new HudSnap.Guide(true, 300)), HudSnap.guides(before, W, H, List.of(other)));
		assertEquals(120, snap(new HudRect(600, 123, 60, 30), List.of(other)).y(), "top 123 to its bottom 120");
	}

	@Test
	void theNearestTargetWins() {
		List<HudRect> others = List.of(new HudRect(200, 400, 10, 10), new HudRect(264, 400, 10, 10));
		assertEquals(204, snap(new HudRect(203, 100, 60, 30), others).x(), "1 px to 264 beats 3 px to 200");
	}

	@Test
	void tiesPreferTheScreenCentreThenTheEarlierElement() {
		List<HudRect> edge = List.of(new HudRect(436, 400, 10, 10));
		assertEquals(450, snap(new HudRect(448, 100, 60, 30), edge).x(), "the centre, 2 px, over an element edge 2 px the other way");
		List<HudRect> two = List.of(new HudRect(102, 400, 10, 10), new HudRect(88, 400, 10, 10));
		assertEquals(102, snap(new HudRect(100, 100, 60, 30), two).x(), "two elements 2 px either way: the first listed");
	}

	@Test
	void theAxesSnapIndependently() {
		HudRect snapped = snap(new HudRect(447, 252, 60, 30), NONE);
		assertEquals(new HudRect(450, 255, 60, 30), snapped);
		assertEquals(List.of(new HudSnap.Guide(true, 480), new HudSnap.Guide(false, 270)), HudSnap.guides(snapped, W, H, NONE), "vertical first");
	}

	@Test
	void everyTargetTheElementLiesOnGetsOneGuide() {
		HudRect rect = new HudRect(420, 100, 60, 30);
		List<HudRect> others = List.of(new HudRect(420, 300, 30, 10), new HudRect(420, 350, 50, 10));
		assertEquals(List.of(new HudSnap.Guide(true, 480), new HudSnap.Guide(true, 420)), HudSnap.guides(rect, W, H, others),
				"its right edge on the centre, its left on two elements' left edges: one guide each column");
	}

	@Test
	void anOddWidthCentresOnAWholePixel() {
		HudRect snapped = snap(new HudRect(448, 100, 61, 30), NONE);
		assertEquals(450, snapped.x(), "centre 478.5 to 480, on a whole pixel");
		assertEquals(List.of(new HudSnap.Guide(true, 480)), HudSnap.guides(snapped, W, H, NONE), "within half a pixel");
	}

	@Test
	void aFractionalScaledSizeLandsOnAWholePixel() {
		List<HudRect> other = List.of(new HudRect(300, 400, 10, 10));
		HudRect snapped = snap(new HudRect(242.5, 100, 54.9, 30), other);
		assertEquals(245, snapped.x());
		assertEquals(List.of(new HudSnap.Guide(true, 300)), HudSnap.guides(snapped, W, H, other));
	}

	@Test
	void snappingNeverLeavesTheWindow() {
		List<HudRect> other = List.of(new HudRect(930, 50, 20, 20));
		HudRect snapped = snap(new HudRect(928, 100, 60, 30), other);
		assertEquals(900, snapped.x(), "held inside the window");
		assertEquals(List.of(new HudSnap.Guide(true, 959)), HudSnap.guides(snapped, W, H, other), "no guide at 930, where it no longer is");
	}

	@Test
	void anAxisLargerThanTheScreenDoesNotSnap() {
		HudRect snapped = snap(new HudRect(5, 252, 1000, 30), NONE);
		assertEquals(5, snapped.x());
		assertEquals(255, snapped.y(), "the other axis still snaps");
		assertTrue(HudSnap.guides(snapped, W, H, NONE).stream().noneMatch(HudSnap.Guide::vertical));
	}

	@Test
	void farFromEverythingNothingChanges() {
		HudRect rect = new HudRect(100, 100, 60, 30);
		assertEquals(rect, snap(rect, NONE));
		assertTrue(HudSnap.guides(rect, W, H, NONE).isEmpty());
	}
}
