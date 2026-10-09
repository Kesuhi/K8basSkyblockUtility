package com.k8bas.skyblockutility.ui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Text in the vanilla font without a drop shadow (REQ-UI-17, D-8). */
public final class UiText {
	private UiText() {
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int argb) {
		graphics.text(font, text, x, y, argb, false);
	}

	/** Centred on {@code centreX}; vanilla's centred text always has a shadow, so it is not used. */
	public static void centred(GuiGraphicsExtractor graphics, Font font, String text, int centreX, int y, int argb) {
		draw(graphics, font, text, centreX - font.width(text) / 2, y, argb);
	}

	public static void rightAligned(GuiGraphicsExtractor graphics, Font font, String text, int rightX, int y, int argb) {
		draw(graphics, font, text, rightX - font.width(text), y, argb);
	}

	/** Cut with "..." to {@code maxWidth}; returns whether it was cut (then a tooltip shows the whole text). */
	public static boolean truncated(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int maxWidth, int argb) {
		String shown = Ellipsis.fit(text, maxWidth, font::width);
		if (!shown.isEmpty()) {
			draw(graphics, font, shown, x, y, argb);
		}
		return !shown.equals(text);
	}
}
