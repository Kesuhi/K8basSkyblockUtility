package com.k8bas.skyblockutility.hud

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * What the HUD editor changes, without drawing or input plumbing, so it is tested without a game
 * (REQ-HUD-05, REQ-HUD-08). Coordinates are GUI pixels on a `screenW`×`screenH` screen,
 * given with each call, so a resize keeps the selection and the unsaved changes (EC-HUD-01).
 *
 * @param opened every item's position when the editor opened
 */
class HudEditorModel(items: List<Item>, opened: Map<String, HudPosition>, private val sizes: Sizes) {
	/** An element as the editor knows it. */
	@JvmRecord
	data class Item(val id: String, val defaultPosition: HudPosition) {
		override fun toString(): String = "Item[id=$id, defaultPosition=$defaultPosition]"
	}

	/** An element's size at scale 1 now (its live content, else its preview). */
	interface Sizes {
		fun width(id: String): Double

		fun height(id: String): Double
	}

	private val items: List<Item> = java.util.List.copyOf(items)
	private val opened: Map<String, HudPosition> = java.util.Map.copyOf(opened)
	private val working: MutableMap<String, HudPosition> = LinkedHashMap(opened)

	@get:JvmName("selected")
	var selected: String? = null
		private set
	private var dragging = false

	/** Whether the current drag has moved the element; a click that only selects re-anchors nothing. */
	private var moved = false

	/** Where the element was grabbed, from its top-left. */
	private var grabX = 0.0
	private var grabY = 0.0

	/** Where the press was: snapping starts only once the mouse is [SNAP_AFTER] px from it (then for the rest of the drag),
	 * so a click that only selects, with a hand's sub-pixel jitter, never moves the element (REQ-HUD-08). */
	private var pressX = 0.0
	private var pressY = 0.0
	private var snapping = false

	/** The guide lines of the last snap (REQ-HUD-14), kept, so drawing them allocates nothing; with the screen size and the
	 * place they were made for, so a resize or a scale change mid-drag shows none rather than stale ones. */
	private var guides: List<HudSnap.Guide> = java.util.List.of()
	private var guidesFor: HudRect? = null
	private var guidesScreenW = 0.0
	private var guidesScreenH = 0.0

	/** The corner whose handle is dragged (0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right), or -1 (REQ-HUD-15). */
	private var handle = -1

	/** The opposite corner as drawn (the outline spans the size rounded up), which stays where it is, and the dragged corner as it
	 * was at the press. */
	private var fixedX = 0.0
	private var fixedY = 0.0
	private var cornerX = 0.0
	private var cornerY = 0.0

	fun items(): List<Item> = items

	/** Whether a corner handle is dragged: the element scales about the opposite corner (REQ-HUD-15). */
	fun scaling(): Boolean = handle >= 0

	/**
	 * The selected element's four handle centres, x and y for the top-left, top-right, bottom-left and bottom-right corner,
	 * each held [HANDLE_RADIUS] inside the window so a handle at its edge is drawn whole (REQ-HUD-15); null with no selection.
	 */
	fun handleCentres(screenW: Double, screenH: Double): DoubleArray? {
		val id = selected ?: return null
		val rect = rect(id, screenW, screenH)
		val out = DoubleArray(8)
		for (corner in 0..3) {
			out[2 * corner] = (if (corner % 2 == 0) rect.x else rect.x + rect.w).coerceIn(HANDLE_RADIUS, screenW - HANDLE_RADIUS)
			out[2 * corner + 1] = (if (corner < 2) rect.y else rect.y + rect.h).coerceIn(HANDLE_RADIUS, screenH - HANDLE_RADIUS)
		}
		return out
	}

	/**
	 * The corner whose handle lies under the point, or -1: the nearest within [HANDLE_REACH], or within a quarter of the
	 * element's smaller side when that is less, so a small element keeps a middle a press moves.
	 */
	fun handleAt(x: Double, y: Double, screenW: Double, screenH: Double): Int {
		val centres = handleCentres(screenW, screenH) ?: return -1
		val rect = rect(selected ?: return -1, screenW, screenH)
		var best = -1
		var nearest = minOf(HANDLE_REACH, minOf(rect.w, rect.h) / 4)
		for (corner in 0..3) {
			val distance = hypot(x - centres[2 * corner], y - centres[2 * corner + 1])
			if (distance <= nearest) {
				best = corner
				nearest = distance
			}
		}
		return best
	}

	/** Whether an element is grabbed: pressed and not yet released (the editor shows the move pointer, REQ-UI-43). */
	fun dragging(): Boolean = dragging

	/** Where element [id] was when the editor opened. */
	fun opened(id: String): HudPosition? = opened[id]

	/** The positions as edited, by id; changed in place by the editor. */
	fun positions(): MutableMap<String, HudPosition> = working

	fun select(id: String?) {
		selected = id
	}

