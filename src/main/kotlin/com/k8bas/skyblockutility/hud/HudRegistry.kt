package com.k8bas.skyblockutility.hud

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The HUD elements, in the order they are drawn (REQ-HUD-01). Shipped features register theirs at
 * startup; test elements live only in the gametest source set (REQ-HUD-10).
 */
object HudRegistry {
	private val ELEMENTS = CopyOnWriteArrayList<HudElement>()

	@JvmStatic
	fun register(element: HudElement) {
		require(ELEMENTS.none { it.id() == element.id() }) { "HUD element id already registered: " + element.id() }
		ELEMENTS.add(element)
	}

	/** Removes an element again (a gametest's own). */
	@JvmStatic
	fun unregister(id: String) {
		ELEMENTS.removeIf { it.id() == id }
	}

	@JvmStatic
	fun elements(): List<HudElement> = java.util.List.copyOf(ELEMENTS)
}
