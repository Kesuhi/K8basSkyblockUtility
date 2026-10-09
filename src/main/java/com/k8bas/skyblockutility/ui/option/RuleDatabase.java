package com.k8bas.skyblockutility.ui.option;

import java.util.List;
import java.util.Set;

/**
 * A list of known mobs or NPCs a feature adds rules from (REQ-UI-11, "Add from database"). Read on the
 * client thread each time the picker draws, so it follows the fetch and the rules as they change.
 */
public interface RuleDatabase {
	/** Whether the list is still loading, ready, or could not be fetched (then nothing can be added, EC-UI-08). */
	enum State {
		LOADING, READY, UNAVAILABLE
	}

	/** One entry: its folder is the island it belongs to, and an optional sub-folder (e.g. an event). */
	record Entry(String id, String name, String folder, String subfolder, String searchText) {
	}

	/** The picker's title, e.g. "Mob Database". */
	String title();

	State state();

	List<Entry> entries();

	/** The ids of the entries a rule already comes from (the picker hides them). */
	Set<String> used();

	/** Adds a rule for the entry; the screen writes it at once (a discrete commit, REQ-UI-15). */
	void add(Entry entry);
}
