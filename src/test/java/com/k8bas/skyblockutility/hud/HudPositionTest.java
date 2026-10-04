package com.k8bas.skyblockutility.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-HUD-03, REQ-HUD-04, REQ-HUD-05 (T2.7): the position model, pure. */
class HudPositionTest {
	private static final double W = 960;
	private static final double H = 540;

	@Test
	void eachAnchorPlacesTheElementAtItsScreenPoint() {
		assertRect(new HudRect(10, 20, 50, 30), new HudPosition(HudAnchor.TOP_LEFT, 10, 20, 1).place(W, H, 50, 30));
		assertRect(new HudRect(W - 50 - 10, 20, 50, 30), new HudPosition(HudAnchor.TOP_RIGHT, -10, 20, 1).place(W, H, 50, 30));
		assertRect(new HudRect(W / 2 - 25, H / 2 - 15, 50, 30), new HudPosition(HudAnchor.CENTER, 0, 0, 1).place(W, H, 50, 30));
		assertRect(new HudRect(W / 2 - 25 + 4, H - 30 - 6, 50, 30), new HudPosition(HudAnchor.BOTTOM_CENTER, 4, -6, 1).place(W, H, 50, 30));
	}

	/** AC-HUD-02 [A]: over all 9 anchors, a drop picks the anchor of the third the centre is in, and nothing moves. */
	@Test
	void reanchoringPicksTheThirdOfTheCentreAndKeepsTheRectangle() {
		double w = 50;
		double h = 30;
		for (HudAnchor start : HudAnchor.values()) {
			for (int col = 0; col < 3; col++) {
				for (int row = 0; row < 3; row++) {
					// An element whose centre is in the middle of third (col, row), stored against `start`.
					double left = (col + 0.5) * W / 3 - w / 2 + 7;
					double top = (row + 0.5) * H / 3 - h / 2 - 5;
					HudPosition stored = new HudPosition(start, (int) Math.round(left - start.fx() * (W - w)),
							(int) Math.round(top - start.fy() * (H - h)), 1);
					HudRect before = stored.place(W, H, w, h);
					HudPosition dropped = stored.reanchored(W, H, w, h);
					assertEquals(HudAnchor.of(col, row), dropped.anchor(), start + " dropped in third " + col + "," + row);
					HudRect after = dropped.place(W, H, w, h);
					assertEquals(before.x(), after.x(), 0.5, start + " -> " + dropped.anchor() + ": x");
					assertEquals(before.y(), after.y(), 0.5, start + " -> " + dropped.anchor() + ": y");
				}
			}
		}
	}

	/** REQ-HUD-03 in drawn pixels: a drop never moves the element by a pixel, also with odd sizes and scaled widths. */
	@Test
	void reanchoringKeepsTheDrawnPixel() {
		double[][] cases = {{427, 66, 150}, {427, 66.5, 150.3}, {960, 51.25, 333.75}, {213, 41, 86}, {854, 120.5, 366.5}};
		for (double[] c : cases) {
			double screen = c[0];
			double w = c[1];
			for (HudAnchor start : new HudAnchor[] {HudAnchor.TOP_LEFT, HudAnchor.TOP_CENTER, HudAnchor.TOP_RIGHT}) {
				HudPosition at = new HudPosition(start, (int) Math.round(c[2] - start.fx() * (screen - w)), 7, 1);
				HudPosition dropped = at.reanchored(screen, 240, w, 20);
				HudRect before = HudRect.onPixels(at.place(screen, 240, w, 20), screen, 240);
				HudRect after = HudRect.onPixels(dropped.place(screen, 240, w, 20), screen, 240);
				assertEquals(before.x(), after.x(), 0, "screen " + screen + ", width " + w + ", " + at + " -> " + dropped);
				assertEquals(before.y(), after.y(), 0);
			}
		}
	}

