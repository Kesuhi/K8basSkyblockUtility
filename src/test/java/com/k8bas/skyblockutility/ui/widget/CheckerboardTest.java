package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-64 (REQ-UI-60, T3.50): the checkerboard's cells are 3 GUI px, start at the shape's top-left corner with a light one
 * and alternate; they cover the board once each, light and dark apart (so a dimmed board keeps its pattern); a corner cuts
 * its cells row by row along its curve; a dimmed swatch's board is dimmed by the same factor as its colour.
 */
class CheckerboardTest {
	@Test
	void cellsOf3GuiPxFromTheTopLeftCorner() {
		List<int[]> cells = Checkerboard.cells(12, 9);
		assertEquals(3, Checkerboard.CELL);
		assertEquals(12, cells.size(), "4 × 3 cells");
		assertArrayEquals(new int[] {0, 0, 3, 3, 0}, cells.get(0), "a light cell at the corner");
		assertArrayEquals(new int[] {3, 0, 3, 3, 1}, cells.get(1), "a dark one beside it");
		for (int[] cell : cells) {
			assertEquals(0, cell[0] % 3, "on the 3 GUI px grid from the left");
			assertEquals(0, cell[1] % 3, "and from the top");
			assertEquals((cell[0] / 3 + cell[1] / 3) % 2, cell[4], "alternating");
		}
		// The last column and row are cut at the edges.
		assertTrue(Checkerboard.cells(10, 8).stream().anyMatch(c -> c[0] == 9 && c[1] == 6 && c[2] == 1 && c[3] == 2));
	}

	/** Every GUI px of the board lies in exactly one cell: light and dark never overlap. */
	@Test
	void cellsCoverTheBoardOnce() {
		int w = 24;
		int h = 14;
		int[][] covered = new int[h][w];
		for (int[] cell : Checkerboard.cells(w, h)) {
			for (int y = cell[1]; y < cell[1] + cell[3]; y++) {
				for (int x = cell[0]; x < cell[0] + cell[2]; x++) {
					covered[y][x]++;
				}
			}
		}
		for (int[] row : covered) {
			for (int count : row) {
				assertEquals(1, count);
			}
		}
	}

	/** A corner of radius 4 GUI px cuts its rows along the circle: most at the edge, none from the radius on, at GUI scales 1-4. */
	@Test
	void aCornerCutsItsRowsAlongTheCurve() {
		for (int scale = 1; scale <= 4; scale++) {
			int previous = Integer.MAX_VALUE;
			for (int row = 0; row < 4 * scale; row++) {
				int inset = Checkerboard.rowInset(4, row, scale);
				assertTrue(inset <= previous, "scale " + scale + ", row " + row + ": " + inset + " after " + previous);
				previous = inset;
			}
			assertTrue(Checkerboard.rowInset(4, 0, scale) > 0, "the edge row is cut");
			assertEquals(0, Checkerboard.rowInset(4, 4 * scale, scale), "none from the radius on");
		}
	}

	/** A dimmed swatch draws its board through the same fade as its colour. */
	@Test
	void aDimmedSwatchDimsItsBoard() {
		int[] colour = {0x80FF0000};
		ColorSwatch swatch = new ColorSwatch(Binding.of(() -> colour[0], value -> colour[0] = value), true, "Colour", null, () -> { }, () -> { });
		swatch.setDimmed(true);
		int[] board = swatch.boardColoursForTests();
		int faded = swatch.fadedForTests(0xFF000000);
		assertEquals(faded >>> 24, board[0] >>> 24, "the light cells at the dimmed alpha");
		assertEquals(faded >>> 24, board[1] >>> 24, "and the dark ones");
		assertTrue(faded >>> 24 < 0xFF, "dimmed: " + Integer.toHexString(faded));
	}
}
