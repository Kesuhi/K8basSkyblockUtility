package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-20, AC-UI-18 (T2.3c): a list that lays out and hit-tests only its visible rows. */
class VirtualListTest {
	/** AC-UI-18: 5,000 rows of 18 px in a 200 px viewport; at most ceil(200/18)+1 = 13 rows, and a click's y gives the right row. */
	@Test
	void fiveThousandRowsLayOutAtMostThirteen() {
		VirtualRows rows = new VirtualRows(18);
		rows.setCount(5000);
		rows.setViewport(200);
		List<Integer> offsets = new ArrayList<>(List.of(0, 1, 9, 17, 18, 19, 35, 36, 100, 4321, rows.maxScroll() - 1, rows.maxScroll()));
		for (int offset = 0; offset <= rows.maxScroll(); offset += 997) {
			offsets.add(offset);
		}
		for (int offset : offsets) {
			rows.setScroll(offset);
			VirtualRows.Range visible = rows.visible();
			assertTrue(visible.count() <= 13, offset + ": " + visible);
			assertEquals(offset / 18, visible.first(), "the first row");
			for (int y = 0; y < 200; y++) {
				int row = rows.rowAt(y);
				assertEquals((offset + y) / 18, row, offset + " y " + y);
				assertTrue(row >= visible.first() && row < visible.end(), "a hit row is laid out");
			}
		}
		assertEquals(5000 * 18 - 200, rows.maxScroll());
	}

	@Test
	void outsideTheRowsHitsNothing() {
		VirtualRows rows = new VirtualRows(18);
		rows.setCount(3);
		rows.setViewport(200);
		assertEquals(0, rows.maxScroll(), "a short list does not scroll");
		assertEquals(2, rows.rowAt(53));
		assertEquals(-1, rows.rowAt(54), "below the last row");
		assertEquals(-1, rows.rowAt(-1));
		assertEquals(-1, rows.rowAt(200), "below the viewport");
		assertEquals(new VirtualRows.Range(0, 3), rows.visible());
		rows.setCount(0);
		assertEquals(0, rows.visible().count());
		assertEquals(-1, rows.rowAt(0));
	}

	@Test
	void scrollIsHeldInRangeAndFollowsTheCount() {
		VirtualRows rows = new VirtualRows(18);
		rows.setCount(100);
		rows.setViewport(200);
		rows.setScroll(-5);
		assertEquals(0, rows.scroll());
		rows.setScroll(1_000_000);
		assertEquals(rows.maxScroll(), rows.scroll());
		rows.setCount(20);
		assertEquals(20 * 18 - 200, rows.scroll(), "a shorter list pulls the scroll back");
		rows.setViewport(400);
		assertEquals(0, rows.scroll(), "a taller viewport too");
	}

	@Test
	void ensureVisibleScrollsAsLittleAsNeeded() {
		VirtualRows rows = new VirtualRows(18);
		rows.setCount(100);
		rows.setViewport(200);
		rows.ensureVisible(5);
		assertEquals(0, rows.scroll(), "already in view");
		rows.ensureVisible(20);
		assertEquals(21 * 18 - 200, rows.scroll(), "its bottom at the viewport's bottom");
		rows.ensureVisible(3);
		assertEquals(3 * 18, rows.scroll(), "its top at the viewport's top");
	}

	@Test
	void theThumbMapsToTheScrollBothWays() {
		VirtualRows rows = new VirtualRows(18);
		rows.setCount(5000);
		rows.setViewport(200);
		VirtualRows.Thumb top = rows.thumb(200);
		assertEquals(0, top.offset());
		assertEquals(VirtualRows.MIN_THUMB, top.length(), "a very long list still has a thumb to grab");
		rows.setScroll(rows.maxScroll());
		assertEquals(200 - VirtualRows.MIN_THUMB, rows.thumb(200).offset(), "at the end");
		rows.setScroll(rows.scrollForThumb(0, 200));
		assertEquals(0, rows.scroll());
		rows.setScroll(rows.scrollForThumb(200, 200));
		assertEquals(rows.maxScroll(), rows.scroll(), "the thumb dragged to the bottom shows the last row");
		assertEquals(4999, rows.visible().end() - 1);
	}

