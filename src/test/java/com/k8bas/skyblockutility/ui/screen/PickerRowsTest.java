package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-11 (T2.5c): the database picker's rows: island folders, search, entries already used hidden. */
class PickerRowsTest {
	private static final List<RuleDatabase.Entry> ENTRIES = List.of(
			new RuleDatabase.Entry("zealot", "Zealot", "The End", null, "Zealot"),
			new RuleDatabase.Entry("special_zealot", "Special Zealot", "The End", null, "Special Zealot"),
			new RuleDatabase.Entry("trick_or_treater", "Trick or Treater", "Hub", "Spooky Festival", "Trick or Treater"),
			new RuleDatabase.Entry("golden_goblin", "Golden Goblin", "Dwarven Mines", null, "Golden Goblin"));

	private static List<String> labels(List<PickerRows.Row> rows) {
		return rows.stream().map(row -> "  ".repeat(row.depth()) + row.label() + (row.entry() == null ? " (" + row.count() + ")" : "")).toList();
	}

	@Test
	void foldersAreClosedUntilOpened() {
		assertEquals(List.of("Dwarven Mines (1)", "Hub (1)", "The End (2)"), labels(PickerRows.rows(ENTRIES, Set.of(), "", Set.of())));
		assertEquals(List.of("Dwarven Mines (1)", "Hub (1)", "The End (2)", "  Special Zealot", "  Zealot"),
				labels(PickerRows.rows(ENTRIES, Set.of(), "", Set.of("The End"))), "open: its entries, by name");
		assertEquals(List.of("Dwarven Mines (1)", "Hub (1)", "  Spooky Festival (1)", "    Trick or Treater", "The End (2)"),
				labels(PickerRows.rows(ENTRIES, Set.of(), "", Set.of("Hub", "Hub/Spooky Festival"))), "a sub-folder inside its island");
	}

	@Test
	void aSearchShowsTheMatchesWithTheirFoldersOpen() {
		assertEquals(List.of("The End (2)", "  Special Zealot", "  Zealot"), labels(PickerRows.rows(ENTRIES, Set.of(), "  ZEAL ", Set.of())));
		assertEquals(List.of("Hub (1)", "  Spooky Festival (1)", "    Trick or Treater"), labels(PickerRows.rows(ENTRIES, Set.of(), "spooky", Set.of())),
				"the sub-folder's name matches too");
		assertTrue(PickerRows.rows(ENTRIES, Set.of(), "zzzz", Set.of()).isEmpty());
	}

	/** An entry that already backs a rule is hidden; a folder left empty goes too. */
	@Test
	void usedEntriesAreHidden() {
		assertEquals(List.of("Hub (1)", "The End (1)", "  Special Zealot"),
				labels(PickerRows.rows(ENTRIES, Set.of("zealot", "golden_goblin"), "", Set.of("The End"))));
	}

	/** A folder closed during a search closes visibly, and the folders opened by hand are left as they were. */
	@Test
	void aFolderClosedInASearchClosesThere() {
		assertEquals(List.of("The End (2)"), labels(PickerRows.rows(ENTRIES, Set.of(), "zeal", Set.of(), Set.of("The End"))));
		assertEquals(List.of("Dwarven Mines (1)", "Hub (1)", "The End (2)"), labels(PickerRows.rows(ENTRIES, Set.of(), "", Set.of(), Set.of("The End"))),
				"without a query only the folders opened by hand count");
	}

	@Test
	void folderKeysTellIslandsAndSubFoldersApart() {
		List<PickerRows.Row> rows = PickerRows.rows(ENTRIES, Set.of(), "", Set.of("Hub"));
		assertEquals("Hub", rows.get(1).folderKey());
		assertEquals("Hub/Spooky Festival", rows.get(2).folderKey());
		assertTrue(rows.get(1).open() && !rows.get(2).open());
	}
}
