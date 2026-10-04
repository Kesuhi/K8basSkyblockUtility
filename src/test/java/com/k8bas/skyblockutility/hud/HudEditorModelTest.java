package com.k8bas.skyblockutility.hud;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-HUD-05, REQ-HUD-08 (T2.8): the HUD editor's model, AC-HUD-04 [A] and EC-HUD-01/02/03/06. */
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
}
