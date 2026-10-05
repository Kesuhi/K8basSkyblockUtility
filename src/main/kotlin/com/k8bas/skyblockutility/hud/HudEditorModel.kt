package com.k8bas.skyblockutility.hud

import kotlin.math.abs

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

	fun items(): List<Item> = items

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

	/** Selects the element under the point and grabs it where it was pressed, or deselects on empty space. */
	fun press(x: Double, y: Double, screenW: Double, screenH: Double) {
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

	/** Ends a drag that moved the element: anchored to the screen third of its centre, where it is (REQ-HUD-03). */
	fun release(screenW: Double, screenH: Double) {
		val id = selected
		if (dragging && moved && id != null) {
			reanchor(id, screenW, screenH)
		}
		dragging = false
		moved = false
		guides = java.util.List.of()
	}

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
		if (id == null || dragging) {
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
	}
}
