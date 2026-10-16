package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Easing;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.UiTime;

/**
 * An overlay's open transition (REQ-UI-38, T3.29): from the moment it opens, its opacity eases from 0 to 1 and its scale from
 * {@link #FROM_SCALE} to 1 about its anchor over {@link #DURATION_MS}, easing out, and the modal backdrop's alpha follows the
 * same curve; with motion off (Animations OFF) it is open at once. Only the drawing follows it: input meets the overlay's
 * final, unscaled layout from the first frame. There is no close transition. Pure and on an injected clock source.
 */
public final class OverlayTransition {
	public static final long DURATION_MS = 120;
	public static final float FROM_SCALE = 0.96F;

	private final UiTime time;
	private final long openedAt;

	public OverlayTransition(UiTime time) {
		this.time = time;
		this.openedAt = time.millis();
	}

	/** How far it is open: 0 at the start, 1 from {@link #DURATION_MS} on (and with motion off), easing out. */
	public float opacity() {
		if (!time.motion()) {
			return 1F;
		}
		long elapsed = time.millis() - openedAt;
		if (elapsed >= DURATION_MS) {
			return 1F;
		}
		return elapsed <= 0 ? 0F : Easing.outCubic((float) elapsed / DURATION_MS);
	}

	public float scale() {
		return FROM_SCALE + (1F - FROM_SCALE) * opacity();
	}

	/** The backdrop's alpha now, for its full {@code alpha}. */
	public int backdropAlpha(int alpha) {
		return Math.round(alpha * opacity());
	}

	/**
	 * The map that draws the box from (x0, y0) to (x1, y1) onto {@link #box}: {toX, toY, scaleX, scaleY}, so a point (x, y) is
	 * drawn at (toX + scaleX (x - x0), toY + scaleY (y - y0)); null once open, when it is drawn as it is.
	 */
	public float[] pose(int x0, int y0, int x1, int y1, float ax, float ay, int guiScale) {
		if (scale() >= 1F || x1 <= x0 || y1 <= y0) {
			return null;
		}
		float[] to = box(x0, y0, x1, y1, ax, ay, guiScale);
		return new float[] {to[0], to[1], (to[2] - to[0]) / (x1 - x0), (to[3] - to[1]) / (y1 - y0)};
	}

	/**
	 * Where the box from (x0, y0) to (x1, y1) is drawn now, scaled about the anchor (ax, ay): {left, top, right, bottom} in
	 * GUI px, each edge on the physical-pixel grid at GUI scale {@code guiScale} (REQ-UI-34). Once open, the box itself.
	 */
	public float[] box(int x0, int y0, int x1, int y1, float ax, float ay, int guiScale) {
		float s = scale();
		if (s >= 1F) {
			return new float[] {x0, y0, x1, y1};
		}
		return new float[] {Shapes.snap(ax + (x0 - ax) * s, guiScale), Shapes.snap(ay + (y0 - ay) * s, guiScale),
				Shapes.snap(ax + (x1 - ax) * s, guiScale), Shapes.snap(ay + (y1 - ay) * s, guiScale)};
	}
}
