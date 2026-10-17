package com.k8bas.skyblockutility.hud;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-HUD-05, REQ-HUD-08 (T2.8), REQ-HUD-14 (T2.9c): the HUD editor's model, AC-HUD-04/14 [A] and EC-HUD-01/02/03/06. */
class HudEditorModelTest {
	private static final double W = 960;
	private static final double H = 540;
	private static final HudPosition ODDS_DEFAULT = new HudPosition(HudAnchor.TOP_LEFT, 4, 4, 1);
	private static final HudPosition XP_DEFAULT = new HudPosition(HudAnchor.BOTTOM_RIGHT, -4, -4, 1);

	private final Map<String, double[]> sizes = new LinkedHashMap<>(Map.of("odds", new double[] {100, 40}, "xp", new double[] {80, 20}));

	private HudEditorModel model(HudPosition odds, HudPosition xp) {
		Map<String, HudPosition> opened = new LinkedHashMap<>();
		opened.put("odds", odds);
		opened.put("xp", xp);
		return new HudEditorModel(List.of(new HudEditorModel.Item("odds", ODDS_DEFAULT), new HudEditorModel.Item("xp", XP_DEFAULT)),
				opened, new HudEditorModel.Sizes() {
					@Override
					public double width(String id) {
						return sizes.get(id)[0];
					}

					@Override
					public double height(String id) {
						return sizes.get(id)[1];
					}
				});
	}

