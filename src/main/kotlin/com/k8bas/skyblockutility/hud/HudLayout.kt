package com.k8bas.skyblockutility.hud

/** The default layout check (REQ-HUD-13): which elements' default rectangles, with preview content, overlap. Pure. */
object HudLayout {
	/** "a and b" for each overlapping pair, on a [screenW]×[screenH] GUI. */
	@JvmStatic
	fun overlaps(elements: List<HudElement>, text: TextMeasure, screenW: Double, screenH: Double): List<String> {
		val rects = elements.map { element ->
			val preview = element.preview() ?: return@map null
			val position = element.defaultPosition()
			HudRect.onPixels(
				position.place(screenW, screenH, preview.width(text) * position.scale, preview.height(text) * position.scale),
				screenW,
				screenH,
			)
		}
		val found = ArrayList<String>()
		for (i in rects.indices) {
			for (j in i + 1 until rects.size) {
				val a = rects[i] ?: continue
				val b = rects[j] ?: continue
				if (intersect(a, b)) {
					found.add(elements[i].id() + " and " + elements[j].id())
				}
			}
		}
		return found
	}

	/** Whether they share any area; edges that only touch do not. */
	@JvmStatic
	fun intersect(a: HudRect, b: HudRect): Boolean = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
}
