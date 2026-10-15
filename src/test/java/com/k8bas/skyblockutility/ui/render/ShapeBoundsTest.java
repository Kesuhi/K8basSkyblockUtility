package com.k8bas.skyblockutility.ui.render;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-27 (REQ-UI-27, T3.15): a rounded shape reaches the game as one element, or two back to back with identical
 * bounds, and its bounds hold every pixel it draws, rounded outward, at whole and fractional positions and scales.
 */
class ShapeBoundsTest {
	/** Sizes for the fractional poses; at whole positions every size from 4 to 30 runs. */
	private static final int[] SIZES = {4, 5, 7, 10, 12, 13, 16, 20, 25, 30};
	private static final float[] POSE_SCALES = {0.90F, 0.95F, 1.00F};

	@Test
	void everySizeAtWholePositions() {
		int plans = 0;
		for (int scale = 1; scale <= 4; scale++) {
			Matrix3x2f outer = new Matrix3x2f().translate(37, 21);
			for (int w = 4; w <= 30; w++) {
				for (int h = 4; h <= 30; h++) {
					for (int radius = 0; radius <= 11; radius++) {
						int r = radius == 11 ? h / 2 : radius;
						for (int thickness = 0; thickness <= 1; thickness++) {
							for (boolean smooth : new boolean[] {false, true}) {
								check(outer, scale, w, h, r, thickness, smooth);
								plans++;
							}
						}
					}
				}
			}
		}
		assertTrue(plans > 100_000, "the sweep ran: " + plans);
	}

	@Test
	void sampledSizesAtFractionalPositionsAndScales() {
		int plans = 0;
		for (int scale = 1; scale <= 4; scale++) {
			for (float poseScale : POSE_SCALES) {
				for (int kx = 0; kx < scale; kx++) {
					for (int ky = 0; ky < scale; ky++) {
						Matrix3x2f outer = new Matrix3x2f().translate(37 + (float) kx / scale, 21 + (float) ky / scale).scale(poseScale);
						for (int w : SIZES) {
							for (int h : SIZES) {
								for (int radius = 0; radius <= 11; radius++) {
									int r = radius == 11 ? h / 2 : radius;
									for (int thickness = 0; thickness <= 1; thickness++) {
										for (boolean smooth : new boolean[] {false, true}) {
											check(outer, scale, w, h, r, thickness, smooth);
											plans++;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		assertTrue(plans > 100_000, "the sweep ran: " + plans);
	}

	private static void check(Matrix3x2f outer, int scale, int w, int h, int r, int t, boolean smooth) {
		int x = 3;
		int y = 5;
		String what = "scale " + scale + ", " + w + "x" + h + " r " + r + " t " + t + (smooth ? " smooth" : "") + ", pose " + outer;
		ShapePlan.Result plan = ShapePlan.of(outer, x, y, w, h, r, t, scale, smooth, null);
		List<ShapePlan.Element> elements = plan.elements();
		assertFalse(elements.isEmpty(), what);
		assertTrue(elements.size() <= 2, what + ": " + elements.size() + " elements");
		if (elements.size() == 2) {
			assertTrue(elements.get(0).textured() && !elements.get(1).textured(), what + ": mask corners first, then the straight parts");
			assertArrayEquals(box(elements.get(0)), box(elements.get(1)), what + ": identical bounds");
		}
		if (!smooth) {
			assertEquals(1, elements.size(), what + ": the fill-based shape is one element");
		}
		int[] expected = ShapePlan.bounds(outer, x, y, x + w, y + h);
		Matrix3x2f inner = new Matrix3x2f(outer).translate(x, y).scale(1F / scale, 1F / scale);
		for (ShapePlan.Element element : elements) {
			assertArrayEquals(expected, box(element), what + ": the bounds of the whole shape");
			int[] q = element.quads();
			for (int i = 0; i < q.length; i += 4) {
				float[] drawn = transformed(inner, q[i], q[i + 1], q[i + 2], q[i + 3]);
				assertTrue(drawn[0] >= element.left() - 1e-3 && drawn[1] >= element.top() - 1e-3
						&& drawn[2] <= element.right() + 1e-3 && drawn[3] <= element.bottom() + 1e-3, what + ": a quad outside the bounds");
			}
		}
	}

	/** AC-UI-27: bounds lie inside the scissor, and a shape wholly outside it submits nothing. */
	@Test
	void theScissorCutsTheBoundsOrDropsTheShape() {
		for (int scale = 1; scale <= 4; scale++) {
			for (boolean smooth : new boolean[] {false, true}) {
				Matrix3x2f outer = new Matrix3x2f().translate(0.5F, 0.25F);
				// 10.5..41.5 x 20.25..37.25 rounds outward to 10..42 x 20..38; the scissor keeps 30..60 x 0..30.
				List<ShapePlan.Element> cut = ShapePlan.of(outer, 10, 20, 31, 17, 4, 0, scale, smooth, new int[] {30, 0, 60, 30}).elements();
				assertFalse(cut.isEmpty(), "scale " + scale);
				for (ShapePlan.Element element : cut) {
					assertArrayEquals(new int[] {30, 20, 42, 30}, box(element), "scale " + scale + (smooth ? " smooth" : ""));
				}
				assertTrue(ShapePlan.of(outer, 10, 20, 31, 17, 4, 0, scale, smooth, new int[] {100, 100, 200, 200}).elements().isEmpty(),
						"wholly outside the scissor: nothing");
				assertTrue(ShapePlan.of(outer, 10, 20, 31, 17, 4, 0, scale, smooth, new int[] {42, 0, 60, 30}).elements().isEmpty(),
						"only touching the scissor: nothing");
			}
		}
	}

	@Test
	void boundsRoundOutwardWhereTheGameWouldComeOutShort() {
		// From x 2.5 to 6.5: the game's floor(min) + ceil(size) gives 2..6 and misses the last half pixel.
		assertArrayEquals(new int[] {2, 3, 7, 6}, ShapePlan.bounds(new Matrix3x2f().translate(2.5F, 3.25F), 0, 0, 4, 2));
		assertArrayEquals(new int[] {0, 0, 9, 9}, ShapePlan.bounds(new Matrix3x2f().scale(0.9F), 0, 0, 10, 10));
		assertEquals(null, ShapePlan.bounds(new Matrix3x2f(), 0, 0, 0, 5), "no area, no element");
	}

	@Test
	void nothingIsPlannedForAnEmptyShape() {
		assertTrue(ShapePlan.of(new Matrix3x2f(), 0, 0, 0, 10, 4, 0, 2, true, null).elements().isEmpty());
		assertTrue(ShapePlan.of(new Matrix3x2f(), 0, 0, 10, -1, 4, 0, 2, false, null).elements().isEmpty());
	}

	private static int[] box(ShapePlan.Element element) {
		assertNotNull(element);
		return new int[] {element.left(), element.top(), element.right(), element.bottom()};
	}

	private static float[] transformed(Matrix3x2f pose, int x0, int y0, int x1, int y1) {
		Vector2f a = pose.transformPosition(x0, y0, new Vector2f());
		Vector2f b = pose.transformPosition(x1, y1, new Vector2f());
		return new float[] {Math.min(a.x, b.x), Math.min(a.y, b.y), Math.max(a.x, b.x), Math.max(a.y, b.y)};
	}
}
