package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.UiTime;

/**
 * A scroll that glides (REQ-UI-35, T3.26): a whole-GUI-px target, and a shown offset that eases toward it exponentially
 * with a time constant of {@link #TIME_CONSTANT_MS}. The shown offset is worked out from the time since the target last
 * changed and where it was shown then, so it is the same at any frame rate, never passes the target and is at it from
 * {@link #SETTLE_MS} on. The layout runs at the shown offset rounded to whole GUI px ({@link #laidOut()}, where clicks
 * meet it); the rest is drawn as a translation on the physical-pixel grid ({@link #drawn}). With motion off (Animations
 * OFF) the shown offset is the target. Pure and on an injected clock source; per-frame, so primitives only.
 */
public final class SmoothScroll {
	public static final float TIME_CONSTANT_MS = 50F;
	public static final long SETTLE_MS = 400;

	private final UiTime time;
	private int max;
	private int target;
	/** The shown offset when the target last changed, and when that was. */
	private float from;
	private long changedAt;
	private int scale;

	public SmoothScroll(UiTime time) {
		this.time = time;
		this.changedAt = time.millis();
	}

	/** The largest offset (the content's height less the viewport's); a target or shown offset beyond it is pulled back. */
	public void setMax(int max) {
		this.max = Math.max(0, max);
		if (target > this.max) {
			jumpTo(this.max);
		}
	}

	public int max() {
		return max;
	}

	/**
	 * The GUI scale it is drawn at; a change (EC-UI-18) puts the shown offset on the target at once, with no glide from
	 * where the old scale had it.
	 */
	public void setScale(int scale) {
		if (this.scale != 0 && scale != this.scale) {
			jumpTo(target);
		}
		this.scale = scale;
	}

	public int target() {
		return target;
	}

	/** The shown offset now, in GUI px: between where it was shown when the target last changed and the target. */
	public float shown() {
		if (!time.motion()) {
			return target;
		}
		long elapsed = time.millis() - changedAt;
		if (elapsed >= SETTLE_MS) {
			return target;
		}
		// A clock that went back reads as the start.
		float shown = elapsed <= 0 ? from : target + (from - target) * (float) Math.exp(-elapsed / TIME_CONSTANT_MS);
		return Math.max(0F, Math.min(max, shown));
	}

	/** The offset the layout runs at, and clicks meet: the shown offset rounded to whole GUI px. */
	public int laidOut() {
		return Math.round(shown());
	}

	/** The offset drawn at GUI scale {@code scale}: the shown offset on the physical-pixel grid, the target within half a physical px. */
	public float drawn(int scale) {
		float shown = shown();
		return Math.abs(shown - target) < 0.5F / scale ? target : Shapes.snap(shown, scale);
	}

	public boolean gliding() {
		return shown() != target;
	}

	/** Glides to {@code target} from the shown offset (a wheel notch, bringing a control into view). */
	public void glideTo(int target) {
		if (!time.motion()) {
			jumpTo(target);
			return;
		}
		int clamped = clamp(target);
		// The time counts from when the target last changed (a notch held at the end, a repeated reveal change nothing).
		if (clamped == this.target) {
			return;
		}
		float shown = shown();
		this.target = clamped;
		from = shown;
		changedAt = time.millis();
	}

	public void glideBy(int delta) {
		glideTo(target + delta);
	}

	/** Shows {@code offset} at once (a thumb drag, a press on the track, a restored scroll). */
	public void jumpTo(int offset) {
		target = clamp(offset);
		from = target;
		changedAt = time.millis();
	}

	/** Ends a glide where it is laid out now (a press during the glide, EC-UI-25), so what is under the pointer stays. */
	public void stop() {
		jumpTo(laidOut());
	}

	private int clamp(int offset) {
		return Math.max(0, Math.min(max, offset));
	}
}
