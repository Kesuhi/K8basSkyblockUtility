package com.k8bas.skyblockutility.hud;

import net.minecraft.client.gui.Font;

/** How wide text is and how far apart lines are: the game's font when drawing, a stand-in in unit tests. */
public interface TextMeasure {
	int width(String text);

	int lineHeight();

	static TextMeasure of(Font font) {
		return new TextMeasure() {
			@Override
			public int width(String text) {
				return font.width(text);
			}

			@Override
			public int lineHeight() {
				return font.lineHeight;
			}
		};
	}
}
