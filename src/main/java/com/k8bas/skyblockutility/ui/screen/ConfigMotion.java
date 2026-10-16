package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.Easing;
import com.k8bas.skyblockutility.ui.render.Shapes;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The settings screen's motion (REQ-UI-18, T2.9): the panel opens with a scale of 0.90 to 1.00 over 220 ms,
 * and a category switch slides the content 12 px and fades it in over 200 ms. A request only arms an
 * animation; it starts on the next [frame], so a slow first frame does not eat it. The clock is read once
 * per frame and the values are kept until the next, so drawing and the input that follows see the same
 * frame. Pure and on an injected clock; per-frame, so primitives only.
 */
final class ConfigMotion {
	static final long OPEN_MS = 220;
	static final long SWITCH_MS = 200;
	static final float OPEN_FROM = 0.90F;
	/** How far a new category's content slides into place, in GUI px. */
	static final int SLIDE_PX = 12;

	private final LongSupplier clock;
	private final BooleanSupplier enabled;

	private boolean openArmed;
	private boolean opening;
	private long openStart;
	private boolean switchArmed;
	private boolean switching;
	private long switchStart;
	private int direction;

	private float scale = 1F;
	/** How far the slide still has to go this frame, in GUI px, unrounded and unsigned. */
	private float slideRest;
	private int slide;
	private int veil;

	/**
	 * @param clock   milliseconds, of the screen's UI clock source (REQ-UI-39)
	 * @param enabled that source's motion; false (Animations OFF, the gametests' default): nothing animates
	 */
	ConfigMotion(LongSupplier clock, BooleanSupplier enabled) {
		this.clock = clock;
		this.enabled = enabled;
	}

	/** Arms the open animation. */
	void open() {
		openArmed = true;
	}

	/** Arms the slide-and-fade from one tab to another; the same tab does nothing, a new switch restarts it. */
	void switchCategory(int fromIndex, int toIndex) {
		if (fromIndex == toIndex) {
			return;
		}
		direction = Integer.signum(toIndex - fromIndex);
		switchArmed = true;
	}

	/** Settles both animations at once (an overlay opened mid-animation). */
	void finish() {
		openArmed = false;
		opening = false;
		switchArmed = false;
		switching = false;
		scale = 1F;
		slideRest = 0F;
		slide = 0;
		veil = 0;
	}

	/** Reads the clock and works out this frame's values; called once at the start of each layout. */
	void frame() {
		if (!enabled.getAsBoolean()) {
			finish();
			return;
		}
		long now = clock.getAsLong();
		if (openArmed) {
			openArmed = false;
			opening = true;
			openStart = now;
		}
		if (switchArmed) {
			switchArmed = false;
			switching = true;
			switchStart = now;
		}
		scale = 1F;
		if (opening) {
			float progress = progress(now, openStart, OPEN_MS);
			if (progress >= 1F) {
				opening = false;
			} else {
				scale = OPEN_FROM + (1F - OPEN_FROM) * Easing.outCubic(progress);
			}
		}
		slideRest = 0F;
		slide = 0;
		veil = 0;
		if (switching) {
			float progress = progress(now, switchStart, SWITCH_MS);
			if (progress >= 1F) {
				switching = false;
			} else {
				float rest = 1F - Easing.outCubic(progress);
				slideRest = rest * SLIDE_PX;
				slide = Math.round(slideRest) * direction;
				veil = Math.round(rest * 255);
			}
		}
	}

	/** 0..1; a clock that went back reads as the start. */
	private static float progress(long now, long start, long duration) {
		if (now <= start) {
			return 0F;
		}
		return Math.min(1F, (float) (now - start) / duration);
	}

	/** The panel's scale about the screen centre this frame: 0.90 to 1.00. */
	float scale() {
		return scale;
	}

	/** How far the content is laid out below (positive) or above its place this frame, in whole GUI px: where clicks meet it. */
	int slideOffset() {
		return slide;
	}

	/**
	 * How far the content is drawn below or above its place this frame (REQ-UI-34): on the physical-pixel grid at GUI scale
	 * {@code scale}, within half a GUI px of {@link #slideOffset()}.
	 */
	float slideDrawn(int scale) {
		return slideRest == 0F ? 0F : Shapes.snap(slideRest, scale) * direction;
	}

	/** The alpha of the panel-coloured veil over the content this frame: 255 at the switch, 0 once settled. */
	int veilAlpha() {
		return veil;
	}

	boolean animating() {
		return opening || switching || openArmed || switchArmed;
	}
}
