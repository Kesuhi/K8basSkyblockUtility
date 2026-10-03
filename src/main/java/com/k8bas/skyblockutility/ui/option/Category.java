package com.k8bas.skyblockutility.ui.option;

/** The settings screen's categories, in screen order (REQ-UI-04). A category without a card is hidden. */
public enum Category {
	GENERAL("general", "General"),
	HIGHLIGHTS("highlights", "Highlights"),
	WAYPOINTS("waypoints", "Waypoints"),
	MINING("mining", "Mining"),
	FISHING("fishing", "Fishing"),
	ODDS_TRACKERS("odds_trackers", "Odds & Trackers"),
	SKYBLOCK_XP("skyblock_xp", "SkyBlock XP");

	private final String id;
	private final String displayName;

	Category(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}
}
