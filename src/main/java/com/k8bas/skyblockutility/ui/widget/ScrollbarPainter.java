package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Animated;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiTime;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntUnaryOperator;

/**
 * The one scrollbar look of every scrolling region (REQ-UI-36, T3.27): a pill thumb {@link #IDLE_WIDTH} GUI px wide, inset
 * {@link #INSET} GUI px from the region's right edge, that widens to {@link #WIDE_WIDTH} over a faint track while hovered or
 * dragged, its width and colour eased over 150 ms on the UI clock. Its hit area, {@link #HIT_WIDTH} wide at the region's
 * right edge, is the same in every state and takes in every column of the widened thumb. The thumb's length and place come from the region ({@link VirtualRows#thumb}).
 * One per region, as it keeps the easing.
 */
public final class ScrollbarPainter {
	public static final int IDLE_WIDTH = 3;
	public static final int WIDE_WIDTH = 5;
	public static final int INSET = 2;
	public static final int HIT_WIDTH = INSET + WIDE_WIDTH;
	static final long EASE_MS = 150;

	private final Animated wide;

	public ScrollbarPainter() {
		this(UiClock.SOURCE);
	}

	public ScrollbarPainter(UiTime time) {
		this.wide = new Animated(0F, EASE_MS, time);
	}

	/** Whether a pointer at {@code mouseX} is over the scrollbar of a region from {@code x}, {@code width} wide. */
	public static boolean inHitArea(int x, int width, double mouseX) {
		return mouseX >= x + width - HIT_WIDTH && mouseX < x + width;
	}

	/** The thumb's right edge in GUI px, for a region from {@code x}, {@code width} wide. */
	public static float thumbRight(int x, int width) {
		return x + width - INSET;
	}

	/** The thumb's width in GUI px now, easing toward {@link #WIDE_WIDTH} while {@code hot}, else toward {@link #IDLE_WIDTH}. */
	public float width(boolean hot) {
		wide.animateTo(hot ? 1F : 0F);
		return IDLE_WIDTH + (WIDE_WIDTH - IDLE_WIDTH) * wide.value();
	}

	/**
	 * Draws the scrollbar of a region from (x, y), width × height GUI px, whose thumb lies from {@code thumbTop} for
	 * {@code thumbLength} GUI px down from the region's top; {@code fade} dims a colour as its widget does.
	 */
	public void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float thumbTop, int thumbLength, boolean hot,
			IntUnaryOperator fade) {
		float w = width(hot);
		float t = (w - IDLE_WIDTH) / (WIDE_WIDTH - IDLE_WIDTH);
		float right = thumbRight(x, width);
		if (t > 0F) {
			// The faint track, as wide as the thumb, shows as it widens.
			int track = ColorMath.withAlpha(Math.round(0x80 * t), Theme.SEPARATOR & 0xFFFFFF);
			Shapes.pillV(graphics, right - w, y + 1, right, y + height - 1, fade.applyAsInt(track));
		}
		int colour = ColorMath.lerp(Theme.TEXT_DISABLED, Theme.TEXT_SECONDARY, t);
		Shapes.pillV(graphics, right - w, y + thumbTop + 1, right, y + thumbTop + thumbLength - 1, fade.applyAsInt(colour));
	}
}
