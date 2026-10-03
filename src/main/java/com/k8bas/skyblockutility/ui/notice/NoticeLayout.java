package com.k8bas.skyblockutility.ui.notice;

import com.k8bas.skyblockutility.ui.render.Easing;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each notice is drawn (REQ-UI-21), in GUI pixels. The oldest sits at the chosen edge and newer
 * ones stack away from it. Corner notices slide in from the side, top-centre ones from above (drawn
 * under the older ones, newest first). A notice leaving slides out in the first half of its slide
 * time, and only then gives up its room, so the stack closes up without boxes crossing. A notice that
 * would not fit in the window is not placed (the newest go first).
 */
public final class NoticeLayout {
	public static final int WIDTH = 160;
	public static final int PADDING = 6;
	public static final int LINE_HEIGHT = 10;
	public static final int GAP = 4;
	public static final int MARGIN = 4;

	private NoticeLayout() {
	}

	public record Placed(NoticeQueue.Entry entry, int x, int y, int width, int height) {
	}

	public static int height(Notice notice) {
		return 2 * PADDING + LINE_HEIGHT * (1 + notice.body().size()) - 1;
	}

	/** Oldest first; draw them in reverse, so a newer notice sliding past an older one passes under it. */
	public static List<Placed> place(List<NoticeQueue.Entry> entries, NoticePosition position, int screenWidth, int screenHeight, long now) {
		List<Placed> placed = new ArrayList<>();
		int width = Math.max(1, Math.min(WIDTH, screenWidth - 2 * MARGIN));
		double offset = MARGIN;
		for (NoticeQueue.Entry entry : entries) {
			int height = height(entry.notice());
			if (offset + height > screenHeight - MARGIN) {
				break;
			}
			float shown = entry.shown(now);
			boolean leaving = now > entry.postedAt() + NoticeQueue.SLIDE_TIME_MS / 2 + entry.durationMs();
			float slide;
			if (!leaving) {
				slide = Easing.outCubic(shown);
			} else if (shown >= 0.5F) {
				// Leaving, first half: slides out and keeps its room.
				slide = Easing.outCubic((shown - 0.5F) * 2);
			} else {
				// Second half: out of sight; its room closes up.
				offset += (height + GAP) * Easing.outCubic(shown * 2);
				continue;
			}
			int stackY = (int) Math.round(offset);
			int x;
			int y = position.top() ? stackY : screenHeight - stackY - height;
			if (position == NoticePosition.TOP_CENTRE) {
				x = (screenWidth - width) / 2;
				y -= Math.round((1 - slide) * (stackY + height));
			} else if (position.left()) {
				x = MARGIN - Math.round((1 - slide) * (width + MARGIN));
			} else {
				x = screenWidth - MARGIN - width + Math.round((1 - slide) * (width + MARGIN));
			}
			placed.add(new Placed(entry, x, y, width, height));
			offset += height + GAP;
		}
		return placed;
	}
}