	/** REQ-HUD-04 in drawn pixels: whole pixels, and never past the right or bottom edge for a fractional size. */
	@Test
	void pixelsStayInsideTheWindow() {
		HudRect right = HudRect.onPixels(new HudPosition(HudAnchor.BOTTOM_RIGHT, 0, 0, 1).place(960, 540, 51.25, 28.75), 960, 540);
		assertEquals(908, right.x(), 0, "rounded inward at the right edge");
		assertTrue(right.x() + right.w() <= 960 && right.y() + right.h() <= 540, right.toString());
		HudRect clamped = HudRect.onPixels(new HudRect(359.5, 10.4, 67.5, 20), 427, 240);
		assertEquals(359, clamped.x(), 0);
		assertEquals(10, clamped.y(), 0);
		assertEquals(0, HudRect.onPixels(new HudRect(-3.4, -0.6, 10, 10), 427, 240).x(), 0);
		assertEquals(0, HudRect.onPixels(new HudRect(40, 5, 500, 10), 427, 240).x(), 0, "wider than the window: from its left");
	}

	/** AC-HUD-02 [A]: an element grows away from its anchor. */
	@Test
	void aRightAnchoredElementKeepsItsRightEdgeWhenItGrows() {
		HudPosition position = new HudPosition(HudAnchor.CENTER_RIGHT, -8, 0, 1);
		HudRect narrow = position.place(W, H, 50, 20);
		HudRect wide = position.place(W, H, 80, 20);
		assertEquals(narrow.x() + narrow.w(), wide.x() + wide.w(), 1e-9);
		assertEquals(W - 8, wide.x() + wide.w(), 1e-9);
		HudPosition bottom = new HudPosition(HudAnchor.BOTTOM_LEFT, 3, -3, 1);
		assertEquals(bottom.place(W, H, 50, 20).y() + 20, bottom.place(W, H, 50, 60).y() + 60, 1e-9, "and a bottom one its bottom edge");
	}

	/** REQ-HUD-04: drawn fully inside the window; one larger than the window from its top-left. */
	@Test
	void theDrawTimeClampShiftsInsideAndNeverChangesThePosition() {
		HudPosition position = new HudPosition(HudAnchor.TOP_LEFT, 900, 520, 1);
		HudRect placed = position.place(W, H, 100, 40);
		assertRect(new HudRect(W - 100, H - 40, 100, 40), HudRect.onPixels(placed, W, H));
		assertRect(new HudRect(0, 0, 100, 40), HudRect.onPixels(new HudPosition(HudAnchor.TOP_LEFT, -30, -9, 1).place(W, H, 100, 40), W, H));
		assertRect(new HudRect(0, 10, 1200, 40), HudRect.onPixels(new HudRect(-50, 10, 1200, 40), W, H), "wider than the window: from its left");
		assertRect(new HudRect(0, 0, 1200, 600), HudRect.onPixels(new HudRect(300, 200, 1200, 600), W, H));
		assertEquals(new HudPosition(HudAnchor.TOP_LEFT, 900, 520, 1), position, "the stored position is untouched");
	}

	/** REQ-HUD-05: 0.5-3.0, steps of 0.1, stored to 0.01. */
	@Test
	void theScaleIsHeldToItsRangeAndRoundedToHundredths() {
		assertEquals(0.5, HudScale.clamp(0.1), 1e-9);
		assertEquals(3.0, HudScale.clamp(10), 1e-9);
		assertEquals(1.23, HudScale.clamp(1.2345), 1e-9);
		assertEquals(1.3, HudScale.step(1.0, 3), 1e-9);
		assertEquals(3.0, HudScale.step(3.0, 1), 1e-9);
		assertEquals(0.5, HudScale.step(0.5, -1), 1e-9);
		assertEquals(0.5, new HudPosition(HudAnchor.CENTER, 0, 0, 0.2).scale(), 1e-9, "a position holds its scale in range");
	}

	private static void assertRect(HudRect expected, HudRect actual) {
		assertRect(expected, actual, "");
	}

	private static void assertRect(HudRect expected, HudRect actual, String what) {
		assertEquals(expected.x(), actual.x(), 1e-9, what + " x");
		assertEquals(expected.y(), actual.y(), 1e-9, what + " y");
		assertEquals(expected.w(), actual.w(), 1e-9, what + " w");
		assertEquals(expected.h(), actual.h(), 1e-9, what + " h");
	}
}
