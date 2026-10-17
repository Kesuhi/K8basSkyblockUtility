package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-61 (REQ-UI-57, T3.50): from the theme's colours, an enabled off toggle's track has a contrast of at least 1.8:1 against
 * the card and 1.5:1 against the hovered card, its knob at least 3:1 against the track; a disabled toggle's track and knob
 * keep a lower contrast against the card than the enabled off one's. Contrast as WCAG 2 defines it.
 */
class ToggleContrastTest {
	private static final int ACCENT = 0xFF29B6B2;

	@Test
	void anOffToggleStandsOutFromItsCard() {
		int track = ToggleSwitch.trackColour(0F, ACCENT, true);
		int knob = ToggleSwitch.knobColour(0F, true);
		double card = contrast(track, Theme.CARD);
		double hovered = contrast(track, Theme.CARD_HOVER);
		double knobOnTrack = contrast(knob, track);
		assertTrue(card >= 1.8, "the off track against the card: " + card);
		assertTrue(hovered >= 1.5, "against the hovered card: " + hovered);
		assertTrue(knobOnTrack >= 3, "the off knob against its track: " + knobOnTrack);
	}

	@Test
	void aDisabledToggleStaysDimmerThanAnEnabledOffOne() {
		int track = ToggleSwitch.trackColour(0F, ACCENT, true);
		int knob = ToggleSwitch.knobColour(0F, true);
		int disabledTrack = ToggleSwitch.trackColour(0F, ACCENT, false);
		int disabledKnob = ToggleSwitch.knobColour(0F, false);
		assertTrue(contrast(disabledTrack, Theme.CARD) < contrast(track, Theme.CARD), "the disabled track is dimmer");
		assertTrue(contrast(disabledKnob, Theme.CARD) < contrast(knob, Theme.CARD), "and its knob");
	}

	/** The WCAG 2 contrast ratio of two opaque colours. */
	static double contrast(int a, int b) {
		double x = luminance(a);
		double y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	private static double luminance(int argb) {
		double r = channel(argb >> 16 & 0xFF);
		double g = channel(argb >> 8 & 0xFF);
		double b = channel(argb & 0xFF);
		return 0.2126 * r + 0.7152 * g + 0.0722 * b;
	}

	private static double channel(int value) {
		double c = value / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}
}
