package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Soft fades at a scrolling region's top and bottom edges (REQ-UI-56, T3.49): where more content lies beyond an edge, the
 * content fades into the region's background over {@link #DEPTH} GUI px, as strongly as there is to scroll there, full from
 * {@link #DEPTH} GUI px on; an edge with nothing beyond has none. Drawn above the content and below the scrollbar, one
 * physical-pixel row at a time; it changes no layout and no input, so a control under it still takes clicks. Used by the
 * settings' content area, the sidebar, the database picker's list and open dropdown lists.
 */
public final class EdgeFade {
	/** How deep a full fade reaches into the region, in GUI px. */
	public static final int DEPTH = 8;

	private EdgeFade() {
	}

	/** How strong the fade at an edge is, 0 to 1, with {@code beyond} GUI px of content past it. */
	public static float strength(float beyond) {
		return Math.max(0F, Math.min(1F, beyond / DEPTH));
	}

	/**
	 * The alpha, 0 to 255, of the physical-pixel row {@code row} counted from the edge (0 at the edge) of a fade {@code rows}
	 * rows deep at {@code strength}: highest at the edge, falling to near 0 at its inner end.
	 */
	public static int rowAlpha(int row, int rows, float strength) {
		if (rows <= 0 || row < 0 || row >= rows) {
			return 0;
		}
		return Math.round(255F * strength * (1F - (row + 0.5F) / rows));
	}

	/**
	 * Draws both fades of a region from GUI (x, y), w × h, scrolled to {@code shown} of {@code max} GUI px, in its
	 * {@code background} colour (its alpha is replaced by the fade's).
	 */
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float shown, int max, int background) {
		if (max <= 0 || w <= 0 || h <= 0) {
			return;
		}
		int scale = Shapes.guiScale();
		int rows = Math.min(DEPTH, h / 2) * scale;
		float top = strength(shown);
		float bottom = strength(max - shown);
		float pixel = 1F / scale;
		int rgb = background & 0xFFFFFF;
		for (int row = 0; row < rows; row++) {
			float offset = row * pixel;
			int above = rowAlpha(row, rows, top);
			if (above > 0) {
				Shapes.fill(graphics, x, y + offset, x + w, y + offset + pixel, above << 24 | rgb);
			}
			int below = rowAlpha(row, rows, bottom);
			if (below > 0) {
				Shapes.fill(graphics, x, y + h - offset - pixel, x + w, y + h - offset, below << 24 | rgb);
			}
		}
	}
}
