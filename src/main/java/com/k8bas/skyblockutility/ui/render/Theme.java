package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.config.GeneralConfig;

/**
 * The settings UI's colours (REQ-UI-17, D-8): a dark palette, an accent the player can pick (default
 * teal #29B6B2, AC-UI-16), and fixed destructive and error colours that never follow the accent.
 * Our own values, written from the spec; no AlpakaAddons code or constants (REQ-UI-24).
 *
 * @param accentRgb the accent as 0xRRGGBB
 */
public record Theme(int accentRgb) {
	public static final int BACKDROP = 0x99000000;
	public static final int PANEL = 0xFF1C1E22;
	public static final int HEADER = 0xFF212429;
	public static final int SIDEBAR = 0xFF17191C;
	public static final int CARD = 0xFF262A2F;
	public static final int CARD_HOVER = 0xFF30353B;
	public static final int SEPARATOR = 0xFF3C424A;
	public static final int TEXT_PRIMARY = 0xFFECEFF3;
	public static final int TEXT_SECONDARY = 0xFFA0A7B0;
	public static final int TEXT_DISABLED = 0xFF626973;
	/** Dark text for a light accent: 7.5:1 on the teal, where white would give 2.5:1 (see {@link #textOnAccent()}). */
	public static final int TEXT_DARK = 0xFF101214;
	public static final int DESTRUCTIVE = 0xFFC83737;
	public static final int DESTRUCTIVE_HOVER = 0xFFD14545;
	public static final int DESTRUCTIVE_PRESSED = 0xFFB83232;
	public static final int ERROR = 0xFFFF5555;
	/** A keybind conflict, in vanilla Controls' yellow (R26); the error red stays for real errors. */
	public static final int CONFLICT_TEXT = 0xFFFFFF55;
	public static final int CONFLICT_BAR = 0xFFFFFF00;
	public static final int ERROR_BACKGROUND = 0xFF3A1A1C;

	public static Theme of(GeneralConfig config) {
		return new Theme(config.accentColor == null ? GeneralConfig.DEFAULT_ACCENT : config.accentColor & 0xFFFFFF);
	}

	/** The theme of the current settings, read each frame so an accent change applies at once (REQ-UI-15). */
	public static Theme current() {
		return of(ConfigManager.general());
	}

	public int accent() {
		return ColorMath.opaque(accentRgb);
	}

	public int accentHover() {
		return ColorMath.lighten(accentRgb, 15);
	}

	public int accentPressed() {
		return ColorMath.scale(accentRgb, 85);
	}

	public int accentDim() {
		return ColorMath.scale(accentRgb, 60);
	}

	/** Text on an accent fill (a primary button): dark or light, whichever reads better on the chosen accent. */
	public int textOnAccent() {
		return ColorMath.contrast(TEXT_DARK, accentRgb) >= ColorMath.contrast(TEXT_PRIMARY, accentRgb) ? TEXT_DARK : TEXT_PRIMARY;
	}

	/** A quarter-strength wash of the accent, e.g. behind a selected tab. */
	public int accentBackground() {
		return ColorMath.withAlpha(0x40, accentRgb);
	}
}
