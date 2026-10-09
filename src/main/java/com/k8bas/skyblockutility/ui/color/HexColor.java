package com.k8bas.skyblockutility.ui.color;

import java.util.Locale;

/**
 * The picker's hex field (REQ-UI-13, R27): {@code #RRGGBB} or {@code #AARRGGBB}, with or without the
 * {@code #}, any case, spaces around ignored. A colour without alpha takes 8 digits too and drops the
 * alpha, so {@code #80FF0000} gives opaque red (EC-NPCWP-06).
 */
public final class HexColor {
	private HexColor() {
	}

	/**
	 * @param storesAlpha whether the colour keeps an alpha channel
	 * @return 0xAARRGGBB (6 digits are opaque) for a colour with alpha, 0xRRGGBB without; null if invalid
	 */
	public static Integer parse(String text, boolean storesAlpha) {
		String digits = text.strip();
		if (digits.startsWith("#")) {
			digits = digits.substring(1);
		}
		if (digits.length() != 6 && digits.length() != 8) {
			return null;
		}
		for (int i = 0; i < digits.length(); i++) {
			if (Character.digit(digits.charAt(i), 16) < 0) {
				return null;
			}
		}
		int value = (int) Long.parseLong(digits, 16);
		if (!storesAlpha) {
			return value & 0xFFFFFF;
		}
		return digits.length() == 6 ? 0xFF000000 | value : value;
	}

	public static String format(int color, boolean storesAlpha) {
		return storesAlpha ? String.format(Locale.ROOT, "#%08X", color) : String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
	}
}
