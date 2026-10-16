package com.k8bas.skyblockutility.ui.widget

/**
 * When a tooltip shows and how far it has faded in (REQ-UI-44, T3.34), pure, on the time the screen passes in: after the
 * pointer has rested [DELAY_MS] over the same control, or at once when one showed within the last [RECENT_MS] and the pointer
 * moved on to the next; then fading in over [FADE_MS] (at once with motion off, EC-UI-22; the delay stays). A control is its
 * owner, its text and where it lies: the same control scrolled elsewhere starts again, and a control that comes under a still
 * pointer (the page glided) waits the whole delay (EC-UI-26). Allocates nothing.
 */
class TooltipTimer {
	private var owner: Any? = null
	private var text = ""
	private var x = 0
	private var y = 0
	private var width = 0
	private var height = 0
	private var restStart = 0L
	private var shownAt = NONE
	private var lastShown = NONE
	private var alpha = 0F

	/**
	 * Whether a tooltip shows this frame at [now] for [owner]'s [text] beside the control at [x], [y] ([width] by [height]); a
	 * null [owner] or an empty [text] for none (no control under the pointer, or a press held). [moved]: the pointer moved since
	 * the last frame.
	 */
	fun update(now: Long, owner: Any?, text: String, x: Int, y: Int, width: Int, height: Int, moved: Boolean, motion: Boolean): Boolean {
		if (owner == null || text.isEmpty()) {
			this.owner = null
			shownAt = NONE
			alpha = 0F
			return false
		}
		if (owner !== this.owner || text != this.text || x != this.x || y != this.y || width != this.width || height != this.height) {
			this.owner = owner
			this.text = text
			this.x = x
			this.y = y
			this.width = width
			this.height = height
			restStart = now
			shownAt = if (moved && lastShown != NONE && now - lastShown <= RECENT_MS) now else NONE
		}
		if (shownAt == NONE && now - restStart >= DELAY_MS) {
			shownAt = restStart + DELAY_MS
		}
		if (shownAt == NONE) {
			alpha = 0F
			return false
		}
		lastShown = now
		alpha = if (motion) ((now - shownAt).toFloat() / FADE_MS).coerceIn(0F, 1F) else 1F
		return true
	}

	/** The tooltip's opacity from the last [update] that showed one, 0 to 1. */
	fun alpha(): Float = alpha

	/** Starts the delay again for whatever control comes next, with no tooltip shown at once: after a release, a wheel turn or an overlay change. */
	fun restart() {
		owner = null
		shownAt = NONE
		lastShown = NONE
		alpha = 0F
	}

	companion object {
		const val DELAY_MS = 400L
		const val RECENT_MS = 300L
		const val FADE_MS = 100L
		private const val NONE = Long.MIN_VALUE
	}
}
