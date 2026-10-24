package com.k8bas.skyblockutility.module.bestiary

/** Which families the HUD shows (REQ-BEST-09): the one that rose last, or a pinned list. */
enum class SelectionMode { AUTO, PINNED }

/**
 * The family selection of the Bestiary Tracker (T5.2e, REQ-BEST-09). Auto-follow shows the family whose count rose last
 * (also a rise over 50, which counts no kills, and a changed abbreviated count; a fall is no change); when several rose
 * in the same widget update, the bigger rise wins, then the name, so the choice never depends on the order of the lines
 * or of first sight. Before any rise it shows the family updated last (a menu page, a stored value); after one, a menu
 * page does not take over, as it updates many families at once. A pinned list holds up to 5 families and shows them in
 * the pinned order, also those not seen yet. "Hide maxed" (ON by default, R59) leaves maxed families out of both.
 */
object BestiarySelection {
	/** The most families a pinned list holds. */
	const val MAX_PINS = 5

	/**
	 * [pins] with [key] added at the end, the same pins if it is pinned already, or null when it is refused (the list is
	 * full). The result is unmodifiable; a config keeps a copy.
	 */
	@JvmStatic
	fun pin(pins: List<String>, key: String): List<String>? = when {
		key in pins -> java.util.List.copyOf(pins)
		pins.size >= MAX_PINS -> null
		else -> java.util.List.copyOf(pins + key)
	}

	/** The families to show; a pinned key the table does not know (an old config) is left out. */
	@JvmStatic
	fun select(tracker: BestiaryTracker, mode: SelectionMode, pins: List<String>, hideMaxed: Boolean): List<FamilyView> {
		if (mode == SelectionMode.PINNED) {
			return pins.take(MAX_PINS).mapNotNull { tracker.familyOrUnseen(it) }.filter { !hideMaxed || !it.max }
		}
		val shown = tracker.families().filter { !hideMaxed || !it.max }
		val rose = shown.filter { it.changeOrder > 0 }
		val pick = if (rose.isNotEmpty()) {
			rose.minWith(compareByDescending<FamilyView> { it.changeOrder }.thenByDescending { it.lastRise }.thenBy { it.name }.thenBy { it.key })
		} else {
			shown.minWithOrNull(compareByDescending<FamilyView> { it.updatedAt }.thenBy { it.name }.thenBy { it.key })
		}
		return listOfNotNull(pick)
	}
}
