package com.k8bas.skyblockutility.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What an element shows, already worked out (REQ-HUD-11): its size and how it draws itself, from its
 * top-left at (0, 0) and at scale 1; the framework places and scales it. Built off the draw path (on a
 * tick or an event), so drawing only reads it. Multi-line text is {@link HudText}.
 */
public interface HudContent {
	int width(TextMeasure text);

	int height(TextMeasure text);

	void draw(GuiGraphicsExtractor graphics, Font font);
}
