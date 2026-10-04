package com.k8bas.skyblockutility.hud;

import java.util.ArrayList;
import java.util.List;

/** The default layout check (REQ-HUD-13): which elements' default rectangles, with preview content, overlap. Pure. */
public final class HudLayout {
	private HudLayout() {
	}

	/** "a and b" for each overlapping pair, on a {@code screenW}×{@code screenH} GUI. */
	public static List<String> overlaps(List<HudElement> elements, TextMeasure text, double screenW, double screenH) {
		List<HudRect> rects = new ArrayList<>();
		for (HudElement element : elements) {
			HudContent preview = element.preview();
			HudPosition position = element.defaultPosition();
			rects.add(preview == null ? null : HudRect.onPixels(position.place(screenW, screenH, preview.width(text) * position.scale(),
					preview.height(text) * position.scale()), screenW, screenH));
		}
		List<String> found = new ArrayList<>();
		for (int i = 0; i < rects.size(); i++) {
			for (int j = i + 1; j < rects.size(); j++) {
				if (rects.get(i) != null && rects.get(j) != null && intersect(rects.get(i), rects.get(j))) {
					found.add(elements.get(i).id() + " and " + elements.get(j).id());
				}
			}
		}
		return found;
	}

	/** Whether they share any area; edges that only touch do not. */
	public static boolean intersect(HudRect a, HudRect b) {
		return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
	}
}