	/** The guide lines to draw while a drag is snapped, on a [screenW]×[screenH] screen; none otherwise. */
	fun guides(screenW: Double, screenH: Double): List<HudSnap.Guide> {
		val id = selected
		if (!dragging || id == null || guides.isEmpty() || screenW != guidesScreenW || screenH != guidesScreenH || rect(id, screenW, screenH) != guidesFor) {
			return java.util.List.of()
		}
		return guides
	}

	/** Where element [id] is drawn: its stored place on whole pixels, inside the window (REQ-HUD-04). */
	fun rect(id: String, screenW: Double, screenH: Double): HudRect {
		val position = working.getValue(id)
		return HudRect.onPixels(
			position.place(screenW, screenH, sizes.width(id) * position.scale, sizes.height(id) * position.scale),
			screenW,
			screenH,
		)
	}

	/** The topmost element under the point (the one registered last, drawn on top: EC-HUD-03), or null. */
	fun at(x: Double, y: Double, screenW: Double, screenH: Double): String? = items.asReversed()
		.firstOrNull { item -> rect(item.id, screenW, screenH).let { x >= it.x && y >= it.y && x < it.x + it.w && y < it.y + it.h } }
		?.id

	/**
	 * A press on one of the selected element's handles starts a scale about the opposite corner (before the element under it,
	 * REQ-HUD-15); otherwise it selects the element under the point and grabs it where it was pressed, or deselects on empty space.
	 */
	fun press(x: Double, y: Double, screenW: Double, screenH: Double) {
		val corner = handleAt(x, y, screenW, screenH)
		val id = selected
		if (corner >= 0 && id != null) {
			val rect = rect(id, screenW, screenH)
			val left = corner % 2 == 0
			val top = corner < 2
			handle = corner
			dragging = false
			moved = false
			guides = java.util.List.of()
			pressX = x
			pressY = y
			cornerX = if (left) rect.x else rect.x + rect.w
			cornerY = if (top) rect.y else rect.y + rect.h
			fixedX = if (left) rect.x + drawn(rect.w) else rect.x
			fixedY = if (top) rect.y + drawn(rect.h) else rect.y
			return
		}
		handle = -1
		val hit = at(x, y, screenW, screenH)
		selected = hit
		dragging = hit != null
		moved = false
		pressX = x
		pressY = y
		snapping = false
		guides = java.util.List.of()
		if (hit != null) {
			val rect = rect(hit, screenW, screenH)
			grabX = x - rect.x
			grabY = y - rect.y
		}
	}

	/**
	 * Moves the grabbed element with the mouse, inside the window; with [snap], its edges and centre snap within 4 px
	 * to the screen's centre lines and edges and to the other elements' edges (REQ-HUD-14; Alt held: no [snap]).
	 */
	@JvmOverloads
	fun drag(x: Double, y: Double, screenW: Double, screenH: Double, snap: Boolean = false) {
		val id = selected
		if (handle >= 0 && id != null) {
			// A handle drag scales and never snaps (REQ-HUD-15).
			scaleFromHandle(id, x, y, screenW, screenH)
			return
		}
		if (!dragging || id == null) {
			return
		}
		val before = working.getValue(id)
		val drawn = rect(id, screenW, screenH)
		var left = x - grabX
		var top = y - grabY
		if (!snapping && maxOf(abs(x - pressX), abs(y - pressY)) >= SNAP_AFTER) {
			snapping = true
		}
		val others = if (snap && snapping) items.filter { it.id != id }.map { rect(it.id, screenW, screenH) } else null
		if (others != null) {
			// Where it would be drawn (inside the window, on whole pixels): the 4 px are measured on what is drawn, and an
			// element pushed against an edge still snaps along it.
			val w = sizes.width(id) * before.scale
			val h = sizes.height(id) * before.scale
			val held = HudRect.onPixels(HudRect(left, top, w, h), screenW, screenH)
			val snapped = HudSnap.snap(held, screenW, screenH, others)
			left = snapped.x
			top = snapped.y
		}
		moveTo(id, left, top, screenW, screenH)
		if (rect(id, screenW, screenH) == drawn) {
			// Not moved on screen: the stored place stays as it was (a clamped one included).
			working[id] = before
		} else {
			moved = true
		}
		// From the place as drawn, so a guide never disagrees with the drawing.
		val drawnNow = rect(id, screenW, screenH)
		guides = if (others != null) HudSnap.guides(drawnNow, screenW, screenH, others) else java.util.List.of()
		guidesFor = drawnNow
		guidesScreenW = screenW
		guidesScreenH = screenH
	}

	/** Ends a drag or a handle drag that changed the element: anchored to the screen third of its centre, where it is (REQ-HUD-03). */
	fun release(screenW: Double, screenH: Double) {
		val id = selected
		if ((dragging || handle >= 0) && moved && id != null) {
			reanchor(id, screenW, screenH)
		}
		dragging = false
		handle = -1
		moved = false
		guides = java.util.List.of()
	}

