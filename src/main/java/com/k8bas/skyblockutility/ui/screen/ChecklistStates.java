package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Checklist;

import java.util.HashSet;
import java.util.Set;

/**
 * Which checklists are open and how far (REQ-UI-61): a click opens or closes one, eased as a rule card is (REQ-UI-52); while a
 * search runs, a list whose item labels hold the query shows open at once. A click during a search counts only until the query
 * changes, as for rules (EC-UI-32): when the search ends each list is as it was before it.
 */
final class ChecklistStates {
	private final RuleMotion motion;
	/** Opened by a click outside a search, by id; kept while the screen is open. */
	private final Set<String> open = new HashSet<>();
	/** Opened, or closed, by a click during the search, until the query changes. */
	private final Set<String> openedInSearch = new HashSet<>();
	private final Set<String> closedInSearch = new HashSet<>();

	ChecklistStates(RuleMotion motion) {
		this.motion = motion;
	}

	/** Whether the list shows open, with {@code query} as the search sees it ("" for none) while {@code searching}. */
	boolean expanded(Checklist list, boolean searching, String query) {
		if (!searching) {
			return open.contains(list.id());
		}
		return !closedInSearch.contains(list.id()) && (open.contains(list.id()) || openedInSearch.contains(list.id()) || list.matches(query));
	}

	/** How far the list is open now, 0 to 1; a state no click set is reached at once. */
	float reveal(Checklist list, boolean searching, String query) {
		return motion.reveal(list.id(), expanded(list, searching, query));
	}

	/** A click on its button: it eases open or closed from where it is. */
	void toggle(Checklist list, boolean searching, String query) {
		boolean was = expanded(list, searching, query);
		String id = list.id();
		if (!searching) {
			if (was) {
				open.remove(id);
			} else {
				open.add(id);
			}
		} else if (was) {
			openedInSearch.remove(id);
			closedInSearch.add(id);
		} else {
			closedInSearch.remove(id);
			openedInSearch.add(id);
		}
		motion.toggled(list.id(), !was);
	}

	/** The query changed (or the search ended): the clicks made during the search no longer count. */
	void queryChanged() {
		openedInSearch.clear();
		closedInSearch.clear();
	}
}
