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

	/**
	 * AC-UI-31 (REQ-UI-30): each pixel of a disc mask is within 0.05 of the circle's exact area in it at diameters 4-40; in
	 * fact within 0.003, half an alpha step and the strip integral's error, at every diameter from 1.
	 */
	@Test
	void discCoverageIsTheExactArea() {
		for (int d = 1; d <= 40; d++) {
			for (int y = 0; y < d; y++) {
				for (int x = 0; x < d; x++) {
					double exact = discArea(d / 2.0, d / 2.0, d / 2.0, x, y);
					assertEquals(exact, CornerMask.discAlpha(d, x, y) / 255.0, 0.003, "diameter " + d + ", pixel " + x + "," + y);
				}
			}
		}
	}

	/** Odd diameters too (REQ-UI-30: baked per diameter): the disc is centred on the mask, full inside and clear outside. */
	@Test
	void discMaskIsCentredAndMirrorSymmetric() {
		for (int d = 1; d <= 41; d++) {
			long sum = 0;
			for (int y = 0; y < d; y++) {
				for (int x = 0; x < d; x++) {
					int a = CornerMask.discAlpha(d, x, y);
					assertEquals(a, CornerMask.discAlpha(d, y, x), "diameter " + d);
					assertEquals(a, CornerMask.discAlpha(d, d - 1 - x, y), "diameter " + d);
					assertEquals(a, CornerMask.discAlpha(d, x, d - 1 - y), "diameter " + d);
					sum += a;
				}
			}
			double disc = Math.PI * d * d / 4;
			assertEquals(disc, sum / 255.0, d * d * 0.5 / 255 + 1e-9, "diameter " + d + ": the area of the disc, up to rounding");
			if (d >= 3) {
				assertEquals(255, CornerMask.discAlpha(d, d / 2, d / 2), "diameter " + d + ": the centre is covered");
				assertTrue(CornerMask.discAlpha(d, (d - 1) / 2, 0) >= 128, "diameter " + d + ": the top edge's middle is drawn");
			}
			// Up to diameter 3 the circle covers most of the corner pixel (pi/4 of it at 1 and 2, about 0.55 at 3).
			if (d >= 4) {
				assertTrue(CornerMask.discAlpha(d, 0, 0) < 128, "diameter " + d + ": the corner is mostly clear");
			}
		}
	}

	@Test
	void bakeDiscHasOneValuePerPixel() {
		for (int d = 1; d <= 25; d++) {
			int[] disc = CornerMask.bakeDisc(d);
			int[] expected = new int[d * d];
			for (int y = 0; y < d; y++) {
				for (int x = 0; x < d; x++) {
					expected[y * d + x] = CornerMask.discAlpha(d, x, y);
				}
			}
			assertArrayEquals(expected, disc, "row-major, diameter " + d);
		}
	}

	/**
	 * The area of the circle of radius r centred at (cx, cy) inside pixel (x, y), integrated over thin vertical strips: an
	 * independent check of the mask, not its formula.
	 */
	static double discArea(double cx, double cy, double r, int x, int y) {
		int strips = 2000;
		double sum = 0;
		for (int i = 0; i < strips; i++) {
			double dx = x + (i + 0.5) / strips - cx;
			if (Math.abs(dx) < r) {
				double half = Math.sqrt(r * r - dx * dx);
				sum += Math.max(0, Math.min(y + 1, cy + half) - Math.max(y, cy - half));
			}
		}
		return sum / strips;
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
