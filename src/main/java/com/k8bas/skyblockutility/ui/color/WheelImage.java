package com.k8bas.skyblockutility.ui.color;

import com.k8bas.skyblockutility.ui.render.CornerMask;

/**
 * The colour wheel's pixels (REQ-UI-13, REQ-UI-31, T3.19), baked at the size the wheel is drawn at in screen pixels, so it is
 * drawn 1:1 instead of a fixed texture stretched by a non-integer factor. Each pixel is the colour {@link WheelGeometry}
 * picks at its centre, so the colours drawn are the colours picked, and its alpha is the disc mask's exact coverage
 * ({@link CornerMask#discAlpha}), so the rim is anti-aliased like every other disc. A pixel outside the circle is transparent
 * but keeps the rim's colour, so linear sampling at a fractional position or scale never blends in black. The texture is
 * baked at full brightness and drawn with {@link #tint} for any other (REQ-UI-48); {@link #argb} at other brightnesses is
 * what that tint gives, for the tests. Pure, so it is tested without a game.
 */
public final class WheelImage {
	private final int size;
	private final float[] hue;
	private final float[] saturation;
	private final int[] alpha;

	private WheelImage(int size) {
		this.size = size;
		hue = new float[size * size];
		saturation = new float[size * size];
		alpha = new int[size * size];
		double radius = size / 2.0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int i = y * size + x;
				float[] hs = WheelGeometry.hsAt(x + 0.5 - radius, y + 0.5 - radius, radius, 0F);
				hue[i] = hs[0];
				saturation[i] = hs[1];
				alpha[i] = CornerMask.discAlpha(size, x, y);
			}
		}
	}

	/** A wheel of size × size screen pixels. */
	public static WheelImage of(int size) {
		return new WheelImage(size);
	}

	public int size() {
		return size;
	}

	/**
	 * The vertex colour that draws the full-brightness wheel at brightness {@code value} (REQ-UI-48): the opaque grey
	 * (v, v, v). An HSV brightness scales red, green and blue alike, so the tinted pixels are the pixels at that brightness
	 * (within the 8-bit rounding), and the alpha, so the rim, stays.
	 */
	public static int tint(float value) {
		int grey = Math.round(Math.clamp(value, 0F, 1F) * 255F);
		return 0xFF000000 | grey << 16 | grey << 8 | grey;
	}

	/** The pixels at brightness {@code value}, as ARGB, row-major. */
	public int[] argb(float value) {
		int[] argb = new int[size * size];
		for (int i = 0; i < argb.length; i++) {
			argb[i] = alpha[i] << 24 | new Hsv(hue[i], saturation[i], value).toRgb();
		}
		return argb;
	}
}
