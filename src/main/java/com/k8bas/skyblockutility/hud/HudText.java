package com.k8bas.skyblockutility.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Multi-line text content (REQ-HUD-12): the feature supplies its lines and its options say which show.
 * Its bounds are the drawn text, shadow included, plus {@link #MARGIN} on every side; a feature whose lines change makes a
 * new one, so the bounds follow the content. Lines may carry § formatting.
 */
public record HudText(List<String> lines) implements HudContent {
	public static final int MARGIN = 2;
	private static final int TEXT = 0xFFFFFFFF;

	public HudText {
		lines = List.copyOf(lines);
	}

	/** The lines whose index {@code shown} accepts, or null (nothing to draw) when none is. */
	public static HudText of(List<String> lines, IntPredicate shown) {
		List<String> kept = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			if (shown.test(i)) {
				kept.add(lines.get(i));
			}
		}
		return kept.isEmpty() ? null : new HudText(kept);
	}

	@Override
	public int width(TextMeasure text) {
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, text.width(line));
		}
		return widest + 2 * MARGIN;
	}

	/** Each line's glyphs and their shadow fill its line height: a descender's shadow reaches the last row. */
	@Override
	public int height(TextMeasure text) {
		return lines.size() * text.lineHeight() + 2 * MARGIN;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font) {
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), MARGIN, MARGIN + i * font.lineHeight, TEXT, true);
		}
	}
}
