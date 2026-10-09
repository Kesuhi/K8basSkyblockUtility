package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.config.Normalizable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NpcSearchConfig implements Normalizable {
	public boolean enabled = true;
	/** The "You found <label>" title (REQ-GLOW-14); default ON (D-6). */
	public boolean foundTitleEnabled = true;
	/** "Show beacon beams" and "Show distance" for the fixed waypoints (REQ-NPCWP-08); default ON. */
	public boolean showBeams = true;
	public boolean showDistance = true;
	/** Island colours the player changed; an island missing here uses its default (WaypointColors). */
	public Map<String, Integer> islandBeamColors = new LinkedHashMap<>();
	public List<NpcRule> rules = new ArrayList<>();

	@Override
	public boolean normalize() {
		boolean changed = false;
		if (rules == null) {
			rules = new ArrayList<>();
			changed = true;
		}
		changed |= rules.removeIf(Objects::isNull);
		if (islandBeamColors == null) {
			islandBeamColors = new LinkedHashMap<>();
			changed = true;
		}
		changed |= islandBeamColors.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
		for (NpcRule rule : rules) {
			changed |= rule.normalize();
		}
		return changed;
	}
}
