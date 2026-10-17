package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The checkerboard behind a translucent colour (REQ-UI-60, T3.50): square cells of {@link #CELL} GUI px, light and dark,
 * starting at the shape's top-left corner, so every swatch, preview and alpha track shows the same pattern. Each cell is its
 * own fill, so a dimmed board keeps its pattern (its colours go through the widget's dimming, as the colour over it does);
 * a cell that reaches into a rounded corner is drawn one physical-pixel row at a time, each row cut where the corner's curve
 * crosses it, so the board keeps the shape's rounding. Whole GUI px otherwise, on the physical-pixel grid.
 */
public final class Checkerboard {
	/** A cell's side, in GUI px. */
	public static final int CELL = 3;
	static final int LIGHT = 0xFF9A9A9A;
	static final int DARK = 0xFF5E5E5E;

	private Checkerboard() {
	}

	/**
	 * The cells of a w × h GUI px board: {x, y, w, h, dark} from its top-left, the last row and column cut at its edges, dark 1
	 * where the pattern has a dark cell (the one at the corner is light).
	 */
	static List<int[]> cells(int w, int h) {
		List<int[]> cells = new ArrayList<>();
		for (int cy = 0; cy < h; cy += CELL) {
			for (int cx = 0; cx < w; cx += CELL) {
				cells.add(new int[] {cx, cy, Math.min(CELL, w - cx), Math.min(CELL, h - cy), (cx / CELL + cy / CELL) % 2});
			}
		}
		return cells;
	}

	/**
	 * How many physical px a corner rounded by {@code radius} GUI px cuts from the row {@code row} physical px from the shape's
	 * top (or bottom) edge at GUI scale {@code scale}: the circle's inset at that row's middle, rounded; 0 below the corner.
	 */
	static int rowInset(int radius, int row, int scale) {
		float r = radius * scale;
		if (row >= r) {
			return 0;
		}
		float dy = r - (row + 0.5F);
		return Math.round(r - (float) Math.sqrt(Math.max(0F, r * r - dy * dy)));
	}

	/** Draws the board over GUI (x, y), w × h, with corners rounded by {@code radius}, its colours through {@code fade}. */
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, IntUnaryOperator fade) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int light = fade.applyAsInt(LIGHT);
		int dark = fade.applyAsInt(DARK);
		int scale = Shapes.guiScale();
		int r = Math.min(radius, Math.min(w, h) / 2);
		for (int[] cell : cells(w, h)) {
			int colour = cell[4] == 1 ? dark : light;
			boolean corner = r > 0 && (cell[0] < r || cell[0] + cell[2] > w - r) && (cell[1] < r || cell[1] + cell[3] > h - r);
			if (!corner) {
				Shapes.plain(graphics, x + cell[0], y + cell[1], x + cell[0] + cell[2], y + cell[1] + cell[3], colour);
				continue;
			}
			// In a corner: row by physical row, each cut where the curve crosses it.
			for (int row = cell[1] * scale; row < (cell[1] + cell[3]) * scale; row++) {
				int fromEdge = Math.min(row, h * scale - 1 - row);
				int inset = rowInset(r, fromEdge, scale);
				float left = Math.max(cell[0] * scale, inset);
				float right = Math.min((cell[0] + cell[2]) * scale, w * scale - inset);
				if (right > left) {
					Shapes.fill(graphics, x + left / scale, y + (float) row / scale, x + right / scale, y + (float) (row + 1) / scale, colour);
				}
			}
		}
	}
}
