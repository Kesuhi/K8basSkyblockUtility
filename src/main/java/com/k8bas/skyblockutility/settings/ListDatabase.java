package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.ui.option.RuleDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link RuleDatabase} over a module's fetched list (the mob or NPC database): its entries are turned
 * into picker entries once per list, and Add hands the original entry to the module, which makes the
 * rule as the 1.0.1 picker did.
 *
 * @param <T> the module's entry type
 */
public final class ListDatabase<T> implements RuleDatabase {
	private final String title;
	private final Supplier<State> state;
	private final Supplier<List<T>> source;
	private final Function<T, Entry> map;
	private final Supplier<Set<String>> used;
	private final Consumer<T> add;
	private List<T> mappedFrom;
	private List<Entry> entries = List.of();
	private Map<String, T> byId = Map.of();

	/**
	 * @param state  the fetch's state (asking for it also starts the fetch, once)
	 * @param map    an entry as the picker shows it; its id is the entry's id
	 * @param used   the ids the module's rules already come from
	 * @param add    makes and stores a rule for the entry, and applies it
	 */
	public ListDatabase(String title, Supplier<State> state, Supplier<List<T>> source, Function<T, Entry> map, Supplier<Set<String>> used,
			Consumer<T> add) {
		this.title = title;
		this.state = state;
		this.source = source;
		this.map = map;
		this.used = used;
		this.add = add;
	}

	@Override
	public String title() {
		return title;
	}

	@Override
	public State state() {
		return state.get();
	}

	@Override
	public List<Entry> entries() {
		List<T> now = source.get();
		if (now != mappedFrom) {
			mappedFrom = now;
			Map<String, T> ids = new HashMap<>();
			entries = now.stream().map(item -> {
				Entry entry = map.apply(item);
				ids.putIfAbsent(entry.id(), item);
				return entry;
			}).toList();
			byId = Map.copyOf(ids);
		}
		return entries;
	}

	@Override
	public Set<String> used() {
		return used.get();
	}

	@Override
	public void add(Entry entry) {
		// A second click that came before the picker refreshed: the entry already backs a rule.
		if (used.get().contains(entry.id())) {
			return;
		}
		entries();
		T item = byId.get(entry.id());
		if (item != null) {
			add.accept(item);
		}
	}
}
