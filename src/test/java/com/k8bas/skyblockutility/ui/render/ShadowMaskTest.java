package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-42 [A] (REQ-UI-41, T3.31): the shadow's 9-slice mask, baked per GUI scale. Its alpha falls monotonically from the
 * element's edge outward and reaches 0 at its blur extent, along a straight edge and around a corner, at GUI scales 1-4.
 * (Its texture setup is the masks' linear, clamp-to-edge one, {@code Shapes.maskTexture}, checked in DiscsAndPillsTest.)
 */
class ShadowMaskTest {
	/** Its cache key is its own: not a disc, nor the corner of the same radius and thickness, and it gives back what it holds. */
	@Test
	void aShadowKeyIsItsOwn() {
		int key = MaskKey.shadow(16, 12);
		assertTrue(MaskKey.isShadow(key));
		assertTrue(!MaskKey.isDisc(key));
		assertEquals(16, MaskKey.radius(key));
		assertEquals(12, MaskKey.thickness(key));
		assertTrue(key != MaskKey.corner(16, 12) && key != MaskKey.disc(16));
		assertTrue(!MaskKey.isShadow(MaskKey.corner(16, 12)) && !MaskKey.isShadow(MaskKey.disc(16)));
		assertEquals(16, MaskKey.radius(MaskKey.corner(16, 12)), "a corner key still reads its radius");
	}

	/**
	 * The 9-slice tiles the ring around the element exactly once, from the blur outside it to its corner radius inside it, with
	 * its texture coordinates inside the mask; also for a box shorter than two corners; and its bounds stay in the window.
	 */
	@Test
	void theSlicesTileTheRingAroundTheElement() {
		org.joml.Matrix3x2f identity = new org.joml.Matrix3x2f();
		for (int[] box : new int[][] {{120, 60}, {20, 10}}) {
			int w = box[0];
			int h = box[1];
			int radius = 8;
			int blur = 12;
			int e = Math.min(radius, Math.min(w, h) / 2) + blur;
			ShapePlan.Element element = ShapePlan.shadow(identity, 100, 100, w, h, e, blur, 1, new int[] {0, 0, 640, 480});
			int[] quads = element.quads();
			float[] uvs = element.uvs();
			java.util.Set<Long> covered = new java.util.HashSet<>();
			for (int q = 0; q < quads.length; q += 4) {
				for (int y = quads[q + 1]; y < quads[q + 3]; y++) {
					for (int x = quads[q]; x < quads[q + 2]; x++) {
						assertTrue(covered.add((long) x << 32 | (y & 0xFFFFFFFFL)), w + "x" + h + ": " + x + "," + y + " covered twice");
					}
				}
				for (int i = 0; i < 4; i++) {
					assertTrue(uvs[q + i] >= 0F && uvs[q + i] <= 1F, w + "x" + h + ": uv " + uvs[q + i]);
				}
			}
			for (int y = -blur; y < h + blur; y++) {
				for (int x = -blur; x < w + blur; x++) {
					boolean centre = x >= e - blur && x < w + blur - e && y >= e - blur && y < h + blur - e;
					assertEquals(!centre, covered.contains((long) x << 32 | (y & 0xFFFFFFFFL)), w + "x" + h + ": " + x + "," + y);
				}
			}
		}
		// Near the window's corner its bounds are cut to the window.
		ShapePlan.Element atCorner = ShapePlan.shadow(identity, 0, 0, 40, 20, 20, 12, 1, new int[] {0, 0, 640, 480});
		assertTrue(atCorner.left() >= 0 && atCorner.top() >= 0, "cut to the window: " + atCorner.left() + "," + atCorner.top());
	}

	@Test
	void itFallsFromTheEdgeToZeroAtTheBlurExtent() {
		for (int scale = 1; scale <= 4; scale++) {
			for (int guiRadius : new int[] {4, 6, 8}) {
				int radius = guiRadius * scale;
				int blur = Shapes.SHADOW_BLUR * scale;
				int e = radius + blur;
				int[] alpha = ShadowMask.bake(radius, blur);
				assertEquals(4 * e * e, alpha.length, "a square of four corner tiles");
				String what = "scale " + scale + ", radius " + guiRadius;
				// Along the top edge's slice (the column next to the arc's centre), from the element's edge up to the mask's top.
				int last = 256;
				for (int row = e - radius - 1; row >= 0; row--) {
					int a = alpha[row * 2 * e + e - 1];
					assertTrue(a <= last, what + ": falls outward along the edge at row " + row + ": " + a + " after " + last);
					last = a;
				}
				assertTrue(alpha[(e - radius) * 2 * e + e - 1] >= 250, what + ": full at the element's edge");
				assertEquals(0, last, what + ": nothing at the blur extent, its outermost texel");
				// Symmetric, as its right and bottom tiles are the left and top ones' mirror images.
				for (int y = 0; y < 2 * e; y++) {
					for (int x = 0; x < 2 * e; x++) {
						assertEquals(alpha[y * 2 * e + x], alpha[y * 2 * e + 2 * e - 1 - x], what + ": left-right at " + x + "," + y);
						assertEquals(alpha[y * 2 * e + x], alpha[(2 * e - 1 - y) * 2 * e + x], what + ": top-bottom at " + x + "," + y);
					}
				}
				// Around the corner: from the arc outward along the diagonal.
				last = 256;
				for (int i = e - 1; i >= 0; i--) {
					int a = alpha[i * 2 * e + i];
					assertTrue(a <= last, what + ": falls outward around the corner at " + i);
					last = a;
				}
				assertEquals(0, last, what + ": nothing at the tile's outer corner, beyond the blur");
			}
		}
	}

	@Test
	void theFalloffIsZeroFromTheBlurOnAndFullInside() {
		assertEquals(255, ShadowMask.alpha(-3F, 12));
		assertEquals(255, ShadowMask.alpha(0F, 12));
		assertEquals(0, ShadowMask.alpha(12F, 12), "at the blur extent");
		assertEquals(0, ShadowMask.alpha(11.5F, 12), "at the outermost texel's centre");
		assertEquals(0, ShadowMask.alpha(20F, 12));
		int half = ShadowMask.alpha(6F, 12);
		assertTrue(half > 0 && half < 255 / 2, "eased: below half way at half the blur: " + half);
	}
}
