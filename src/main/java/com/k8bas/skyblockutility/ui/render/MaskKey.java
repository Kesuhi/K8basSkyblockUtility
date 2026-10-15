package com.k8bas.skyblockutility.ui.render;

/**
 * The cache key of a mask texture (REQ-UI-47, T3.18), all sizes in screen pixels: a corner mask by its radius and outline
 * thickness (0 for a fill), a disc mask by its diameter, so an odd diameter has its own disc. One int, so the cache keys
 * them without allocating on every draw.
 */
public final class MaskKey {
	/** Above every corner key: radius 256 and thickness up to 511 fit below bit 18. */
	private static final int DISC = 1 << 20;

	private MaskKey() {
	}

	public static int corner(int radius, int thickness) {
		return radius << 9 | thickness;
	}

	public static int disc(int diameter) {
		return DISC | diameter;
	}

	public static boolean isDisc(int key) {
		return (key & DISC) != 0;
	}

	public static int diameter(int key) {
		return key & (DISC - 1);
	}

	public static int radius(int key) {
		return key >> 9;
	}

	public static int thickness(int key) {
		return key & 0x1FF;
	}
}
