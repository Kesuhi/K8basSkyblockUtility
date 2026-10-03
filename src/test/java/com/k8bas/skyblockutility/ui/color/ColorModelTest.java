package com.k8bas.skyblockutility.ui.color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-13, AC-UI-12, R27 (T2.3b part B): the colour picker's models. */
class ColorModelTest {
	@Test
	void hexParsesSixOrEightDigits() {
		assertEquals(0x1A2B3C, HexColor.parse("#1A2B3C", false));
		assertEquals(0x29B6B2, HexColor.parse("29b6b2", false), "no # and lower case");
		assertEquals(0x29B6B2, HexColor.parse(" #29B6B2 ", false), "spaces around");
		for (String invalid : new String[] {"#GG1234", "#12345", "#1234567", "", "##123456", "#FFF", "#123456789"}) {
			assertNull(HexColor.parse(invalid, false), invalid);
			assertNull(HexColor.parse(invalid, true), invalid);
		}
		assertEquals(0x8029B6B2, HexColor.parse("#8029B6B2", true));
		assertEquals(0xFF29B6B2, HexColor.parse("#29B6B2", true), "six digits are opaque");
		assertEquals(0xFF0000, HexColor.parse("#80FF0000", false), "R27: a colour without alpha drops it (EC-NPCWP-06)");
	}

	@Test
	void hexFormatsWithOrWithoutAlpha() {
		assertEquals("#1A2B3C", HexColor.format(0x1A2B3C, false));
		assertEquals("#1A2B3C", HexColor.format(0xFF1A2B3C, false));
		assertEquals("#80FF0000", HexColor.format(0x80FF0000, true));
	}

	@Test
	void hsvRoundTripsEveryColourExactly() {
		Hsv start = new Hsv(0F, 0F, 1F);
		for (int r = 0; r <= 255; r += 17) {
			for (int g = 0; g <= 255; g += 17) {
				for (int b = 0; b <= 255; b += 17) {
					int rgb = r << 16 | g << 8 | b;
					assertEquals(rgb, Hsv.fromRgb(rgb, start).toRgb(), Integer.toHexString(rgb));
				}
			}
		}
		assertEquals(0xFF0000, new Hsv(0F, 1F, 1F).toRgb());
		assertEquals(0x00FF00, new Hsv(1F / 3, 1F, 1F).toRgb());
		assertEquals(0x0000FF, new Hsv(2F / 3, 1F, 1F).toRgb());
	}

	@Test
	void greyAndBlackKeepWhatTheyCannotShow() {
		Hsv picked = new Hsv(0.55F, 0.8F, 1F);
		Hsv black = Hsv.fromRgb(0x000000, picked);
		assertEquals(0.55F, black.h(), 1e-6);
		assertEquals(0.8F, black.s(), 1e-6, "black keeps the hue and saturation");
		Hsv grey = Hsv.fromRgb(0x808080, picked);
		assertEquals(0.55F, grey.h(), 1e-6, "a grey keeps the hue");
		assertEquals(0F, grey.s(), 1e-6);
	}

	/** AC-UI-12: brightness to 0 and back keeps the hue and saturation picked in the dialog. */
	@Test
	void brightnessToZeroAndBackKeepsHueAndSaturation() {
		ColorPickerState state = new ColorPickerState(0x0AA351, false);
		state.setWheel(200F / 360F, 0.8F);
		state.setBrightness(0F);
		assertEquals(0x000000, state.color());
		state.setBrightness(1F);
		assertEquals(200F / 360F, state.hsv().h(), 1F / 360F);
		assertEquals(0.8F, state.hsv().s(), 0.01F);
	}

