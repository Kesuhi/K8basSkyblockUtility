package com.k8bas.skyblockutility.ui.render;

/**
 * A soft drop shadow's alpha mask (REQ-UI-41, T3.31), in screen pixels: a square of four corner tiles, each
 * {@code radius + blur} wide, whose inner corner is the centre of the element's rounded corner. A pixel's alpha falls from
 * full at the element's edge to nothing at {@code blur} beyond it, eased out; the straight edges are the slices next to the
 * centre, stretched. Pure, so the falloff is tested without a texture.
 */
public final class ShadowMask {
	private ShadowMask() {
	}

	/**
	 * The alpha 0-255 at {@code distance} screen px outside the element's edge (negative inside) for a blur of {@code blur}: 0
	 * from the centre of the blur's outermost texel, half a pixel short of it, on.
	 */
	public static int alpha(float distance, int blur) {
		float extent = blur - 0.5F;
		if (distance <= 0F) {
			return 255;
		}
		if (distance >= extent) {
			return 0;
		}
		float rest = 1F - distance / extent;
		return Math.round(255 * rest * rest);
	}

	/** The mask, row by row: 2(radius + blur) square, for an element corner of {@code radius} and a blur of {@code blur}. */
	public static int[] bake(int radius, int blur) {
		int e = radius + blur;
		int size = 2 * e;
		int[] alpha = new int[size * size];
		for (int y = 0; y < size; y++) {
			float dy = Math.abs(y + 0.5F - e);
			for (int x = 0; x < size; x++) {
				float dx = Math.abs(x + 0.5F - e);
				alpha[y * size + x] = alpha((float) Math.sqrt(dx * dx + dy * dy) - radius, blur);
			}
		}
		return alpha;
	}
}
