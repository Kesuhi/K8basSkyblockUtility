package com.k8bas.skyblockutility.module.npcsearch;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * When the "You found <label>" title may show (REQ-GLOW-14): the toggle is on, the entity is
 * visible, the player has line of sight to it, and the rule has not shown its title on this
 * server yet. reset() runs on every location change, so each run (a new server) shows it again.
 * Client thread only.
 */
final class FoundTitleGate {
	private final Set<String> shown = new HashSet<>();

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
