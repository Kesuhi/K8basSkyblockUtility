package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.GeneralConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-17 (T2.2): theme tokens; the accent comes from the config, the destructive and error colours are fixed. */
class ThemeTest {
	/** AC-UI-16 [A]: a fresh config gives the teal accent #29B6B2. */
	@Test
	void aFreshConfigHasTheTealAccent() {
		GeneralConfig fresh = new GeneralConfig();
		assertNull(fresh.accentColor, "not written until the player picks one");
		assertEquals(0xFF29B6B2, Theme.of(fresh).accent());
	}

	@Test
	void theAccentFollowsTheConfigAndTheFixedColoursDoNot() {
		GeneralConfig config = new GeneralConfig();
		config.accentColor = 0xFF5252;
		Theme red = Theme.of(config);
		assertEquals(0xFFFF5252, red.accent());
		assertEquals(0xFFC83737, Theme.DESTRUCTIVE);
		assertEquals(0xFFFF5555, Theme.ERROR);
		config.accentColor = 0x12FF5252;
		assertEquals(0xFFFF5252, Theme.of(config).accent(), "the accent is always opaque");
	}

	@Test
	void accentVariantsAreDerived() {
		Theme teal = Theme.of(new GeneralConfig());
		assertEquals(0xFF49C1BE, teal.accentHover());
		assertEquals(0xFF239B97, teal.accentPressed());
		assertEquals(0xFF196D6B, teal.accentDim());
		assertEquals(0x4029B6B2, teal.accentBackground());
	}

	/** Text drawn with an alpha of 0 is skipped by the game, so no text colour may have one. */
	@Test
	void textColoursAreOpaque() {
		for (int color : new int[] {Theme.TEXT_PRIMARY, Theme.TEXT_SECONDARY, Theme.TEXT_DISABLED, Theme.TEXT_DARK, Theme.ERROR}) {
			assertEquals(0xFF, color >>> 24, Integer.toHexString(color));
		}
	}

	/** A label on the accent stays readable whatever accent the player picks. */
	@Test
	void textOnTheAccentFollowsItsBrightness() {
		assertEquals(Theme.TEXT_DARK, Theme.of(new GeneralConfig()).textOnAccent(), "dark on the teal");
		GeneralConfig config = new GeneralConfig();
		config.accentColor = 0x1E3A8A;
		Theme navy = Theme.of(config);
		assertEquals(Theme.TEXT_PRIMARY, navy.textOnAccent(), "light on a dark blue");
		assertTrue(ColorMath.contrast(navy.textOnAccent(), navy.accentRgb()) >= 4.5);
		config.accentColor = 0xFFFFFF;
		assertEquals(Theme.TEXT_DARK, Theme.of(config).textOnAccent());
		assertEquals(21.0, ColorMath.contrast(0xFFFFFF, 0x000000), 1e-9);
	}

	/** REQ-UI-16: a stored value is kept as it is; the read ignores alpha bits instead. */
	@Test
	void aStoredAccentIsNotRewritten() {
		GeneralConfig config = new GeneralConfig();
		config.accentColor = 0xFF29B6B2;
		assertFalse(config.normalize());
		assertEquals(0xFF29B6B2, config.accentColor);
		assertEquals(0xFF29B6B2, Theme.of(config).accent());
	}

	@Test
	void colourMaths() {
		assertEquals(0xFF808080, ColorMath.lerp(0xFF000000, 0xFFFFFFFF, 0.5F));
		assertEquals(0x80123456, ColorMath.lerp(0x80123456, 0xFFFFFFFF, 0F));
		assertEquals(0xFFFFFFFF, ColorMath.lerp(0x80123456, 0xFFFFFFFF, 1F));
		assertEquals(0xFF123456, ColorMath.opaque(0x00123456));
		assertEquals(0x40123456, ColorMath.withAlpha(0x40, 0xFF123456));
	}
}
