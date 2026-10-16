package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.Animated;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.UiTime;

/**
 * Where the sidebar's selected-tab highlight and its accent bar are (REQ-UI-45, T3.35): on a category switch they slide from
 * the old tab to the new one over 150 ms, easing out (cubic), from wherever they are; with Animations OFF they are there at
 * once. The position counts in tabs, so it holds while the sidebar scrolls; it is drawn on the physical-pixel grid
 * (REQ-UI-34).
 */
final class TabIndicator {
	private final Animated position;

	TabIndicator(UiTime time) {
		position = new Animated(0F, Animated.DEFAULT_DURATION_MS, time);
	}

	/** A category switch: slides to tab {@code index}. */
	void slideTo(int index) {
		position.animateTo(index);
	}

	/** The tabs changed under it (a search): to tab {@code index} with no slide. */
	void jumpTo(int index) {
		position.snapTo(index);
	}

	/** The tab it is going to. */
	int target() {
		return Math.round(position.target());
	}

	/** Where it is now, in tabs from the first. */
	float position() {
		return position.value();
	}

	/** Its top where the first tab's top is {@code firstTop} and tabs are {@code pitch} GUI px apart, on the grid of GUI scale {@code scale}. */
	float drawnTop(float firstTop, int pitch, int scale) {
		return Shapes.snap(firstTop + position() * pitch, scale);
	}
}
