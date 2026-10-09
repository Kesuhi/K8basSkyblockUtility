package com.k8bas.skyblockutility.ui.render

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.config.GeneralConfig

/**
 * The settings UI's colours (REQ-UI-17, D-8): a dark palette, an accent the player can pick (default
 * teal #29B6B2, AC-UI-16), and fixed destructive and error colours that never follow the accent.
 * Our own values, written from the spec; no AlpakaAddons code or constants (REQ-UI-24).
 *
 * @param accentRgb the accent as 0xRRGGBB
 */
@JvmRecord
data class Theme(val accentRgb: Int) {
	fun accent(): Int = ColorMath.opaque(accentRgb)

	fun accentHover(): Int = ColorMath.lighten(accentRgb, 15)

	fun accentPressed(): Int = ColorMath.scale(accentRgb, 85)

	fun accentDim(): Int = ColorMath.scale(accentRgb, 60)

	/** Text on an accent fill (a primary button): dark or light, whichever reads better on the chosen accent. */
	fun textOnAccent(): Int =
		if (ColorMath.contrast(TEXT_DARK, accentRgb) >= ColorMath.contrast(TEXT_PRIMARY, accentRgb)) TEXT_DARK else TEXT_PRIMARY

	/** A quarter-strength wash of the accent, e.g. behind a selected tab. */
	fun accentBackground(): Int = ColorMath.withAlpha(0x40, accentRgb)

	override fun toString(): String = "Theme[accentRgb=$accentRgb]"

	companion object {
		const val BACKDROP: Int = 0x99000000.toInt()
		const val PANEL: Int = 0xFF1C1E22.toInt()
		const val HEADER: Int = 0xFF212429.toInt()
		const val SIDEBAR: Int = 0xFF17191C.toInt()
		const val CARD: Int = 0xFF262A2F.toInt()
		const val CARD_HOVER: Int = 0xFF30353B.toInt()
		const val SEPARATOR: Int = 0xFF3C424A.toInt()
		const val TEXT_PRIMARY: Int = 0xFFECEFF3.toInt()
		const val TEXT_SECONDARY: Int = 0xFFA0A7B0.toInt()
		const val TEXT_DISABLED: Int = 0xFF626973.toInt()

		/** Dark text for a light accent: 7.5:1 on the teal, where white would give 2.5:1 (see [textOnAccent]). */
		const val TEXT_DARK: Int = 0xFF101214.toInt()
		const val DESTRUCTIVE: Int = 0xFFC83737.toInt()
		const val DESTRUCTIVE_HOVER: Int = 0xFFD14545.toInt()
		const val DESTRUCTIVE_PRESSED: Int = 0xFFB83232.toInt()
		const val ERROR: Int = 0xFFFF5555.toInt()

		/** A keybind conflict, in vanilla Controls' yellow (R26); the error red stays for real errors. */
		const val CONFLICT_TEXT: Int = 0xFFFFFF55.toInt()
		const val CONFLICT_BAR: Int = 0xFFFFFF00.toInt()
		const val ERROR_BACKGROUND: Int = 0xFF3A1A1C.toInt()

		@JvmStatic
		fun of(config: GeneralConfig): Theme = Theme(config.accentColor?.let { it and 0xFFFFFF } ?: GeneralConfig.DEFAULT_ACCENT)

		/** The theme of the current settings, read each frame so an accent change applies at once (REQ-UI-15). */
		@JvmStatic
		fun current(): Theme = of(ConfigManager.general())
	}
}
