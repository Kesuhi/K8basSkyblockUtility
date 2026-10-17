package com.k8bas.skyblockutility.ui.notice;

import com.k8bas.skyblockutility.ui.render.Easing;
import com.k8bas.skyblockutility.ui.render.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Where each notice is drawn (REQ-UI-21), in GUI pixels. The oldest sits at the chosen edge and newer
 * ones stack away from it. Corner notices slide in from the side, top-centre ones from above (drawn
 * under the older ones, newest first). A notice leaving slides out in the first half of its slide
 * time, and only then gives up its room, so the stack closes up without boxes crossing. A notice that
 * would not fit in the window is not placed (the newest go first).
 */
public final class NoticeLayout {
	public static final int WIDTH = 160;
	/** A notice with longer text (a backup file's name) grows up to this, within the window. */
	public static final int MAX_WIDTH = 360;
	public static final int PADDING = 6;
	public static final int LINE_HEIGHT = 10;
	public static final int GAP = 4;
	public static final int MARGIN = 4;

	private NoticeLayout() {
	}

	/**
	 * A notice's box: x and y in whole GUI px, and where it is drawn, on the physical-pixel grid (REQ-UI-34), within half a
	 * GUI px of them; the same once it is in place.
	 */
	public record Placed(NoticeQueue.Entry entry, int x, int y, int width, int height, float drawnX, float drawnY) {
	}

	/** At least {@link #WIDTH}, more for longer text, at most {@link #MAX_WIDTH} and never wider than the window. */
	static int width(Notice notice, int screenWidth, ToIntFunction<String> measure) {
		int text = measure.applyAsInt(notice.title());
		for (String line : notice.body()) {
			text = Math.max(text, measure.applyAsInt(line));
		}
		int wanted = Math.max(WIDTH, text + 2 * PADDING + 2);
		return Math.max(1, Math.min(Math.min(wanted, MAX_WIDTH), screenWidth - 2 * MARGIN));
	}

	public static int height(Notice notice) {
		return 2 * PADDING + LINE_HEIGHT * (1 + notice.body().size()) - 1;
	}

	/** Oldest first; draw them in reverse, so a newer notice sliding past an older one passes under it. */
	public static List<Placed> place(List<NoticeQueue.Entry> entries, NoticePosition position, int screenWidth, int screenHeight, long now) {
		return place(entries, position, screenWidth, screenHeight, now, text -> 0);
	}

	/** As above; a notice whose text is wider than {@link #WIDTH} allows grows to fit it, up to {@link #MAX_WIDTH}. */
	public static List<Placed> place(List<NoticeQueue.Entry> entries, NoticePosition position, int screenWidth, int screenHeight, long now,
			ToIntFunction<String> measure) {
		return place(entries, position, screenWidth, screenHeight, now, measure, true);
	}

	/**
	 * As above; with {@code motion} off (Animations OFF, REQ-UI-39) a notice is in place from its first frame and a leaving one
	 * is gone at once, its room closed.
	 */
	public static List<Placed> place(List<NoticeQueue.Entry> entries, NoticePosition position, int screenWidth, int screenHeight, long now,
			ToIntFunction<String> measure, boolean motion) {
		return place(entries, position, screenWidth, screenHeight, now, measure, motion, 1);
	}

	/** As above, the slides drawn on the physical-pixel grid at GUI scale {@code scale} (REQ-UI-34). */
	public static List<Placed> place(List<NoticeQueue.Entry> entries, NoticePosition position, int screenWidth, int screenHeight, long now,
			ToIntFunction<String> measure, boolean motion, int scale) {
		List<Placed> placed = new ArrayList<>();
		double offset = MARGIN;
		for (NoticeQueue.Entry entry : entries) {
			int height = height(entry.notice());
			int width = width(entry.notice(), screenWidth, measure);
			if (offset + height > screenHeight - MARGIN) {
				break;
			}
			float shown = entry.shown(now);
			boolean leaving = now > entry.postedAt() + NoticeQueue.SLIDE_TIME_MS / 2 + entry.durationMs();
			float slide;
			if (!motion) {
				if (leaving) {
					continue;
				}
				slide = 1F;
			} else if (!leaving) {
				slide = Easing.outCubic(shown);
			} else if (shown >= 0.5F) {
				// Leaving, first half: slides out and keeps its room.
				slide = Easing.outCubic((shown - 0.5F) * 2);
			} else {
				// Second half: out of sight; its room closes up.
				offset += (height + GAP) * Easing.outCubic(shown * 2);
				continue;
			}
			// On the physical-pixel grid; at GUI scale 1 these are whole GUI px.
			float stackY = Shapes.snap((float) offset, scale);
			float x;
			float y = position.top() ? stackY : screenHeight - stackY - height;
			if (position == NoticePosition.TOP_CENTRE) {
				x = (screenWidth - width) / 2;
				y -= Shapes.snap((1 - slide) * (stackY + height), scale);
			} else if (position.left()) {
				x = MARGIN - Shapes.snap((1 - slide) * (width + MARGIN), scale);
			} else {
				x = screenWidth - MARGIN - width + Shapes.snap((1 - slide) * (width + MARGIN), scale);
			}
			placed.add(new Placed(entry, Math.round(x), Math.round(y), width, height, x, y));
			offset += height + GAP;
		}
		return placed;
	}
}
