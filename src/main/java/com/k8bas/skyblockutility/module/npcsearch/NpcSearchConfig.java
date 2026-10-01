package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.config.Normalizable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NpcSearchConfig implements Normalizable {
	public boolean enabled = true;
	/** The "You found <label>" title (REQ-GLOW-14); default ON (D-6). */
	public boolean foundTitleEnabled = true;
	/** Waypoint labels and their distance line in white; off draws them in the NPC's rule colour
	 *  (R21). Default ON. */
	public boolean whiteWaypointLabels = true;
	public List<NpcRule> rules = new ArrayList<>();

	@Override
	public boolean normalize() {
		boolean changed = false;
		if (rules == null) {
			rules = new ArrayList<>();
			changed = true;
		}
		changed |= rules.removeIf(Objects::isNull);
		for (NpcRule rule : rules) {
			changed |= rule.normalize();
		}
		return changed;
	}
}
