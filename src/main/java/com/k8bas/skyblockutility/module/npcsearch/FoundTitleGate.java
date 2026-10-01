package com.k8bas.skyblockutility.module.npcsearch;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * When the "You found <label>" title may show (REQ-GLOW-14): the rule is for one of the special
 * NPCs (R20), the toggle is on, the entity is visible, the player has line of sight to it, and the
 * rule has not shown its title on this server yet. reset() runs on every location change, so each
 * run (a new server) shows it again. Client thread only.
 */
final class FoundTitleGate {
	/** NPC database ids of the NPCs that get the title (R20): rare-room NPCs of the Catacombs and
	 *  the Crystal Hollows. */
	static final Set<String> SPECIAL = Set.of("trinity", "tomioka", "duncan", "xalx", "pete");

	private final Set<String> shown = new HashSet<>();

	/** A rule from the NPC database counts by its id; a hand-made rule (no id) by its label. */
	static boolean isSpecial(String sourceId, String label) {
		if (sourceId != null && !sourceId.isBlank()) {
			return SPECIAL.contains(sourceId.toLowerCase(Locale.ROOT));
		}
		return label != null && SPECIAL.contains(label.strip().toLowerCase(Locale.ROOT));
	}

	/** @param lineOfSight asked last, and only when everything else allows the title: it casts a
	 *                    ray through the world */
	boolean allow(String ruleId, boolean enabled, boolean invisible, BooleanSupplier lineOfSight) {
		if (!enabled || invisible || shown.contains(ruleId) || !lineOfSight.getAsBoolean()) {
			return false;
		}
		shown.add(ruleId);
		return true;
	}

	void reset() {
		shown.clear();
	}
}
