package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T2.5c: a module's fetched list as the picker's database. */
class ListDatabaseTest {
	private record Mob(String id, String name, String island) {
	}

	@Test
	void entriesAreMappedOncePerListAndAddHandsBackTheOriginal() {
		List<Mob> fetched = List.of(new Mob("zealot", "Zealot", "The End"), new Mob("goblin", "Golden Goblin", "Dwarven Mines"));
		List<List<Mob>> source = new ArrayList<>(List.of(List.of()));
		List<Mob> added = new ArrayList<>();
		RuleDatabase.State[] state = {RuleDatabase.State.LOADING};
		ListDatabase<Mob> database = new ListDatabase<>("Mob Database", () -> state[0], () -> source.get(0),
				mob -> new RuleDatabase.Entry(mob.id(), mob.name(), mob.island(), null, mob.name()), () -> Set.of("goblin"), added::add);
		assertEquals(RuleDatabase.State.LOADING, database.state());
		assertTrue(database.entries().isEmpty(), "nothing while loading");
		source.set(0, fetched);
		state[0] = RuleDatabase.State.READY;
		List<RuleDatabase.Entry> entries = database.entries();
		assertEquals(List.of("Zealot", "Golden Goblin"), entries.stream().map(RuleDatabase.Entry::name).toList());
		assertSame(entries, database.entries(), "mapped once for the same list");
		assertEquals(Set.of("goblin"), database.used());
		database.add(entries.get(0));
		assertEquals(List.of(fetched.get(0)), added, "Add hands the module its own entry");
		database.add(new RuleDatabase.Entry("unknown", "Gone", "Hub", null, ""));
		assertEquals(1, added.size(), "an entry no longer in the list adds nothing");
		database.add(entries.get(1));
		assertEquals(1, added.size(), "an entry that already backs a rule adds nothing (a second click before the list refreshed)");
	}
}
