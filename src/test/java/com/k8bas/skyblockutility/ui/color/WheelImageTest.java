package com.k8bas.skyblockutility.ui.color;

import com.k8bas.skyblockutility.ui.render.CornerMask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-UI-31 and REQ-UI-13 (T3.19): the wheel is baked at its drawn size in physical pixels, its rim the disc mask's exact
 * coverage, each pixel the colour {@link WheelGeometry} picks at its centre, so the colours picked do not change.
 */
class WheelImageTest {
	/** The wheel's sizes at GUI scales 1-4 (80 GUI px), and odd ones. */
	private static final int[] SIZES = {80, 81, 160, 240, 319, 320};

	@Test
	void theRimIsTheDiscMasksCoverage() {
		for (int size : SIZES) {
			WheelImage wheel = WheelImage.of(size);
			assertEquals(size, wheel.size());
			int[] argb = wheel.argb(1F);
			assertEquals(size * size, argb.length);
			int soft = 0;
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int alpha = argb[y * size + x] >>> 24;
					assertEquals(CornerMask.discAlpha(size, x, y), alpha, "size " + size + ", pixel " + x + "," + y);
					if (alpha > 0 && alpha < 255) {
						soft++;
					}
				}
			}
			assertTrue(soft > 4 * size / 2, "size " + size + ": an anti-aliased rim, " + soft + " soft pixels");
		}
	}

	/** Each drawn pixel is the colour a pick at its centre gives, at full and at lower brightness. */
	@Test
	void eachPixelIsTheColourPickedAtItsCentre() {
		for (int size : SIZES) {
			WheelImage wheel = WheelImage.of(size);
			for (float value : new float[] {1F, 0.5F, 0.13F}) {
				int[] argb = wheel.argb(value);
				double radius = size / 2.0;
				for (int y = 0; y < size; y++) {
					for (int x = 0; x < size; x++) {
						if (argb[y * size + x] >>> 24 == 0) {
							continue;
						}
						float[] hs = WheelGeometry.hsAt(x + 0.5 - radius, y + 0.5 - radius, radius, 0F);
						assertEquals(new Hsv(hs[0], hs[1], value).toRgb(), argb[y * size + x] & 0xFFFFFF,
								"size " + size + ", value " + value + ", pixel " + x + "," + y);
					}
				}
			}
		}
	}

	/**
	 * A pixel outside the circle is transparent but keeps the rim's colour (full saturation at its angle), so linear sampling
	 * at a fractional position or scale blends the rim with its own colour, never with black.
	 */
	@Test
	void transparentPixelsKeepTheRimsColour() {
		for (int size : SIZES) {
			WheelImage wheel = WheelImage.of(size);
			int[] argb = wheel.argb(0.8F);
			double radius = size / 2.0;
			int outside = 0;
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					if (argb[y * size + x] >>> 24 != 0) {
						continue;
					}
					outside++;
					float[] hs = WheelGeometry.hsAt(x + 0.5 - radius, y + 0.5 - radius, radius, 0F);
					assertEquals(1F, hs[1], "size " + size + ": a transparent pixel lies outside the circle");
					assertEquals(new Hsv(hs[0], 1F, 0.8F).toRgb(), argb[y * size + x] & 0xFFFFFF, "size " + size + ", pixel " + x + "," + y);
				}
			}
			assertTrue(outside > 0, "size " + size + ": the corners are transparent");
		}
	}

	/** Another brightness changes only the colours: the same pixels are covered, with the same hue and saturation. */
	@Test
	void anotherBrightnessKeepsTheShape() {
		WheelImage wheel = WheelImage.of(160);
		int[] full = wheel.argb(1F);
		int[] dark = wheel.argb(0F);
		for (int i = 0; i < full.length; i++) {
			assertEquals(full[i] >>> 24, dark[i] >>> 24, "pixel " + i);
			assertEquals(0, dark[i] & 0xFFFFFF, "brightness 0 is black, pixel " + i);
		}
	}
}