	@Test
	void theWidgetScrollsClicksAndDragsItsThumb() {
		List<Integer> clicked = new ArrayList<>();
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, clicked::add);
		list.setBounds(10, 20, 160, 200);
		assertTrue(list.press(50, 20 + 2 * 18 + 5, 0));
		assertEquals(List.of(2), clicked);
		assertTrue(list.scroll(50, 100, -1, false), "the wheel scrolls it");
		assertEquals(3 * 18, list.rows().scroll(), "three rows a notch");
		assertTrue(list.scroll(50, 100, -1, true));
		assertEquals(4 * 18, list.rows().scroll(), "one with Shift");
		assertFalse(list.scroll(500, 100, -1, false), "not when the mouse is elsewhere");
		list.press(50, 20 + 5, 0);
		assertEquals(4, clicked.get(1), "the click's row after scrolling");
		assertFalse(list.press(50, 20 + 5, 1), "only the left button picks");

		// The thumb on the right edge: grabbed and dragged to the bottom.
		int thumbX = 10 + 160 - 2;
		int thumbTop = 20 + list.rows().thumb(200).offset();
		assertTrue(list.press(thumbX, thumbTop + 2, 0));
		assertEquals(2, clicked.size(), "grabbing the thumb picks no row");
		list.drag(thumbX, 20 + 400);
		list.release(thumbX, 20 + 400);
		assertEquals(list.rows().maxScroll(), list.rows().scroll());
		// A press on the track below the thumb jumps there.
		list.press(thumbX, 20 + 10, 0);
		list.release(thumbX, 20 + 10);
		assertTrue(list.rows().scroll() < list.rows().maxScroll() / 10, "a press on the track moves the thumb there: " + list.rows().scroll());
	}

	@Test
	void rowTooltipsFollowTheScrollAndNotTheThumb() {
		VirtualList list = new VirtualList(18, () -> 100, (graphics, font, index, x, y, w, h, hovered) -> { }, index -> { });
		list.setBounds(0, 0, 100, 200);
		list.setTooltip("the list");
		list.setRowTooltip(index -> index % 2 == 0 ? "row " + index : null);
		list.rows().setScroll(10 * 18);
		assertEquals("row 10", list.tooltipAt(50, 5));
		assertEquals("", list.tooltipAt(50, 18 + 5), "null means none");
		assertEquals("the list", list.tooltipAt(98, 5), "the thumb's gutter shows no row's tooltip");
		list.setRowTooltip(null);
		assertEquals("", list.tooltipAt(50, 5));
	}

	/** A grab whose release was lost (the press went elsewhere) must not turn a later row click into a thumb drag. */
	@Test
	void aLostReleaseDoesNotTurnARowClickIntoAThumbDrag() {
		List<Integer> clicked = new ArrayList<>();
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, clicked::add);
		list.setBounds(0, 0, 160, 200);
		list.press(158, 2, 0);
		list.press(50, 5, 0);
		list.drag(50, 190);
		assertEquals(0, list.rows().scroll(), "a drag from a row does not scroll");
		assertEquals(List.of(0), clicked);
	}

	/** A row click with its place in the row, so a button drawn on the row is hit-tested as drawn. */
	@Test
	void aRowClickKnowsWhereInTheRowItFell() {
		List<String> clicks = new ArrayList<>();
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, index -> clicks.add("plain"));
		list.onRowClick((index, x, y, width, height) -> clicks.add(index + " at " + x + "," + y + " of " + width + "x" + height));
		list.setBounds(10, 20, 160, 200);
		list.rows().setScroll(9);
		list.press(15, 20 + 18 + 4, 0);
		assertEquals(List.of("1 at 5.0,13.0 of 154x18"), clicks, "the second row on screen (scrolled half a row), without the scroll bar's width");
	}

	@Test
	void aListThatFitsHasNoThumbAndPassesTheWheelOn() {
		List<Integer> clicked = new ArrayList<>();
		VirtualList list = new VirtualList(18, () -> 4, (graphics, font, index, x, y, w, h, hovered) -> { }, clicked::add);
		list.setBounds(0, 0, 100, 200);
		assertFalse(list.scroll(50, 50, -1, false), "nothing to scroll");
		list.press(98, 5, 0);
		assertEquals(List.of(0), clicked, "the right edge is part of the row when there is no thumb");
		assertTrue(list.press(50, 150, 0), "a click below the rows is taken");
		assertEquals(1, clicked.size(), "but picks nothing");
	}
}
