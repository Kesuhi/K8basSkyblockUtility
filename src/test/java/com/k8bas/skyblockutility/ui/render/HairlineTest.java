package com.k8bas.skyblockutility.ui.render;

import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * AC-UI-34 [A] (REQ-UI-33, T3.22): hairlines are ceil(guiScale / 2) physical px thick, 1, 1, 2, 2, 3 and 3 at GUI scales
 * 1-6, while a focus ring stays 1 GUI px, guiScale physical px; a float position snaps to the 1/guiScale grid.
 */
class HairlineTest {
	@Test
	void theThicknessRule() {
		int[] expected = {1, 1, 2, 2, 3, 3};
		for (int scale = 1; scale <= 6; scale++) {
			assertEquals(expected[scale - 1], Shapes.hairlinePx(scale), "scale " + scale);
			assertEquals(scale, Shapes.focusRingPx(scale), "scale " + scale + ": a focus ring stays 1 GUI px");
		}
	}

	@Test
	void floatPositionsSnapToTheScreenPixelGrid() {
		assertEquals(10.5F, Shapes.snap(10.4F, 2));
		assertEquals(10F, Shapes.snap(10.2F, 2));
		assertEquals(10F + 1F / 3, Shapes.snap(10.3F, 3), 1e-6);
		assertEquals(7F, Shapes.snap(7.1F, 1));
	}

	/**
	 * EC-UI-19: at GUI scale 3 a rounded hairline, as Shapes plans it, is an outline 2 physical px thick: its straight edges
	 * are 2 rows and 2 columns, and the inside is empty.
	 */
	@Test
	void aRoundedHairlineAtScaleThreeIsTwoPixelsThick() {
		int scale = 3;
		int w = 46 * scale;
		int h = 16 * scale;
		ShapePlan.Result plan = ShapePlan.ofPixels(new Matrix3x2f(), 8, 68, w, h, Shapes.RADIUS_CONTROL * scale, Shapes.hairlinePx(scale), Corners.ALL,
				scale, false, null);
		boolean[][] drawn = new boolean[h][w];
		for (ShapePlan.Element element : plan.elements()) {
			int[] q = element.quads();
			for (int i = 0; i < q.length; i += 4) {
				for (int y = q[i + 1]; y < q[i + 3]; y++) {
					for (int x = q[i]; x < q[i + 2]; x++) {
						drawn[y][x] = true;
					}
				}
			}
		}
		int middle = w / 2;
		int rows = 0;
		while (drawn[rows][middle]) {
			rows++;
		}
		int columns = 0;
		while (drawn[h / 2][columns]) {
			columns++;
		}
		assertArrayEquals(new int[] {2, 2}, new int[] {rows, columns}, "the top edge's rows and the left edge's columns");
		assertFalse(drawn[h / 2][middle], "the inside is empty");
	}
}
