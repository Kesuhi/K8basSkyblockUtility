package com.k8bas.skyblockutility.settings

import com.k8bas.skyblockutility.ui.option.RuleDatabase
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * A [RuleDatabase] over a module's fetched list (the mob or NPC database): its entries are turned
 * into picker entries once per list, and Add hands the original entry to the module, which makes the
 * rule as the 1.0.1 picker did.
 *
 * @param T     the module's entry type
 * @param state the fetch's state (asking for it also starts the fetch, once)
 * @param map   an entry as the picker shows it; its id is the entry's id
 * @param used  the ids the module's rules already come from
 * @param add   makes and stores a rule for the entry, and applies it
 */
class ListDatabase<T : Any>(
	private val title: String,
	private val state: Supplier<RuleDatabase.State>,
	private val source: Supplier<List<T>>,
	private val map: Function<T, RuleDatabase.Entry>,
	private val used: Supplier<Set<String>>,
	private val add: Consumer<T>,
) : RuleDatabase {
	/** The list the entries were last mapped from; a new list (a fetch, a test) maps them again. */
	private var mappedFrom: List<T>? = null
	private var entries: List<RuleDatabase.Entry> = java.util.List.of()
	private var byId: Map<String, T> = java.util.Map.of()

	override fun title(): String = title

	override fun state(): RuleDatabase.State = state.get()

	override fun entries(): List<RuleDatabase.Entry> {
		val now = source.get()
		if (now !== mappedFrom) {
			mappedFrom = now
			val ids = HashMap<String, T>()
			entries = now.stream().map { item -> map.apply(item).also { ids.putIfAbsent(it.id(), item) } }.toList()
			byId = java.util.Map.copyOf(ids)
		}
		return entries
	}

	override fun used(): Set<String> = used.get()

	override fun add(entry: RuleDatabase.Entry) {
		// A second click that came before the picker refreshed: the entry already backs a rule.
		if (used.get().contains(entry.id())) {
			return
		}
		entries()
		byId[entry.id()]?.let(add::accept)
	}
}
