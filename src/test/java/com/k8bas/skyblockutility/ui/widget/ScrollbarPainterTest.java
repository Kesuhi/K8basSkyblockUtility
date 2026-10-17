package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.UiTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-37 [A] (REQ-UI-36, T3.27): one scrollbar for every scrolling region. Its hit area is at least 5 GUI px wide in every
 * state, idle included; the thumb is 3 GUI px wide and inset 2 GUI px from the region's right edge, and on hover its drawn
 * width eases to 5 GUI px over 150 ms (strictly between at 75 ms, 5 from 150 ms on) and back after the pointer leaves.
 */
class ScrollbarPainterTest {
	private final long[] now = {1_000};
	private final boolean[] motion = {true};
	private final ScrollbarPainter painter = new ScrollbarPainter(UiTime.of(() -> now[0], () -> motion[0]));

	/** The hit area does not depend on the state; it takes in every column of the widened thumb, and only up to the region's edge. */
	@Test
	void theHitAreaCoversTheWidenedThumb() {
		// A region from x 100, 100 wide: the widened thumb, 5 GUI px with its right edge 2 in, covers columns 193 to 197.
		int hits = 0;
		for (int column = 0; column < 300; column++) {
			boolean hit = ScrollbarPainter.inHitArea(100, 100, column + 0.5);
			hits += hit ? 1 : 0;
			if (column >= 193 && column <= 197) {
				assertTrue(hit, "column " + column + " of the widened thumb");
			}
			if (column >= 200 || column < 193) {
				assertFalse(hit, "column " + column + " is not the scrollbar's");
			}
		}
		assertTrue(hits >= 5, "at least 5 GUI px wide: " + hits);
	}

	@Test
	void theThumbIsInsetFromTheEdge() {
		assertEquals(3F, painter.width(false), "idle: 3 GUI px");
		assertEquals(198F, ScrollbarPainter.thumbRight(100, 100), "its right edge 2 GUI px in from the region's");
	}

	/** The widgets' wiring: a press on any drawn column of the widened thumb grabs it (also while it is still idle), not a row. */
	@Test
	void aListGrabsItsThumbOnEveryColumnItIsDrawnOn() {
		java.util.List<Integer> clicked = new java.util.ArrayList<>();
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, clicked::add);
		list.setBounds(0, 0, 160, 200);
		for (int column = 153; column <= 157; column++) {
			list.setScroll(0);
			assertTrue(list.press(column + 0.5, 5, 0), "column " + column);
			list.drag(column + 0.5, 105);
			assertTrue(list.rows().scroll() > 0, "column " + column + " grabbed the thumb: " + list.rows().scroll());
			list.release(column + 0.5, 105);
		}
		assertEquals(java.util.List.of(), clicked, "no row was picked");
		list.setScroll(0);
		list.press(152.5, 5, 0);
		assertEquals(java.util.List.of(0), clicked, "left of the thumb is a row");
	}

	@Test
	void hoverWidensItOver150MsAndLeavingNarrowsIt() {
		assertEquals(3F, painter.width(false));
		painter.width(true);
		assertEquals(3F, painter.width(true), "0 ms");
		now[0] += 75;
		float halfway = painter.width(true);
		assertTrue(halfway > 3F && halfway < 5F, "75 ms: " + halfway);
		now[0] += 75;
		assertEquals(5F, painter.width(true), "150 ms");
		now[0] += 500;
		assertEquals(5F, painter.width(true), "and stays");
		painter.width(false);
		now[0] += 75;
		float back = painter.width(false);
		assertTrue(back > 3F && back < 5F, "leaving, 75 ms: " + back);
		now[0] += 75;
		assertEquals(3F, painter.width(false), "150 ms after leaving");
	}

	/**
	 * REQ-UI-47: the disc mask chosen for the thumb and its track is the idle width's while idle and the widened width's from
	 * the first frame it widens, so the in-between widths need no masks of their own. ConfigMotionGameTest counts the masks a
	 * drawn scrollbar adds.
	 */
	@Test
	void widthsInBetweenBorrowTheWidenedMask() {
		for (int scale = 1; scale <= 10; scale++) {
			java.util.Set<Integer> masks = new java.util.TreeSet<>();
			for (int step = 0; step <= 1000; step++) {
				masks.add(ScrollbarPainter.maskPx(ScrollbarPainter.IDLE_WIDTH + (ScrollbarPainter.WIDE_WIDTH - ScrollbarPainter.IDLE_WIDTH) * step / 1000F,
						scale));
			}
			assertEquals(java.util.Set.of(ScrollbarPainter.IDLE_WIDTH * scale, ScrollbarPainter.WIDE_WIDTH * scale), masks,
					"scale " + scale + ": the masks of the idle and the widened thumb only");
			assertEquals(ScrollbarPainter.IDLE_WIDTH * scale, ScrollbarPainter.maskPx(ScrollbarPainter.IDLE_WIDTH, scale), "idle, its own");
			assertEquals(ScrollbarPainter.WIDE_WIDTH * scale, ScrollbarPainter.maskPx(Math.nextUp((float) ScrollbarPainter.IDLE_WIDTH), scale),
					"the widened thumb's from the first frame");
		}
	}

	@Test
	void withAnimationsOffItWidensAtOnce() {
		motion[0] = false;
		assertEquals(3F, painter.width(false));
		assertEquals(5F, painter.width(true));
		assertEquals(3F, painter.width(false));
	}
}
