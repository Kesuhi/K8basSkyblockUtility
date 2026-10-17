package com.k8bas.skyblockutility.ui.widget

/**
 * Which pointer shape a screen asks for this frame (REQ-UI-43, T3.33), pure: a held drag keeps its own shape wherever the
 * mouse goes; otherwise the topmost widget under the mouse decides, as it would take a press there, and a disabled one
 * gives the arrow. Called once per frame; allocates nothing itself.
 */
object PointerPick {
	/**
	 * The shape for the mouse at [x], [y] over [widgets] (drawn in order, so the last is on top; with an overlay open only
	 * its widgets), with [pressed] the widget a held press went to, or null.
	 */
	@JvmStatic
	fun pick(widgets: List<Widget>, pressed: Widget?, x: Double, y: Double): PointerShape {
		pressed?.dragCursor()?.let { return it }
		for (i in widgets.size - 1 downTo 0) {
			val widget = widgets[i]
			if (!widget.contains(x, y) || !widget.inClip(x, y)) {
				continue
			}
			if (!widget.isEnabled) {
				return PointerShape.DEFAULT
			}
			return widget.cursorAt(x, y) ?: continue
		}
		return PointerShape.DEFAULT
	}
}
