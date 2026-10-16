package com.k8bas.skyblockutility.ui.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * AC-UI-33 [C] (REQ-UI-32, T3.21), for gametests only: an icon of the kit in a screenshot against its baked coverage. The
 * backdrop is read at the icon box's top-left pixel, which no icon covers, and every pixel must be the icon's colour over it
 * by the icon's coverage (times the colour's alpha, for a dimmed control), so the edges are anti-aliased and the icon is
 * drawn 1:1 at its size times the GUI scale.
 */
public final class IconProbe {
	/** Blending on an 8-bit framebuffer rounds; a wrong icon is off by far more. */
	private static final int TOLERANCE = 3;

	private IconProbe() {
	}

	/** As {@link #check(BufferedImage, IconShapes.Icon, int, int, int, int, int)}, on a screenshot file. */
	public static String check(Path shot, IconShapes.Icon icon, int x, int y, int size, int scale, int colour) {
		try {
			return check(ImageIO.read(shot.toFile()), icon, x, y, size, scale, colour);
		} catch (IOException e) {
			throw new AssertionError("cannot read " + shot, e);
		}
	}

	/** "" if the icon, size GUI px at GUI (x, y), is drawn in the ARGB {@code colour} at this scale, else what differs. */
	public static String check(BufferedImage image, IconShapes.Icon icon, int x, int y, int size, int scale, int colour) {
		int px = size * scale;
		int[] alpha = IconShapes.bake(icon, px);
		if (alpha[0] != 0) {
			return icon + ": its top-left pixel is covered, so the backdrop cannot be read there";
		}
		int rgb = colour & 0xFFFFFF;
		float opacity = (colour >>> 24) / 255F;
		int backdrop = image.getRGB(x * scale, y * scale) & 0xFFFFFF;
		int soft = 0;
		int wrong = 0;
		String first = "";
		for (int ly = 0; ly < px; ly++) {
			for (int lx = 0; lx < px; lx++) {
				int a = alpha[ly * px + lx];
				int actual = image.getRGB(x * scale + lx, y * scale + ly) & 0xFFFFFF;
				int expected = blend(rgb, backdrop, a / 255F * opacity);
				if (!within(actual, expected) && wrong++ == 0) {
					first = " (first at " + lx + "," + ly + ": " + Integer.toHexString(actual) + ", expected " + Integer.toHexString(expected) + ")";
				}
				if (a > 0 && a < 255 && !within(actual, backdrop) && !within(actual, rgb)) {
					soft++;
				}
			}
		}
		if (wrong > 0) {
			return icon + " at scale " + scale + ": " + wrong + " pixels differ from its coverage over " + Integer.toHexString(backdrop) + first;
		}
		return soft > 0 ? "" : icon + " at scale " + scale + ": no anti-aliased edge pixel";
	}

	private static int blend(int top, int bottom, float alpha) {
		int out = 0;
		for (int shift = 0; shift <= 16; shift += 8) {
			out |= Math.round((top >> shift & 0xFF) * alpha + (bottom >> shift & 0xFF) * (1 - alpha)) << shift;
		}
		return out;
	}

	private static boolean within(int a, int b) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > TOLERANCE) {
				return false;
			}
		}
		return true;
	}
}
