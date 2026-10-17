package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-33 [A] (REQ-UI-32, T3.20): each icon of the kit rasterises to icon size × GUI scale at GUI scales 1-6 with 4×4
 * supersampling; pixels on its stroke's centre line are at least 0.9 covered and pixels more than one physical px from the
 * stroke at most 0.05; the chevrons and the × are mirror-symmetric. Without a texture the icon is fill spans of its
 * half-covered pixels (REQ-UI-47).
 */
class IconShapesTest {
	/** The sizes the UI draws icons at, in GUI px. */
	private static final int[] SIZES = {7, 8, 9, 10};

	@Test
	void eachIconRasterisesToItsSizeTimesTheScale() {
		for (IconShapes.Icon icon : IconShapes.Icon.values()) {
			for (int size : SIZES) {
				for (int scale = 1; scale <= 6; scale++) {
					int px = size * scale;
					assertEquals(px * px, IconShapes.bake(icon, px).length, icon + " at " + size + " GUI px, scale " + scale);
				}
			}
		}
	}

	/** A pixel is on the centre line when the line passes within half a pixel of its centre, inside the pixel. */
	@Test
	void theCentreLineIsCoveredAndFarPixelsAreClear() {
		for (IconShapes.Icon icon : IconShapes.Icon.values()) {
			int onLine = 0;
			int far = 0;
			for (int size : SIZES) {
				for (int scale = 1; scale <= 6; scale++) {
					int px = size * scale;
					int[] alpha = IconShapes.bake(icon, px);
					double half = IconShapes.halfStroke(px);
					for (int y = 0; y < px; y++) {
						for (int x = 0; x < px; x++) {
							double distance = IconShapes.distance(icon, px, x + 0.5, y + 0.5);
							String what = icon + " at " + px + " px, pixel " + x + "," + y;
							if (distance <= 0.5) {
								onLine++;
								assertTrue(alpha[y * px + x] >= 0.9 * 255, what + ": on the centre line, coverage " + alpha[y * px + x] / 255.0);
							}
							if (distance > half + 1) {
								far++;
								assertTrue(alpha[y * px + x] <= 0.05 * 255, what + ": more than 1 px from the stroke, coverage " + alpha[y * px + x] / 255.0);
							}
						}
					}
				}
			}
			assertTrue(onLine > 100 && far > 100, icon + ": the sweep sees the line and the space around it: " + onLine + ", " + far);
		}
	}

	@Test
	void theChevronsAndTheCrossAreMirrorSymmetric() {
		for (int size : SIZES) {
			for (int scale = 1; scale <= 6; scale++) {
				int px = size * scale;
				for (IconShapes.Icon icon : new IconShapes.Icon[] {IconShapes.Icon.CHEVRON_UP, IconShapes.Icon.CHEVRON_DOWN, IconShapes.Icon.CROSS}) {
					int[] alpha = IconShapes.bake(icon, px);
					for (int y = 0; y < px; y++) {
						for (int x = 0; x < px; x++) {
							assertEquals(alpha[y * px + x], alpha[y * px + px - 1 - x], icon + " at " + px + " px: left and right, pixel " + x + "," + y);
						}
					}
				}
				for (IconShapes.Icon icon : new IconShapes.Icon[] {IconShapes.Icon.CHEVRON_RIGHT, IconShapes.Icon.CROSS}) {
					int[] alpha = IconShapes.bake(icon, px);
					for (int y = 0; y < px; y++) {
						for (int x = 0; x < px; x++) {
							assertEquals(alpha[y * px + x], alpha[(px - 1 - y) * px + x], icon + " at " + px + " px: top and bottom, pixel " + x + "," + y);
						}
					}
				}
			}
		}
	}

	/** The up chevron is the down chevron upside down, and the right one the down one mirrored on the diagonal: one shape. */
	@Test
	void theChevronsAreOneShapeTurned() {
		for (int px : new int[] {8, 16, 24, 32}) {
			int[] down = IconShapes.bake(IconShapes.Icon.CHEVRON_DOWN, px);
			int[] up = IconShapes.bake(IconShapes.Icon.CHEVRON_UP, px);
			int[] right = IconShapes.bake(IconShapes.Icon.CHEVRON_RIGHT, px);
			for (int y = 0; y < px; y++) {
				for (int x = 0; x < px; x++) {
					assertEquals(down[y * px + x], up[(px - 1 - y) * px + x], "up at " + px + " px, pixel " + x + "," + y);
					assertEquals(down[y * px + x], right[x * px + y], "right at " + px + " px, pixel " + x + "," + y);
				}
			}
		}
	}

	/**
	 * Each icon looks like what it stands for, checked at 40 px on pixels picked by hand, not through the code's own distance:
	 * the stroke where the drawing has one, nothing where it has none (the glass's hole, the reset arrow's gap at the top).
	 */
	@Test
	void eachIconHasItsStrokesWhereItsDrawingHasThem() {
		int[][] covered = {
				{20, 27, 13, 21}, // the down chevron's apex and its left arm
				{20, 12, 13, 18}, // the up chevron's apex and its left arm
				{27, 20, 21, 13}, // the right chevron's apex and its upper arm
				{19, 19, 13, 13}, // the ×'s centre and a ray
				{16, 29, 26, 20}, // the check mark's corner and its long stroke
				{28, 17, 31, 31}, // the glass's ring and its handle
				{20, 33, 7, 21}, // the reset arrow's bottom and its left side
		};
		int[][] clear = {
				{20, 15}, // inside the down chevron, above its apex
				{20, 24}, // inside the up chevron, below its apex
				{15, 20}, // inside the right chevron
				{20, 10}, // between the ×'s upper rays
				{16, 20}, // above the check mark's corner
				{17, 17}, // the glass's hole
				{20, 8}, // the reset arrow's gap at the top
		};
		IconShapes.Icon[] icons = IconShapes.Icon.values();
		for (int i = 0; i < icons.length; i++) {
			int[] alpha = IconShapes.bake(icons[i], 40);
			for (int k = 0; k < covered[i].length; k += 2) {
				int a = alpha[covered[i][k + 1] * 40 + covered[i][k]];
				assertTrue(a >= 0.9 * 255, icons[i] + ": stroke at " + covered[i][k] + "," + covered[i][k + 1] + ", coverage " + a);
			}
			assertEquals(0, alpha[clear[i][1] * 40 + clear[i][0]], icons[i] + ": nothing at " + clear[i][0] + "," + clear[i][1]);
		}
	}

	/** REQ-UI-47: without a texture, an icon is the fill spans of its half-covered pixels, each drawn once. */
	@Test
	void withoutATextureAnIconIsFillSpans() {
		for (IconShapes.Icon icon : IconShapes.Icon.values()) {
			for (int px : new int[] {8, 16, 27, 48}) {
				int[] alpha = IconShapes.bake(icon, px);
				int[] spans = IconShapes.spans(icon, px);
				int[][] drawn = new int[px][px];
				for (int i = 0; i < spans.length; i += 4) {
					for (int y = spans[i + 1]; y < spans[i + 3]; y++) {
						for (int x = spans[i]; x < spans[i + 2]; x++) {
							drawn[y][x]++;
						}
					}
				}
				for (int y = 0; y < px; y++) {
					for (int x = 0; x < px; x++) {
						assertEquals(alpha[y * px + x] >= 128 ? 1 : 0, drawn[y][x], icon + " at " + px + " px, pixel " + x + "," + y);
					}
				}
			}
		}
	}
}
