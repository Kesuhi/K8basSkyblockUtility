package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-18 (T2.9d, R32): the anti-aliased corner masks, one alpha per screen pixel. */
class CornerMaskTest {
	/** Where a mask pixel is at least half covered is exactly where the fill-based corners draw it. */
	@Test
	void halfCoverageMatchesTheFillCorners() {
		for (int r = 1; r <= 96; r++) {
			boolean[][] fill = cover(Corners.fill(2 * r, 2 * r, r), 2 * r, 2 * r);
			for (int y = 0; y < 2 * r; y++) {
				for (int x = 0; x < 2 * r; x++) {
					assertEquals(fill[y][x], CornerMask.alpha(r, x, y) >= 128, "radius " + r + ", pixel " + x + "," + y);
				}
			}
		}
	}

	@Test
	void insideIsOpaqueOutsideIsClear() {
		assertEquals(255, CornerMask.alpha(6, 5, 5));
		assertEquals(0, CornerMask.alpha(6, 0, 0));
		// RenderKitGameTest's pixels at every scale: the outer corner is clear, (R/2, R/2) is fully covered.
		for (int s = 1; s <= 4; s++) {
			int r = Shapes.RADIUS_CARD * s;
			assertEquals(0, CornerMask.alpha(r, 0, 0), "scale " + s);
			assertEquals(255, CornerMask.alpha(r, r / 2, r / 2), "scale " + s);
		}
	}

	@Test
	void maskIsMirrorSymmetric() {
		for (int r = 1; r <= 24; r++) {
			for (int y = 0; y < 2 * r; y++) {
				for (int x = 0; x < 2 * r; x++) {
					int a = CornerMask.alpha(r, x, y);
					assertEquals(a, CornerMask.alpha(r, y, x));
					assertEquals(a, CornerMask.alpha(r, 2 * r - 1 - x, y));
					assertEquals(a, CornerMask.alpha(r, x, 2 * r - 1 - y));
				}
			}
		}
	}

	@Test
	void alphaFallsOutwardAlongTheDiagonal() {
		for (int r = 1; r <= 48; r++) {
			for (int i = r - 1; i > 0; i--) {
				assertTrue(CornerMask.alpha(r, i - 1, i - 1) <= CornerMask.alpha(r, i, i), "radius " + r + " at " + i);
			}
			// Radius 1 has one pixel per quadrant, 0.79 covered; from radius 2 on the pixel next to the centre is full.
			if (r >= 2) {
				assertEquals(255, CornerMask.alpha(r, r - 1, r - 1), "the centre is covered");
			}
		}
	}

	@Test
	void areaMatchesTheDisc() {
		for (int r = 4; r <= 96; r++) {
			long sum = 0;
			for (int y = 0; y < 2 * r; y++) {
				for (int x = 0; x < 2 * r; x++) {
					sum += CornerMask.alpha(r, x, y);
				}
			}
			double disc = Math.PI * r * r;
			assertEquals(disc, sum / 255.0, disc * 0.01, "radius " + r);
		}
	}

	@Test
	void ringIsOuterMinusInner() {
		for (int r = 1; r <= 24; r++) {
			for (int t = 1; t <= r; t++) {
				boolean[][] ring = cover(Corners.ring(2 * r, 2 * r, r, t), 2 * r, 2 * r);
				for (int y = 0; y < 2 * r; y++) {
					for (int x = 0; x < 2 * r; x++) {
						int a = CornerMask.ringAlpha(r, t, x, y);
						String at = "radius " + r + ", thickness " + t + ", pixel " + x + "," + y;
						assertTrue(a >= 0 && a <= 255, at);
						double d = Math.hypot(x + 0.5 - r, y + 0.5 - r);
						if (d <= r - t - 0.5 || d >= r + 0.5) {
							assertEquals(0, a, at + ": inside the hole or outside");
						} else if (d >= r - t + 0.5 && d <= r - 0.5) {
							assertEquals(255, a, at + ": on the band");
						}
						assertEquals(ring[y][x], a >= 128, at + ": half coverage is the fill-based outline");
					}
				}
			}
			for (int y = 0; y < 2 * r; y++) {
				for (int x = 0; x < 2 * r; x++) {
					assertEquals(CornerMask.alpha(r, x, y), CornerMask.ringAlpha(r, r, x, y), "no hole when the thickness is the radius");
				}
			}
		}
	}

	@Test
	void bakeHasOneValuePerPixel() {
		for (int r = 1; r <= 12; r++) {
			int[] fill = CornerMask.bakeFill(r);
			int[] ring = CornerMask.bakeRing(r, Math.max(1, r / 3));
			assertEquals(4 * r * r, fill.length);
			assertEquals(4 * r * r, ring.length);
			int[] expectedFill = new int[4 * r * r];
			int[] expectedRing = new int[4 * r * r];
			for (int y = 0; y < 2 * r; y++) {
				for (int x = 0; x < 2 * r; x++) {
					expectedFill[y * 2 * r + x] = CornerMask.alpha(r, x, y);
					expectedRing[y * 2 * r + x] = CornerMask.ringAlpha(r, Math.max(1, r / 3), x, y);
				}
			}
			assertArrayEquals(expectedFill, fill, "row-major, radius " + r);
			assertArrayEquals(expectedRing, ring, "row-major, radius " + r);
		}
	}

	static boolean[][] cover(List<Corners.Span> spans, int w, int h) {
		boolean[][] covered = new boolean[h][w];
		for (Corners.Span span : spans) {
			for (int y = span.y0(); y < span.y1(); y++) {
				for (int x = span.x0(); x < span.x1(); x++) {
					covered[y][x] = true;
				}
			}
		}
		return covered;
	}
}
