package com.k8bas.skyblockutility.ui.option;

import java.util.List;
import java.util.Set;

/**
 * A list of known mobs or NPCs a feature adds rules from (REQ-UI-11, "Add from database"). Read on the
 * client thread each time the picker draws, so it follows the fetch and the rules as they change.
 */
public interface RuleDatabase {
	/** Whether the list is still loading, ready, or could not be read (then nothing can be added, EC-UI-08). */
	enum State {
		LOADING, READY, UNAVAILABLE
	}

	/** What the picker shows instead of Add for a mob that is invisible by design (REQ-NPCDB-08, R5). */
	String NEVER_HIGHLIGHTED = "never highlighted (Hypixel rules)";

	/**
	 * One entry: its folder is the island it belongs to, and an optional sub-folder (e.g. an event). An entry with
	 * a note is listed greyed with the note instead of Add and cannot be added (REQ-NPCDB-08).
	 */
	record Entry(String id, String name, String folder, String subfolder, String searchText, String note) {
		public Entry(String id, String name, String folder, String subfolder, String searchText) {
			this(id, name, folder, subfolder, searchText, null);
		}

		public boolean addable() {
			return note == null;
		}
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
