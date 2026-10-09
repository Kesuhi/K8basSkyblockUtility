package com.k8bas.skyblockutility.module.npcsearch

import java.util.Locale
import java.util.function.BooleanSupplier

/**
 * When the "You found <label>" title may show (REQ-GLOW-14): the rule is for one of the special
 * NPCs (R20), the toggle is on, the entity is visible, the player has line of sight to it, and the
 * rule has not shown its title on this server yet. reset() runs on every location change, so each
 * run (a new server) shows it again. Client thread only. Internal (was package-private); members public.
 */
internal class FoundTitleGate {
	private val shown = HashSet<String?>()

	/**
	 * @param lineOfSight asked last, and only when everything else allows the title: it casts a
	 *                    ray through the world (a Java BooleanSupplier, so nothing extra is made per match)
	 */
	fun allow(ruleId: String?, enabled: Boolean, invisible: Boolean, lineOfSight: BooleanSupplier): Boolean {
		if (!enabled || invisible || shown.contains(ruleId) || !lineOfSight.asBoolean) {
			return false
		}
		shown.add(ruleId)
		return true
	}

	fun reset() {
		shown.clear()
	}

	companion object {
		/**
		 * NPC database ids of the NPCs that get the title (R20): rare-room NPCs of the Catacombs and
		 * the Crystal Hollows.
		 */
		@JvmField
		val SPECIAL: Set<String> = java.util.Set.of("trinity", "tomioka", "duncan", "xalx", "pete")

		/** A rule from the NPC database counts by its id; a hand-made rule (no id) by its label. */
		@JvmStatic
		fun isSpecial(sourceId: String?, label: String?): Boolean {
			// Java's String.isBlank and strip: Character.isWhitespace only.
			if (sourceId != null && !sourceId.all(Character::isWhitespace)) {
				return SPECIAL.contains(sourceId.lowercase(Locale.ROOT))
			}
			return label != null && SPECIAL.contains(label.trim(Character::isWhitespace).lowercase(Locale.ROOT))
		}
	}
}
