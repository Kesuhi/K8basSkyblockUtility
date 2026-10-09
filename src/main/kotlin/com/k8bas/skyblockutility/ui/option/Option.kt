package com.k8bas.skyblockutility.ui.option

/**
 * One setting, declared once (REQ-UI-07): its text feeds the card and the search index, its binding
 * the value. Later kinds (decimal slider, HUD position) join the sealed family.
 *
 * Declared as functions, not properties, so Java keeps calling `option.id()` and so on (R30).
 */
sealed interface Option {
	/** Unique id, e.g. "npc_search.show_distance"; a search hit names it. */
	fun id(): String

	/** Where the value is stored: a path in the config file ("modules.npc_search.showDistance"), or
	 *  "options.txt:" plus the key mapping's name for a keybind. Never changes without a migration. */
	fun storageKey(): String

	fun text(): OptionText

	/** Rated AMBER in PLAN §3: its tooltip explains the restriction (REQ-UI-19, AC-UI-17). */
	fun amber(): Boolean

	fun status(): OptionStatus

	/** The documented default (REQ-XC-TOGGLE-02). */
	fun defaultValue(): Any

	/** Further texts the search matches, such as a dropdown's entry labels (REQ-UI-08). */
	fun searchLabels(): List<String> = emptyList()
}
