package com.k8bas.skyblockutility.ui.render;

/**
 * The opacity the UI kit draws at (REQ-UI-38, T3.29): 1, or less while an overlay fades in. The kit's shapes, images, icons and
 * text multiply it into their colour's alpha, so a whole overlay fades without a shader. Render thread only; nested fades
 * multiply, and {@link #leave} puts back the one before.
 */
public final class UiFade {
	private static float alpha = 1F;

	private UiFade() {
	}

	/** {@code argb} with its alpha times the fade in effect. */
	public static int apply(int argb) {
		if (alpha >= 1F) {
			return argb;
		}
		int a = Math.round((argb >>> 24) * alpha);
		return a << 24 | (argb & 0xFFFFFF);
	}

	/** Draws at {@code factor} times the fade in effect until {@link #leave}; returns the fade before. */
	public static float enter(float factor) {
		float previous = alpha;
		alpha = previous * Math.max(0F, Math.min(1F, factor));
		return previous;
	}

	public static void leave(float previous) {
		alpha = previous;
	}
}
