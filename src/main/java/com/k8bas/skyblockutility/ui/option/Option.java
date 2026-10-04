package com.k8bas.skyblockutility.ui.option;

import java.util.List;

/**
 * One setting, declared once (REQ-UI-07): its text feeds the card and the search index, its binding
 * the value. Later kinds (decimal slider, HUD position) join the permits list.
 */
public sealed interface Option permits Toggle, IntSlider, Choice, Keybind, ColorOption, TextOption, ActionOption, InfoOption, DatabaseOption {
	/** Unique id, e.g. "npc_search.show_distance"; a search hit names it. */
	String id();

	/** Where the value is stored: a path in the config file ("modules.npc_search.showDistance"), or
	 *  "options.txt:" plus the key mapping's name for a keybind. Never changes without a migration. */
	String storageKey();

	OptionText text();

	/** Rated AMBER in PLAN §3: its tooltip explains the restriction (REQ-UI-19, AC-UI-17). */
	boolean amber();

	OptionStatus status();

	/** The documented default (REQ-XC-TOGGLE-02). */
	Object defaultValue();

	/** Further texts the search matches, such as a dropdown's entry labels (REQ-UI-08). */
	default List<String> searchLabels() {
		return List.of();
	}
}
