package com.k8bas.skyblockutility.ui.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * AC-UI-34 [C] (REQ-UI-33, T3.22-T3.23), for gametests only: how thick a line is in a screenshot, counted in physical px
 * along a straight edge, against the rule written out here rather than read from the code under test.
 */
public final class HairlineProbe {
	private HairlineProbe() {
	}

	/** REQ-UI-33's rule: ceil(guiScale / 2) physical px. */
	public static int expected(int scale) {
		return (int) Math.ceil(scale / 2.0);
	}

	public static BufferedImage read(Path shot) {
		try {
			return ImageIO.read(shot.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read " + shot, e);
		}
	}

	/** How many pixels from physical (x, y), stepping by (dx, dy), have the colour {@code argb}, within 8 per channel. */
	public static int run(BufferedImage image, int x, int y, int dx, int dy, int argb) {
		int n = 0;
		while (x + n * dx >= 0 && y + n * dy >= 0 && x + n * dx < image.getWidth() && y + n * dy < image.getHeight()
				&& near(image.getRGB(x + n * dx, y + n * dy), argb)) {
			n++;
		}
		return n;
	}

	/** Whether the line of {@code argb} starting at (x, y) along (dx, dy) is the hairline thickness at this scale. */
	public static String check(BufferedImage image, String what, int scale, int x, int y, int dx, int dy, int argb) {
		int thick = run(image, x, y, dx, dy, argb);
		return thick == expected(scale) ? "" : what + " at scale " + scale + " is " + thick + " px, the hairline " + expected(scale) + "; ";
	}

	private static boolean near(int a, int b) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > 8) {
				return false;
			}
		}
		return true;
	}
}
