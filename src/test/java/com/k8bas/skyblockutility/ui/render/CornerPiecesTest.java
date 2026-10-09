package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-18 (T2.9d, R32): a smooth rounded shape as four mask quads plus plain fills that never overlap. */
class CornerPiecesTest {
	private final int[] pieces = new int[CornerPieces.MAX_PIECES * CornerPieces.STRIDE];

	/** Every pixel of the box is drawn exactly once: no gap, and no overlap that would blend a translucent colour twice. */
	@Test
	void fillPiecesTileTheBoxExactlyOnce() {
		for (int r = 1; r <= 12; r++) {
			for (int w = 2 * r; w <= 40; w++) {
				for (int h = 2 * r; h <= 40; h++) {
					int n = CornerPieces.fill(w, h, r, pieces);
					int[][] count = count(n, w, h);
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							assertEquals(1, count[y][x], w + "x" + h + " radius " + r + ", pixel " + x + "," + y);
						}
					}
				}
			}
		}
	}

	@Test
	void ringPiecesCoverOnlyTheBand() {
		for (int r = 1; r <= 10; r++) {
			for (int t = 1; t <= r; t++) {
				for (int w = Math.max(2 * r, 2 * t + 1); w <= 32; w++) {
					for (int h = Math.max(2 * r, 2 * t + 1); h <= 32; h++) {
						int n = CornerPieces.ring(w, h, r, t, pieces);
						int[][] count = count(n, w, h);
						for (int y = 0; y < h; y++) {
							for (int x = 0; x < w; x++) {
								boolean corner = (x < r || x >= w - r) && (y < r || y >= h - r);
								boolean band = x < t || y < t || x >= w - t || y >= h - t;
								assertEquals(corner || band ? 1 : 0, count[y][x], w + "x" + h + " radius " + r + " thickness " + t
										+ ", pixel " + x + "," + y);
							}
						}
					}
				}
			}
		}
	}

	/** Drawn with its mask, the smooth shape is at least half covered exactly where the fill-based shape draws. */
	@Test
	void drawnCoverageMatchesTheFillShape() {
		for (int r = 1; r <= 8; r++) {
			int[] fillMask = CornerMask.bakeFill(r);
			for (int w = 2 * r; w <= 24; w++) {
				for (int h = 2 * r; h <= 24; h++) {
					assertSameShape(draw(CornerPieces.fill(w, h, r, pieces), fillMask, r, w, h),
							CornerMaskTest.cover(Corners.fill(w, h, r), w, h), w + "x" + h + " radius " + r);
					for (int t = 1; t <= r && 2 * t < Math.min(w, h); t++) {
						assertSameShape(draw(CornerPieces.ring(w, h, r, t, pieces), CornerMask.bakeRing(r, t), r, w, h),
								CornerMaskTest.cover(Corners.ring(w, h, r, t), w, h), w + "x" + h + " radius " + r + " thickness " + t);
					}
				}
			}
		}
	}

	@Test
	void maskQuadsUseTheirOwnQuadrant() {
		int n = CornerPieces.fill(30, 20, 6, pieces);
		assertEquals(7, n);
		assertPiece(0, CornerPieces.KIND_MASK, 0, 0, 6, 6, 0, 0);
		assertPiece(1, CornerPieces.KIND_MASK, 24, 0, 30, 6, 6, 0);
		assertPiece(2, CornerPieces.KIND_MASK, 0, 14, 6, 20, 0, 6);
		assertPiece(3, CornerPieces.KIND_MASK, 24, 14, 30, 20, 6, 6);
		for (int i = 4; i < n; i++) {
			assertEquals(CornerPieces.KIND_FILL, pieces[i * CornerPieces.STRIDE], "the straight parts are fills");
		}
		assertEquals(8, CornerPieces.ring(30, 20, 6, 2, pieces));
		assertPiece(3, CornerPieces.KIND_MASK, 24, 14, 30, 20, 6, 6);
	}

	/** With whole GUI pixels (R = r·s, W = w·s), every piece starts and ends on a GUI pixel. */
	@Test
	void wholeGuiRadiusKeepsPiecesGuiAligned() {
		for (int s = 1; s <= 4; s++) {
			for (int r : new int[] {1, 2, 4, 5, 6}) {
				for (int w = 2 * r; w <= 20; w++) {
					for (int h = 2 * r; h <= 20; h++) {
						assertAligned(CornerPieces.fill(w * s, h * s, r * s, pieces), s);
						if (2 < Math.min(w, h)) {
							assertAligned(CornerPieces.ring(w * s, h * s, r * s, s, pieces), s);
						}
					}
				}
			}
		}
	}

	@Test
	void zeroAreaPiecesAreSkipped() {
		assertEquals(4, CornerPieces.fill(12, 12, 6, pieces), "a circle: corners only");
		assertEquals(6, CornerPieces.fill(20, 12, 6, pieces), "a pill: corners and the top and bottom strips");
		assertEquals(5, CornerPieces.fill(12, 20, 6, pieces), "a standing pill: corners and the middle");
		assertEquals(7, CornerPieces.fill(20, 20, 6, pieces));
		assertEquals(4, CornerPieces.ring(12, 12, 6, 1, pieces));
		assertEquals(6, CornerPieces.ring(20, 12, 6, 1, pieces));
		assertEquals(8, CornerPieces.ring(20, 20, 6, 1, pieces));
	}

	@Test
	void unsupportedShapesReturnMinusOne() {
		assertEquals(-1, CornerPieces.fill(10, 10, 0, pieces), "no radius: the plain fills draw it");
		assertEquals(-1, CornerPieces.fill(600, 600, CornerMask.MAX_RADIUS + 1, pieces), "too large for a mask");
		assertEquals(-1, CornerPieces.fill(0, 10, 2, pieces));
		assertEquals(-1, CornerPieces.fill(10, -1, 2, pieces));
		assertEquals(-1, CornerPieces.fill(10, 10, 6, pieces), "not clamped");
		assertEquals(-1, CornerPieces.ring(20, 20, 2, 3, pieces), "thicker than the radius");
		assertEquals(-1, CornerPieces.ring(20, 20, 4, 0, pieces), "no thickness");
		assertEquals(-1, CornerPieces.ring(4, 20, 2, 2, pieces), "no hole left");
		assertEquals(-1, CornerPieces.ring(20, 4, 2, 2, pieces), "no hole left");
		assertEquals(CornerMask.MAX_RADIUS, CornerPieces.clampRadius(1000, 1000, CornerMask.MAX_RADIUS));
		assertEquals(4, CornerPieces.clampRadius(9, 9, 6), "half the shorter side");
		assertEquals(0, CornerPieces.clampRadius(9, 9, -2));
	}

	@Test
	void keysAreDistinct() {
		Set<Integer> keys = new HashSet<>();
		for (int r = 1; r <= CornerMask.MAX_RADIUS; r++) {
			for (int t = 0; t <= r; t++) {
				assertTrue(keys.add(CornerPieces.key(r, t)), r + "/" + t);
			}
		}
	}

	private int[][] count(int n, int w, int h) {
		assertTrue(n > 0, "drawable");
		int[][] count = new int[h][w];
		for (int i = 0; i < n; i++) {
			int o = i * CornerPieces.STRIDE;
			for (int y = pieces[o + 2]; y < pieces[o + 4]; y++) {
				for (int x = pieces[o + 1]; x < pieces[o + 3]; x++) {
					count[y][x]++;
				}
			}
		}
		return count;
	}

	/** The alpha of each pixel as the GPU draws it: 255 under a fill, the mask's texel under a mask quad. */
	private int[][] draw(int n, int[] mask, int r, int w, int h) {
		assertTrue(n > 0, "drawable");
		int[][] alpha = new int[h][w];
		for (int i = 0; i < n; i++) {
			int o = i * CornerPieces.STRIDE;
			for (int y = pieces[o + 2]; y < pieces[o + 4]; y++) {
				for (int x = pieces[o + 1]; x < pieces[o + 3]; x++) {
					alpha[y][x] = pieces[o] == CornerPieces.KIND_FILL ? 255
							: mask[(pieces[o + 6] + y - pieces[o + 2]) * 2 * r + pieces[o + 5] + x - pieces[o + 1]];
				}
			}
		}
		return alpha;
	}

	private static void assertSameShape(int[][] alpha, boolean[][] fill, String what) {
		for (int y = 0; y < fill.length; y++) {
			for (int x = 0; x < fill[y].length; x++) {
				assertEquals(fill[y][x], alpha[y][x] >= 128, what + ", pixel " + x + "," + y);
			}
		}
	}

	private void assertPiece(int i, int kind, int x0, int y0, int x1, int y1, int u, int v) {
		int o = i * CornerPieces.STRIDE;
		int[] piece = new int[CornerPieces.STRIDE];
		System.arraycopy(pieces, o, piece, 0, CornerPieces.STRIDE);
		assertArrayEquals(new int[] {kind, x0, y0, x1, y1, u, v}, piece, "piece " + i);
	}

	private void assertAligned(int n, int s) {
		assertTrue(n > 0, "drawable");
		for (int i = 0; i < n; i++) {
			for (int k = 1; k <= 4; k++) {
				assertEquals(0, pieces[i * CornerPieces.STRIDE + k] % s, "piece " + i + " at scale " + s);
			}
		}
	}
}