	/** AC-HUD-14 [A] (T2.9c): dragged to 2 px from the vertical centre line, it snaps there and shows the guide; dropped, it stays. */
	@Test
	void aSnappingDragCentresTheElement() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(442, 110, W, H, true);
		assertEquals(430, model.rect("odds", W, H).x(), 0, "centre 482 snapped to 480");
		assertTrue(model.guides(W, H).contains(new HudSnap.Guide(true, 480)));
		model.release(W, H);
		assertEquals(430, model.rect("odds", W, H).x(), 0, "kept on the drop");
		assertEquals(0.5, model.positions().get("odds").anchor().fx(), "anchored to the centre column");
		assertEquals(0, model.positions().get("odds").x(), "centred exactly");
		assertTrue(model.guides(W, H).isEmpty(), "no guide after the drop");
	}

	/** AC-UI-44 [A] (REQ-UI-43, T3.33): the editor shows the move pointer while an element is dragged, and only then. */
	@Test
	void itKnowsWhileAnElementIsDragged() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		assertEquals("odds", model.at(210, 110, W, H), "over an element (the editor shows the move pointer there)");
		assertNull(model.at(5, 5, W, H), "over empty space");
		assertFalse(model.dragging(), "before a press");
		model.press(5, 5, W, H);
		assertFalse(model.dragging(), "a press on empty space");
		model.release(W, H);
		model.press(210, 110, W, H);
		assertTrue(model.dragging(), "a press on an element grabs it");
		model.drag(250, 110, W, H, false);
		assertTrue(model.dragging(), "while it moves");
		model.release(W, H);
		assertFalse(model.dragging(), "dropped");
	}

	/** AC-HUD-14 [A]: with Alt held (no snap) the element follows the mouse exactly and no guide shows. */
	@Test
	void withoutSnappingTheDragFollowsTheMouse() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(442, 110, W, H, false);
		assertEquals(432, model.rect("odds", W, H).x(), 0);
		assertTrue(model.guides(W, H).isEmpty());
	}

	@Test
	void aDragSnapsToAnotherElementsEdge() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(783, 110, W, H, true);
		assertEquals(776, model.rect("odds", W, H).x(), 0, "its right edge 873 to xp's left edge 876");
		assertTrue(model.guides(W, H).contains(new HudSnap.Guide(true, 876)));
	}

	/** REQ-HUD-08: a click that only selects never moves the element, even 2 px from a target. */
	@Test
	void aPressWithoutMovingNeverSnaps() {
		HudPosition stored = new HudPosition(HudAnchor.TOP_LEFT, 432, 100, 1);
		HudEditorModel model = model(stored, XP_DEFAULT);
		model.press(442, 110, W, H);
		model.drag(442, 110, W, H, true);
		assertEquals(stored, model.positions().get("odds"));
		assertTrue(model.guides(W, H).isEmpty());
	}

	/** REQ-HUD-08: a hand's sub-pixel jitter during a click that only selects never snaps the element, 2 px from a target. */
	@Test
	void aJitterWhileClickingNeverSnaps() {
		HudPosition stored = new HudPosition(HudAnchor.TOP_LEFT, 432, 100, 1);
		HudEditorModel model = model(stored, XP_DEFAULT);
		model.press(442, 110, W, H);
		model.drag(442.33, 110, W, H, true);
		model.drag(441.8, 109.9, W, H, true);
		model.release(W, H);
		assertEquals(stored, model.positions().get("odds"), "within 2 px of the press nothing snaps");
	}

	/** The 4 px are measured on the place as drawn: left 425.5 is drawn at 426, its centre 4 px from 480, and snaps. */
	@Test
	void theThresholdIsMeasuredOnTheDrawnPlace() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(435.5, 110, W, H, true);
		assertEquals(430, model.rect("odds", W, H).x(), 0);
	}

	/** EC-HUD-01 mid-drag: on another screen size the guides of the old one are not shown. */
	@Test
	void guidesAreForTheScreenTheyWereMadeFor() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(442, 110, W, H, true);
		assertFalse(model.guides(W, H).isEmpty());
		assertTrue(model.guides(640, 360).isEmpty(), "resized: none until the next move");
	}

	@Test
	void aScaledElementSnapsByItsScaledBounds() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1.5), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(417, 110, W, H, true);
		assertEquals(405, model.rect("odds", W, H).x(), 0, "150 px wide: centre 482 to 480");
	}

	@Test
	void itsOwnStartPlaceIsNoTarget() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(212, 110, W, H, true);
		assertEquals(202, model.rect("odds", W, H).x(), 0, "2 px away from where it started, nothing pulls it back");
	}

	@Test
	void guidesClearOnScrollReleaseAndCancel() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(442, 110, W, H, true);
		assertFalse(model.guides(W, H).isEmpty());
		model.scroll(450, 110, 1, W, H);
		assertTrue(model.guides(W, H).isEmpty(), "a new scale: the guides are stale");
		// 110 px wide now: left 426 puts its centre 1 px from 480.
		model.drag(436, 110, W, H, true);
		assertFalse(model.guides(W, H).isEmpty(), "the next move snaps the new size");
		model.release(W, H);
		assertTrue(model.guides(W, H).isEmpty());
		model.press(440, 110, W, H);
		model.drag(443, 110, W, H, true);
		assertFalse(model.guides(W, H).isEmpty(), "snapped again before the cancel");
		model.cancel();
		assertTrue(model.guides(W, H).isEmpty());
	}

	/** Arrow nudges never snap: a 1 px step next to a target stays a 1 px step. */
	@Test
	void nudgesNeverSnap() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 428, 100, 1), XP_DEFAULT);
		model.select("odds");
		model.nudge(1, 0, W, H);
		assertEquals(429, model.rect("odds", W, H).x(), 0, "centre 479, not pulled to 480");
		assertTrue(model.guides(W, H).isEmpty());
	}

	/** AC-HUD-04: a drag by (+40, +25) moves the rectangle by as much, with no jump at the press. */
	@Test
	void aDragMovesTheElementByTheMouseWithoutAJump() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		HudRect before = model.rect("odds", W, H);
		model.press(230, 117, W, H);
		assertEquals("odds", model.selected());
		assertEquals(before, model.rect("odds", W, H), "the press alone moves nothing");
		for (int i = 1; i <= 40; i++) {
			model.drag(230 + i, 117 + i * 25.0 / 40, W, H);
		}
		model.release(W, H);
		HudRect after = model.rect("odds", W, H);
		assertEquals(before.x() + 40, after.x(), 0);
		assertEquals(before.y() + 25, after.y(), 0);
	}

	@Test
	void aDropReanchorsWithoutMoving() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(810, 460, W, H);
		HudRect dragged = model.rect("odds", W, H);
		model.release(W, H);
		assertEquals(HudAnchor.BOTTOM_RIGHT, model.positions().get("odds").anchor(), "the centre is in the bottom right third");
		assertEquals(dragged, model.rect("odds", W, H));
	}

	/** Drags keep the element inside the window. */
	@Test
	void aDragStopsAtTheWindowEdges() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(-500, -500, W, H);
		assertEquals(new HudRect(0, 0, 100, 40), model.rect("odds", W, H));
		model.drag(5000, 5000, W, H);
		model.release(W, H);
		assertEquals(new HudRect(W - 100, H - 40, 100, 40), model.rect("odds", W, H));
	}

	/** AC-HUD-04: 3 notches up from 1.00 give 1.30; held at 3.00 and 0.50. The wheel scales the hovered element, else the selected one. */
	@Test
	void theWheelScalesInTenthsWithinTheRange() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		for (int i = 0; i < 3; i++) {
			assertTrue(model.scroll(210, 110, 1, W, H));
		}
		assertEquals(1.3, model.positions().get("odds").scale(), 1e-9);
		model.positions().put("odds", model.positions().get("odds").withScale(3.0));
		model.scroll(210, 110, 1, W, H);
		assertEquals(3.0, model.positions().get("odds").scale(), 1e-9);
		model.positions().put("odds", model.positions().get("odds").withScale(0.5));
		model.scroll(210, 110, -1, W, H);
		assertEquals(0.5, model.positions().get("odds").scale(), 1e-9);
		assertFalse(model.scroll(600, 300, 1, W, H), "over empty space with nothing selected: nothing");
		model.press(210, 110, W, H);
		model.release(W, H);
		assertTrue(model.scroll(600, 300, 1, W, H), "over empty space: the selected one");
		assertEquals(0.6, model.positions().get("odds").scale(), 1e-9);
	}

	/** AC-HUD-04: an arrow moves the selection 1 px, Shift+arrow 10 px; a click on empty space deselects. */
	@Test
	void arrowsNudgeTheSelection() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.nudge(1, 0, W, H);
		assertEquals(200, model.rect("odds", W, H).x(), 0, "nothing selected: nothing moves");
		model.press(210, 110, W, H);
		model.release(W, H);
		model.nudge(1, 0, W, H);
		assertEquals(201, model.rect("odds", W, H).x(), 0);
		model.nudge(0, 10, W, H);
		assertEquals(110, model.rect("odds", W, H).y(), 0);
		model.nudge(-10000, 0, W, H);
		assertEquals(0, model.rect("odds", W, H).x(), 0, "and stays inside the window");
		model.press(600, 300, W, H);
		model.release(W, H);
		assertNull(model.selected(), "a click on empty space deselects");
	}

	/** AC-HUD-04: Cancel restores every element; Reset Selected only the selected one; Reset All every one. */
	@Test
	void cancelAndTheResets() {
		HudPosition odds = new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1.5);
		HudPosition xp = new HudPosition(HudAnchor.CENTER, 10, 10, 2);
		HudEditorModel model = model(odds, xp);
		model.press(210, 110, W, H);
		model.drag(400, 300, W, H);
		model.release(W, H);
		model.scroll(410, 310, 2, W, H);
		model.positions().put("xp", xp.withScale(0.5));
		model.cancel();
		assertEquals(Map.of("odds", odds, "xp", xp), model.positions());

		model.press(210, 110, W, H);
		model.release(W, H);
		model.resetSelected();
		assertEquals(ODDS_DEFAULT, model.positions().get("odds"));
		assertEquals(xp, model.positions().get("xp"), "only the selected one");
		model.resetAll();
		assertEquals(Map.of("odds", ODDS_DEFAULT, "xp", XP_DEFAULT), model.positions());
	}

	/** EC-HUD-03: the element drawn on top (registered later) gets the click. */
	@Test
	void theTopmostElementGetsTheClick() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), new HudPosition(HudAnchor.TOP_LEFT, 210, 110, 1));
		model.press(220, 120, W, H);
		assertEquals("xp", model.selected());
	}

	/** EC-HUD-02: larger than the window, it is drawn from the top-left, can be selected and scaled down. */
	@Test
	void anElementLargerThanTheWindowCanBeSelectedAndScaledDown() {
		sizes.put("odds", new double[] {200, 120});
		HudEditorModel model = model(new HudPosition(HudAnchor.CENTER, 0, 0, 3), XP_DEFAULT);
		assertEquals(new HudRect(0, 0, 600, 360), model.rect("odds", 320, 240));
		assertTrue(model.scroll(5, 5, -10, 320, 240));
		assertEquals(2.0, model.positions().get("odds").scale(), 1e-9);
		model.press(5, 5, 320, 240);
		assertEquals("odds", model.selected());
	}

	/** EC-HUD-06: content that changes size during a drag keeps the grab point under the cursor (no jump). */
	@Test
	void contentGrowingDuringADragDoesNotJump() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(210, 110, W, H);
		model.drag(260, 160, W, H);
		sizes.put("odds", new double[] {140, 60});
		model.drag(261, 160, W, H);
		assertEquals(251, model.rect("odds", W, H).x(), 0, "the left edge stays 10 px left of the cursor");
	}

	/** EC-HUD-06 for anchors whose offset depends on the size: the grabbed point stays under the cursor. */
	@Test
	void contentGrowingDuringADragKeepsTheGrabPointForEveryAnchor() {
		for (HudAnchor anchor : new HudAnchor[] {HudAnchor.BOTTOM_RIGHT, HudAnchor.CENTER}) {
			sizes.put("odds", new double[] {100, 40});
			HudEditorModel model = model(new HudPosition(anchor, 0, 0, 1), XP_DEFAULT);
			HudRect start = model.rect("odds", W, H);
			model.press(start.x() + 10, start.y() + 10, W, H);
			model.drag(start.x() - 90, start.y() - 60, W, H);
			sizes.put("odds", new double[] {140, 60});
			model.drag(start.x() - 89, start.y() - 60, W, H);
			HudRect rect = model.rect("odds", W, H);
			assertEquals(start.x() - 99, rect.x(), 0, anchor + ": x");
			assertEquals(start.y() - 70, rect.y(), 0, anchor + ": y");
		}
	}

	/** A click that only selects never changes the position, even for an element drawn clamped (REQ-HUD-04). */
	@Test
	void aClickWithoutMovingChangesNothing() {
		HudPosition stored = new HudPosition(HudAnchor.TOP_LEFT, 500, 30, 1);
		HudEditorModel model = model(stored, XP_DEFAULT);
		HudRect clamped = model.rect("odds", 427, 240);
		assertEquals(327, clamped.x(), 0, "drawn shifted inside the small window");
		model.press(clamped.x() + 5, clamped.y() + 5, 427, 240);
		model.drag(clamped.x() + 5, clamped.y() + 5, 427, 240);
		model.release(427, 240);
		assertEquals("odds", model.selected());
		assertEquals(stored, model.positions().get("odds"), "selected, not moved, not re-anchored");
	}

	/** EC-HUD-01: a resize keeps the selection and the unsaved changes; elements are placed from their anchors. */
	@Test
	void aResizeKeepsTheSelectionAndTheChanges() {
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		model.press(900, 525, W, H);
		model.release(W, H);
		model.nudge(-10, 0, W, H);
		assertEquals("xp", model.selected());
		HudRect small = model.rect("xp", 427, 240);
		assertEquals(427 - 4 - 10 - 80, small.x(), 0, "still 14 px from the right edge");
		assertEquals("xp", model.selected());
	}

	/** A 60×30 element (as AC-HUD-16's) at {@code at}, selected by a click on it. */
	private HudEditorModel boxAt(HudPosition at) {
		sizes.put("odds", new double[] {60, 30});
		HudEditorModel model = model(at, XP_DEFAULT);
		HudRect rect = model.rect("odds", W, H);
		model.press(rect.x() + 1, rect.y() + 1, W, H);
		model.release(W, H);
		return model;
	}

	/** The handle {@code corner}'s centre (0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right). */
	private static double[] handle(HudEditorModel model, int corner) {
		double[] centres = model.handleCentres(W, H);
		return new double[] {centres[2 * corner], centres[2 * corner + 1]};
	}

	/** AC-HUD-16 [A] (REQ-HUD-15, T3.54): the selected element has a handle at each corner, none without a selection. */
	@Test
	void theSelectedElementHasFourHandles() {
		sizes.put("odds", new double[] {60, 30});
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), XP_DEFAULT);
		assertNull(model.handleCentres(W, H), "nothing selected: no handles");
		model.press(210, 110, W, H);
		model.release(W, H);
		assertArrayEquals(new double[] {200, 100, 260, 100, 200, 130, 260, 130}, model.handleCentres(W, H));
		assertEquals(3, model.handleAt(261, 129, W, H), "on the bottom-right handle");
		assertEquals(0, model.handleAt(197, 102, W, H), "3 px off the top-left corner still takes it");
		assertEquals(-1, model.handleAt(230, 115, W, H), "the element's middle is not a handle");
		assertEquals(-1, model.handleAt(195.5, 100, W, H), "4.5 px off the top-left corner is out of reach");
		model.press(5, 5, W, H);
		assertEquals(-1, model.handleAt(261, 129, W, H), "deselected: no handle");
	}

	/** AC-HUD-16 [A]: the bottom-right handle dragged by (+30, +15) scales 1.0 to 1.5 about the top-left corner. */
	@Test
	void theBottomRightHandleScalesAboutTheTopLeft() {
		HudEditorModel model = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1));
		double[] grab = handle(model, 3);
		model.press(grab[0], grab[1], W, H);
		assertTrue(model.scaling(), "a press on a handle starts a scale");
		assertFalse(model.dragging(), "not a move");
		model.drag(grab[0] + 30, grab[1] + 15, W, H, true);
		assertEquals(1.5, model.positions().get("odds").scale());
		assertEquals(new HudRect(200, 100, 90, 45), model.rect("odds", W, H), "the top-left corner stays");
		model.release(W, H);
		assertFalse(model.scaling(), "released");
		assertEquals(new HudRect(200, 100, 90, 45), model.rect("odds", W, H), "kept on release");
	}

	/** AC-HUD-16 [A]: the top-left handle dragged by (+15, +6) gives 0.8, the larger of 45/60 and 24/30, about the bottom-right. */
	@Test
	void theTopLeftHandleScalesAboutTheBottomRight() {
		HudEditorModel model = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1));
		double[] grab = handle(model, 0);
		model.press(grab[0], grab[1], W, H);
		model.drag(grab[0] + 15, grab[1] + 6, W, H, false);
		assertEquals(0.8, model.positions().get("odds").scale());
		assertEquals(new HudRect(212, 106, 48, 24), model.rect("odds", W, H), "the bottom-right corner (260, 130) stays");
	}

	/** AC-HUD-16 [A]: a drag past either end stays at 0.5 or 3.0, the opposite corner fixed. */
	@Test
	void aHandleDragStaysBetweenHalfAndThree() {
		HudEditorModel model = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1));
		double[] grab = handle(model, 3);
		model.press(grab[0], grab[1], W, H);
		model.drag(205, 102, W, H, false);
		assertEquals(0.5, model.positions().get("odds").scale(), "shrunk past the end");
		assertEquals(new HudRect(200, 100, 30, 15), model.rect("odds", W, H));
		model.drag(150, 50, W, H, false);
		assertEquals(0.5, model.positions().get("odds").scale(), "past the fixed corner too");
		model.drag(500, 300, W, H, false);
		assertEquals(3.0, model.positions().get("odds").scale(), "grown past the end");
		assertEquals(new HudRect(200, 100, 180, 90), model.rect("odds", W, H));
	}

	/**
	 * AC-HUD-16 [A], EC-HUD-12: a drag that would take the element out of the window stops at the scale where it still fits,
	 * on the 0.1 step, the opposite corner fixed; at the window's top-left corner the same for the top-left handle.
	 */
	@Test
	void aHandleDragStopsAtTheWindowEdge() {
		HudEditorModel model = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 880, 100, 1));
		double[] grab = handle(model, 3);
		model.press(grab[0], grab[1], W, H);
		model.drag(959, 160, W, H, false);
		assertEquals(1.3, model.positions().get("odds").scale(), "80 px to the right edge: at most 1.33, so 1.3");
		assertEquals(new HudRect(880, 100, 78, 39), model.rect("odds", W, H), "the top-left corner stays");
		model.release(W, H);

		// Grabbed 2 px inside the corner and dragged to the window's corner: 1.4 wanted (42 / 30), 1.3 fits.
		HudEditorModel corner = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 20, 10, 1));
		corner.press(22, 12, W, H);
		assertTrue(corner.scaling(), "2 px inside the corner takes the handle");
		corner.drag(0, 0, W, H, false);
		assertEquals(1.3, corner.positions().get("odds").scale(), "1.4 would leave the window at the left and top: 1.3");
		assertEquals(new HudRect(2, 1, 78, 39), corner.rect("odds", W, H), "the bottom-right corner (80, 40) stays");

		// Flush with the right edge already: dragged outward, it stays at 1.0.
		HudEditorModel flush = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 900, 100, 1));
		double[] bottomRight = handle(flush, 3);
		flush.press(bottomRight[0], bottomRight[1], W, H);
		flush.drag(bottomRight[0] + 10, bottomRight[1] + 5, W, H, false);
		assertEquals(1.0, flush.positions().get("odds").scale(), "no room to the right: it stays");
		assertEquals(new HudRect(900, 100, 60, 30), flush.rect("odds", W, H));
	}

	/** REQ-HUD-15: a ratio exactly halfway between two steps rounds up (69 of 60 px: 1.15, so 1.2), as 45 of 60 (0.75) does. */
	@Test
	void aHalfwayRatioRoundsUp() {
		HudEditorModel model = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1));
		double[] grab = handle(model, 3);
		model.press(grab[0], grab[1], W, H);
		model.drag(grab[0] + 9, grab[1], W, H, false);
		assertEquals(1.2, model.positions().get("odds").scale());
	}

	/**
	 * REQ-HUD-15: an element of an odd size (61 × 9) scaled by its top-left handle to 0.5 (30.5 × 4.5, drawn 31 × 5) keeps its
	 * drawn bottom-right corner (161, 59) exactly.
	 */
	@Test
	void anOddSizeKeepsItsDrawnCorner() {
		sizes.put("odds", new double[] {61, 9});
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 100, 50, 1), XP_DEFAULT);
		model.press(130, 54, W, H);
		model.release(W, H);
		model.press(100, 50, W, H);
		assertTrue(model.scaling());
		model.drag(131, 55, W, H, false);
		assertEquals(0.5, model.positions().get("odds").scale());
		HudRect rect = model.rect("odds", W, H);
		assertEquals(161, rect.x() + Math.ceil(rect.w()), 0, "right edge as drawn");
		assertEquals(59, rect.y() + Math.ceil(rect.h()), 0, "bottom edge as drawn");
	}

	/** REQ-HUD-15: a 4 × 4 element keeps a middle a press moves; its handles reach only a quarter of its side (1 GUI px). */
	@Test
	void aSmallElementCanStillBeMoved() {
		sizes.put("odds", new double[] {8, 8});
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 0.5), XP_DEFAULT);
		model.press(202, 102, W, H);
		model.release(W, H);
		assertEquals("odds", model.selected());
		model.press(202, 102, W, H);
		assertTrue(model.dragging() && !model.scaling(), "its middle, 2.8 px from each corner, moves it");
		model.release(W, H);
		model.press(200.5, 100.5, W, H);
		assertTrue(model.scaling(), "0.7 px from its corner scales it");
	}

	/** REQ-HUD-15: during a handle drag the arrow keys do nothing, and Cancel ends it. */
	@Test
	void aHandleDragIgnoresNudgesAndEndsOnCancel() {
		HudEditorModel model = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1));
		double[] grab = handle(model, 3);
		model.press(grab[0], grab[1], W, H);
		HudPosition before = model.positions().get("odds");
		model.nudge(10, 0, W, H);
		assertEquals(before, model.positions().get("odds"), "no nudge while scaling");
		model.cancel();
		assertFalse(model.scaling(), "Cancel ends it");
	}

	/** REQ-HUD-15: a handle drag does not snap, shows no guide, and on release the element is re-anchored as after a move. */
	@Test
	void aHandleDragDoesNotSnapAndReanchorsOnRelease() {
		HudEditorModel model = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 400, 100, 1));
		double[] grab = handle(model, 3);
		model.press(grab[0], grab[1], W, H);
		model.drag(478, 139, W, H, true);
		assertEquals(1.3, model.positions().get("odds").scale());
		assertEquals(478, model.rect("odds", W, H).x() + model.rect("odds", W, H).w(), 0, "its right edge 2 px from the centre line, not snapped");
		assertTrue(model.guides(W, H).isEmpty(), "no guide");
		assertEquals(HudAnchor.TOP_LEFT, model.positions().get("odds").anchor(), "before the release");
		model.release(W, H);
		assertEquals(0.5, model.positions().get("odds").anchor().fx(), "centre 439 lies in the middle third: anchored there");
		assertEquals(new HudRect(400, 100, 78, 39), model.rect("odds", W, H), "where it was drawn");
	}

	/** REQ-HUD-15: the handles of an element at the window's edge are drawn just inside it (2.5 GUI px, half a handle, in). */
	@Test
	void handlesAtTheWindowEdgeStayInside() {
		HudEditorModel model = boxAt(new HudPosition(HudAnchor.TOP_LEFT, 0, 0, 1));
		assertArrayEquals(new double[] {2.5, 2.5, 60, 2.5, 2.5, 30, 60, 30}, model.handleCentres(W, H));
		assertEquals(0, model.handleAt(1, 1, W, H), "the corner pixel takes the top-left handle");
	}

	/** REQ-HUD-15: a handle takes the press before the element under it; only the selected element has handles. */
	@Test
	void aHandleTakesThePressBeforeAnElement() {
		sizes.put("odds", new double[] {60, 30});
		HudEditorModel model = model(new HudPosition(HudAnchor.TOP_LEFT, 200, 100, 1), new HudPosition(HudAnchor.TOP_LEFT, 258, 128, 1));
		model.press(210, 110, W, H);
		model.release(W, H);
		assertEquals("xp", model.at(259, 129, W, H), "xp lies on odds' bottom-right corner");
		model.press(259, 129, W, H);
		assertTrue(model.scaling(), "the handle of the selected odds takes the press");
		assertEquals("odds", model.selected());
	}
}