	/**
	 * REQ-HUD-15: the box from the fixed corner to the dragged one (moved as the mouse moved since the press) over the element's
	 * unscaled size, the larger of the two ratios, on the 0.1 step (halves up) and in 0.5-3.0; never so large that the element
	 * would leave the window from the fixed corner (then the largest step that fits). The fixed corner stays where it is drawn.
	 */
	private fun scaleFromHandle(id: String, x: Double, y: Double, screenW: Double, screenH: Double) {
		val w = sizes.width(id)
		val h = sizes.height(id)
		if (w <= 0 || h <= 0) {
			return
		}
		val left = handle % 2 == 0
		val top = handle < 2
		val toX = cornerX + (x - pressX)
		val toY = cornerY + (y - pressY)
		// In tenths straight from the sizes, so a ratio exactly halfway (69 of 60 px: 11.5 tenths) rounds up, not by float error.
		val tenths = maxOf((if (left) fixedX - toX else toX - fixedX) * 10 / w, (if (top) fixedY - toY else toY - fixedY) * 10 / h)
		var scale = HudScale.clamp(Math.round(tenths) / 10.0)
		while (scale > HudScale.MIN && !fits(w * scale, h * scale, left, top, screenW, screenH)) {
			scale = HudScale.clamp(scale - HudScale.STEP)
		}
		val position = working.getValue(id)
		if (scale == position.scale) {
			return
		}
		working[id] = position.withScale(scale)
		moveTo(id, if (left) fixedX - drawn(w * scale) else fixedX, if (top) fixedY - drawn(h * scale) else fixedY, screenW, screenH)
		moved = true
	}

	/** Whether a [w]×[h] element drawn from the fixed corner towards the dragged one lies inside the window. */
	private fun fits(w: Double, h: Double, left: Boolean, top: Boolean, screenW: Double, screenH: Double): Boolean =
		(if (left) fixedX - drawn(w) >= 0 else fixedX + drawn(w) <= screenW) && (if (top) fixedY - drawn(h) >= 0 else fixedY + drawn(h) <= screenH)

	/** A size as its outline spans it: rounded up, past float error (60 × 1.3 is 78, not 79). */
	private fun drawn(size: Double): Double = ceil(size - 1e-6)

	/** Scales the element under the mouse, else the selected one, in steps of 0.1 (REQ-HUD-05); false if there is none. */
	fun scroll(x: Double, y: Double, notches: Int, screenW: Double, screenH: Double): Boolean {
		val target = at(x, y, screenW, screenH) ?: selected ?: return false
		val position = working.getValue(target)
		working[target] = position.withScale(HudScale.step(position.scale, notches))
		// A new size: the guides of the last snap no longer fit; the next move snaps it anew.
		guides = java.util.List.of()
		return true
	}

	/** Moves the selected element by ([dx], [dy]) pixels, inside the window. */
	fun nudge(dx: Int, dy: Int, screenW: Double, screenH: Double) {
		val id = selected
		if (id == null || dragging || handle >= 0) {
			return
		}
		val rect = rect(id, screenW, screenH)
		moveTo(id, rect.x + dx, rect.y + dy, screenW, screenH)
		reanchor(id, screenW, screenH)
	}

	fun resetSelected() {
		selected?.let { working[it] = item(it).defaultPosition }
	}

	fun resetAll() {
		items.forEach { working[it.id] = it.defaultPosition }
	}

	/** Every element back where it was when the editor opened. */
	fun cancel() {
		dragging = false
		handle = -1
		guides = java.util.List.of()
		working.clear()
		working.putAll(opened)
	}

	private fun moveTo(id: String, left: Double, top: Double, screenW: Double, screenH: Double) {
		val position = working.getValue(id)
		val w = sizes.width(id) * position.scale
		val h = sizes.height(id) * position.scale
		val x = maxOf(0.0, minOf(left, screenW - w))
		val y = maxOf(0.0, minOf(top, screenH - h))
		working[id] = position.movedTo(x, y, screenW, screenH, w, h)
	}

	private fun reanchor(id: String, screenW: Double, screenH: Double) {
		val position = working.getValue(id)
		working[id] = position.reanchored(screenW, screenH, sizes.width(id) * position.scale, sizes.height(id) * position.scale)
	}

	private fun item(id: String): Item = items.firstOrNull { it.id == id } ?: throw IllegalArgumentException(id)

	private companion object {
		/** How far (GUI px) the mouse must move from the press before a drag snaps. */
		const val SNAP_AFTER = 2.0

		/** A handle's radius (a 5 GUI px disc) and how far from its centre a press still takes it, in GUI px (REQ-HUD-15). */
		const val HANDLE_RADIUS = 2.5
		const val HANDLE_REACH = 4.0
	}
}
