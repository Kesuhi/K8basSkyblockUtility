package com.k8bas.skyblockutility.ui.render;

/** ARGB colour helpers in exact integer maths (T2.2). Written from SPEC REQ-UI-17; no AlpakaAddons code (REQ-UI-24). */
public final class ColorMath {
	private ColorMath() {
	}

	public static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	public static int withAlpha(int alpha, int rgb) {
		return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
	}

	/** Each channel moves {@code percent} % of the way to white; the result is opaque. */
	public static int lighten(int rgb, int percent) {
		return opaque(perChannel(rgb, channel -> channel + ((255 - channel) * percent + 50) / 100));
	}

	/** Each channel is scaled to {@code percent} %; the result is opaque. */
	public static int scale(int rgb, int percent) {
		return opaque(perChannel(rgb, channel -> (channel * percent + 50) / 100));
	}

	/** Linear blend of all four channels; t is clamped to 0..1. */
	public static int lerp(int from, int to, float t) {
		float clamped = Math.max(0F, Math.min(1F, t));
		int result = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int a = from >>> shift & 0xFF;
			int b = to >>> shift & 0xFF;
			result |= Math.round(a + (b - a) * clamped) << shift;
		}
		return result;
	}

	/** The WCAG contrast ratio of two colours (alpha ignored), from 1 to 21. */
	public static double contrast(int a, int b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	/** WCAG relative luminance of an sRGB colour. */
	public static double luminance(int rgb) {
		return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
	}

	private static double linear(int channel) {
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private interface Channel {
		int apply(int channel);
	}

	private static int perChannel(int rgb, Channel channel) {
		int r = channel.apply(rgb >> 16 & 0xFF);
		int g = channel.apply(rgb >> 8 & 0xFF);
		int b = channel.apply(rgb & 0xFF);
		return r << 16 | g << 8 | b;
	}
}