	@Test
	void anInvalidHexIsMarkedAndNotApplied() {
		ColorPickerState state = new ColorPickerState(0x123456, false);
		assertFalse(state.changed(), "nothing changed yet");
		assertEquals("#123456", state.hexText());
		state.typeHex("#GG1234");
		assertFalse(state.hexValid());
		assertEquals(0x123456, state.color(), "the colour stays");
		state.typeHex("#1A2B3C");
		assertTrue(state.hexValid());
		assertEquals(0x1A2B3C, state.color());
		assertTrue(state.changed());
		state.typeHex("29b6b2");
		assertEquals("29b6b2", state.hexText(), "kept as typed while the field has the focus");
		assertEquals("#29B6B2", state.normalizedHex(), "AC-UI-12: pasting 29b6b2 gives #29B6B2 (shown once the field is left)");
	}

	/** R27, EC-NPCWP-06: 8 digits on a colour without alpha drop the alpha. */
	@Test
	void eightDigitsOnARuleColourGiveOpaqueRed() {
		ColorPickerState state = new ColorPickerState(0x123456, false);
		state.typeHex("#80FF0000");
		assertTrue(state.hexValid());
		assertEquals(0xFF0000, state.color());
		assertEquals("#FF0000", state.normalizedHex());
	}

	/** Typed one key at a time, #AARRGGBB must arrive whole: "#8029B6" is only its start. */
	@Test
	void anAlphaColourTypedKeyByKeyKeepsItsAlpha() {
		ColorPickerState state = new ColorPickerState(0xFF000000, true);
		state.takeExternalChange();
		String typed = "#8029B6B2";
		for (int i = 1; i <= typed.length(); i++) {
			state.typeHex(typed.substring(0, i));
			assertEquals(typed.substring(0, i), state.hexText(), "the text stays as typed");
			assertFalse(state.takeExternalChange(), "typing is no outside change, so the field is not rewritten");
		}
		assertEquals(0x8029B6B2, state.color());
		state.setBrightness(0.5F);
		assertTrue(state.takeExternalChange(), "a slider is");
	}

	@Test
	void presetsAndTheWheelUpdateTheHexText() {
		ColorPickerState state = new ColorPickerState(0x123456, false);
		state.pickPreset(0xFF5555);
		assertEquals(0xFF5555, state.color());
		assertEquals("#FF5555", state.hexText());
		state.setWheel(0F, 1F);
		state.setBrightness(1F);
		assertEquals("#FF0000", state.hexText());
	}

	@Test
	void alphaOnlyForColoursThatStoreIt() {
		ColorPickerState rgb = new ColorPickerState(0x123456, false);
		rgb.setAlpha(0.5F);
		assertEquals(0x123456, rgb.color(), "no alpha bits for an RGB colour");
		ColorPickerState argb = new ColorPickerState(0x80123456, true);
		assertEquals(0x80, argb.color() >>> 24);
		argb.setAlpha(1F);
		assertEquals(0xFF123456, argb.color());
		assertEquals("#FF123456", argb.hexText());
	}

	@Test
	void theWheelMapsAngleToHueAndDistanceToSaturation() {
		float[] left = WheelGeometry.hsAt(-40, 0, 40, 0.3F);
		assertEquals(0F, left[0] % 1F, 1e-4, "red sits at the left edge");
		assertEquals(1F, left[1], 1e-6);
		float[] outside = WheelGeometry.hsAt(0, 90, 40, 0.3F);
		assertEquals(1F, outside[1], 1e-6, "saturation is held at 1 outside the circle");
		float[] centre = WheelGeometry.hsAt(0, 0, 40, 0.3F);
		assertEquals(0.3F, centre[0], 1e-6, "the centre keeps the previous hue");
		assertEquals(0F, centre[1], 1e-6);
		double[] puck = WheelGeometry.puck(0.25F, 0.5F, 40);
		float[] back = WheelGeometry.hsAt(puck[0], puck[1], 40, 0F);
		assertEquals(0.25F, back[0], 1e-4);
		assertEquals(0.5F, back[1], 1e-4);
	}

	@Test
	void presetsAreTheSixteenChatColours() {
		assertEquals(16, ColorPresets.CHAT.length);
		assertEquals(0xFFFF55, ColorPresets.CHAT[14], "yellow");
		assertEquals(0x55FFFF, ColorPresets.CHAT[11], "aqua");
	}
}
