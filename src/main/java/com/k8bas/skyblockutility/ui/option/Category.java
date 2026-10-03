package com.k8bas.skyblockutility.ui.option;

/** The settings screen's categories, in screen order (REQ-UI-04). A category without a card is hidden. */
public enum Category {
	GENERAL("general", "General", "Look of this screen, notices, keybinds and updates."),
	HIGHLIGHTS("highlights", "Highlights", "Outlines for the mobs you choose, seen only where they are in view."),
	WAYPOINTS("waypoints", "Waypoints", "Find NPCs: waypoints at fixed spots, outlines on NPCs by name, and the \"You found\" title."),
	MINING("mining", "Mining", "Alerts and fixes for mining."),
	FISHING("fishing", "Fishing", "Fixes and helpers for fishing."),
	ODDS_TRACKERS("odds_trackers", "Odds & Trackers", "Drop odds and progress trackers."),
	SKYBLOCK_XP("skyblock_xp", "SkyBlock XP", "Settings for the SkyBlock XP optimizer.");

	private final String id;
	private final String displayName;
	private final String description;

	Category(String id, String displayName, String description) {
		this.id = id;
		this.displayName = displayName;
		this.description = description;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	/** Shown under the category's name at the top of its page. */
	public String description() {
		return description;
	}
}
