package com.k8bas.skyblockutility.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-HUD-13, AC-HUD-12, EC-HUD-10 (T2.7b): the shipped elements' default rectangles do not overlap. */
class HudLayoutTest {
	/**
	 * At least as wide as any glyph of the vanilla font: ASCII is at most 7 px (8 in bold), the Unifont fallback (CJK,
	 * symbols, SkyBlock icons) 9 px (10 in bold). A fit here fits with the real widths, as a larger box with the
	 * same anchor holds the smaller one.
	 */
	private static final TextMeasure GENEROUS = new TextMeasure() {
		@Override
		public int width(String text) {
			return text.length() * 10;
		}

		@Override
		public int lineHeight() {
			return 9;
		}
	};

	/** AC-HUD-12 [A], re-run by every later HUD consumer: preview content at 1920×1080, GUI scale 2. */
	@Test
	void theShippedDefaultsDoNotOverlap() {
		List<String> overlaps = HudLayout.overlaps(ShippedHud.elements(), GENEROUS, 960, 540);
		assertTrue(overlaps.isEmpty(), "default rectangles overlap: " + overlaps);
	}

	@Test
	void theCheckFindsAnOverlap() {
		HudElement left = element("left", new HudPosition(HudAnchor.TOP_LEFT, 4, 4, 1), "a line of preview text");
		HudElement below = element("below", new HudPosition(HudAnchor.TOP_LEFT, 4, 40, 1), "another");
		HudElement onTop = element("on_top", new HudPosition(HudAnchor.TOP_LEFT, 30, 6, 1), "another");
		assertEquals(List.of(), HudLayout.overlaps(List.of(left, below), GENEROUS, 960, 540));
		assertEquals(List.of("left and on_top"), HudLayout.overlaps(List.of(left, below, onTop), GENEROUS, 960, 540));
		// 6 characters: 64×13 at scale 1 (y 4-17) clears the box below at y 20; at scale 2 (y 4-30) it reaches it.
		HudElement box = element("box", new HudPosition(HudAnchor.TOP_LEFT, 4, 20, 1), "x");
		assertEquals(List.of(), HudLayout.overlaps(List.of(element("a", new HudPosition(HudAnchor.TOP_LEFT, 4, 4, 1), "a line"), box), GENEROUS, 960, 540));
		assertEquals(List.of("a and box"), HudLayout.overlaps(List.of(element("a", new HudPosition(HudAnchor.TOP_LEFT, 4, 4, 2), "a line"), box), GENEROUS,
				960, 540), "the scale counts");
	}

	/** Edges that only touch are not an overlap. */
	@Test
	void touchingIsNotOverlapping() {
		HudRect a = new HudRect(0, 0, 10, 10);
		assertTrue(!HudLayout.intersect(a, new HudRect(10, 0, 10, 10)));
		assertTrue(!HudLayout.intersect(a, new HudRect(0, 10, 10, 10)));
		assertTrue(HudLayout.intersect(a, new HudRect(9.5, 9.5, 10, 10)));
	}

	private static HudElement element(String id, HudPosition position, String preview) {
		return new HudElement() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public String displayName() {
				return id;
			}

			@Override
			public boolean enabled() {
				return true;
			}

			@Override
			public HudContent content() {
				return null;
			}

			@Override
			public HudContent preview() {
				return HudText.of(List.of(preview), line -> true);
			}

			@Override
			public HudPosition defaultPosition() {
				return position;
			}
		};
	}
}
