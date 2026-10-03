package com.k8bas.skyblockutility.ui.color;

/**
 * A colour as hue, saturation and value, each 0..1 (hue wraps). The picker keeps this as its truth, so
 * moving brightness to 0 and back keeps the hue and saturation (REQ-UI-13, AC-UI-12).
 */
public record Hsv(float h, float s, float v) {
	/** 0xRRGGBB, each channel rounded, so every 8-bit colour round-trips exactly. */
	public int toRgb() {
		double hue = ((h % 1F) + 1F) % 1F * 6.0;
		double c = v * s;
		double x = c * (1 - Math.abs(hue % 2 - 1));
		double m = v - c;
		double r, g, b;
		switch ((int) hue) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return channel(r + m) << 16 | channel(g + m) << 8 | channel(b + m);
	}

	private static int channel(double value) {
		return (int) Math.max(0, Math.min(255, Math.round(value * 255)));
	}

	/**
	 * The HSV of 0xRRGGBB. What the colour cannot show is kept from {@code previous}: a grey keeps the
	 * hue, black keeps the hue and saturation.
	 */
	public static Hsv fromRgb(int rgb, Hsv previous) {
		double r = (rgb >> 16 & 0xFF) / 255.0;
		double g = (rgb >> 8 & 0xFF) / 255.0;
		double b = (rgb & 0xFF) / 255.0;
		double max = Math.max(r, Math.max(g, b));
		double min = Math.min(r, Math.min(g, b));
		double delta = max - min;
		if (max == 0) {
			return new Hsv(previous.h(), previous.s(), 0F);
		}
		if (delta == 0) {
			return new Hsv(previous.h(), 0F, (float) max);
		}
		double hue;
		if (max == r) {
			hue = ((g - b) / delta) % 6;
		} else if (max == g) {
			hue = (b - r) / delta + 2;
		} else {
			hue = (r - g) / delta + 4;
		}
		hue /= 6;
		if (hue < 0) {
			hue += 1;
		}
		return new Hsv((float) hue, (float) (delta / max), (float) max);
	}
}
