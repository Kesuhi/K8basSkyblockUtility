package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.skyblock.SbNumber;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * T5.2d, per-profile last-known values [A]: AC-BEST-13 (after a relogin the last values show with their source and age; a
 * newer live value replaces them; profile B never shows profile A's) and AC-BEST-09's last bullet (the stored value stays
 * the last exact count), REQ-BEST-14, REQ-BEST-17.
 */
class BestiaryStoreTest {
	private static final String DWARVEN = "Dwarven Mines";

	private final List<String> log = new ArrayList<>();

	private BestiaryTracker tracker() {
		return new BestiaryTracker(BestiaryTables.shipped(), log::add);
	}

	private static void widget(BestiaryTracker tracker, String line, long now) {
		tracker.widget(List.of(BestiaryWidget.parseLine(line)), DWARVEN, now);
	}

	/** Profile A's values: Ghost from the widget at 1,000, Glacite Walker from a menu at 2,000, the milestone at 3,000. */
	private BestiaryTracker profileA() {
		BestiaryTracker a = tracker();
		a.profile("account", "apple", "1111");
		widget(a, "Ghost 15: 12,449/12,500", 1_000);
		a.menu(new BestiaryPage("Bestiary", DWARVEN, List.of(new MenuFamily("Glacite Walker", 9, new SbNumber(2_346, false), 34.6, null, false)),
				new MenuMilestone(351, 2, 3_518)), 2_000);
		return a;
	}

	@Test
	void afterARelogInTheLastValuesShowWithTheirAge() {
		String stored = profileA().snapshot();
		BestiaryTracker again = tracker();
		again.profile("account", "apple", "1111");
		again.restore(stored);
		FamilyView ghost = again.family("ghost");
		assertNotNull(ghost);
		assertEquals(new SbNumber(12_449, false), ghost.kills());
		assertEquals(15, ghost.tier());
		assertEquals(BestiarySource.WIDGET, ghost.killsSource());
		assertEquals(1_000L, ghost.killsUpdatedAt());
		FamilyView walker = again.family("glacite_walker");
		assertNotNull(walker);
		assertEquals(BestiarySource.MENU, walker.source());
		assertEquals(2_000, walker.updatedAt());
		assertEquals(new MilestoneView(351, 2, BestiarySource.MENU, 2_000), again.milestone());
		// A newer live value replaces the stored one; the stored count is no baseline, so nothing is counted from it.
		widget(again, "Ghost 15: 12,460/12,500", 5_000);
		assertEquals(new SbNumber(12_460, false), again.family("ghost").kills());
		assertEquals(5_000, again.family("ghost").updatedAt());
		assertEquals(0, again.family("ghost").sessionKills());
	}

	@Test
	void theStoredCountIsTheLastExactOne() {
		// AC-BEST-09: 12,449, then "12.4k": the store keeps 12,449 and its time.
		BestiaryTracker a = tracker();
		widget(a, "Ghost 15: 12,449/12,500", 1_000);
		widget(a, "Ghost 15: 12.4k/12,500", 2_000);
		BestiaryTracker again = tracker();
		again.restore(a.snapshot());
		FamilyView ghost = again.family("ghost");
		assertEquals(new SbNumber(12_449, false), ghost.kills());
		assertEquals(1_000L, ghost.killsUpdatedAt());
		// Nothing exact at all: no count is stored.
		BestiaryTracker abbreviated = tracker();
		widget(abbreviated, "Ghost 15: 12.4k/12,500", 1_000);
		BestiaryTracker fresh = tracker();
		fresh.restore(abbreviated.snapshot());
		assertNull(fresh.family("ghost") == null ? null : fresh.family("ghost").kills());
	}

	@Test
	void anotherProfileNeverShowsTheFirstOnesValues() {
		BestiaryTracker tracker = profileA();
		String storedForA = tracker.snapshot();
		tracker.profile("account", "banana", null);
		assertEquals(List.of(), tracker.families());
		assertNull(tracker.milestone());
		// Banana's own store is empty: nothing of apple's shows.
		tracker.restore(null);
		assertEquals(List.of(), tracker.families());
		assertTrue(storedForA.contains("12449"), storedForA);
	}

	@Test
	void aNewerValueAlreadyShownStays() {
		String stored = profileA().snapshot();
		BestiaryTracker live = tracker();
		widget(live, "Ghost 15: 12,470/12,500", 10_000);
		live.restore(stored);
		assertEquals(new SbNumber(12_470, false), live.family("ghost").kills());
		assertEquals(BestiarySource.WIDGET, live.family("ghost").source());
		// Families the live state has not seen come from the store.
		assertNotNull(live.family("glacite_walker"));
	}

	@Test
	void anUnknownNameIsStoredByItsName() {
		BestiaryTracker a = tracker();
		widget(a, "Nope Beast 3: 10/20", 1_000);
		BestiaryTracker again = tracker();
		again.restore(a.snapshot());
		FamilyView beast = again.family("name:nope beast");
		assertNotNull(beast);
		assertEquals("Nope Beast", beast.name());
		assertNull(beast.family());
		assertEquals(new SbNumber(10, false), beast.kills());
	}

	@Test
	void anUnreadableStoreGivesNothing() {
		BestiaryTracker tracker = tracker();
		// Files of the wrong shape, also valid JSON, never throw: the tick that reads them would end the game.
		for (String bad : new String[]{null, "", "not json", "{\"version\": 99}", "[]", "{\"version\": \"x\"}", "{\"version\": 1, \"families\": {}}",
				"{\"version\": 1, \"families\": null}", "{\"version\": 1, \"milestone\": []}"}) {
			assertFalse(tracker.restore(bad), "unreadable: " + bad);
		}
		assertTrue(tracker.restore(null) == false && tracker.restore("{\"version\": 1, \"families\": []}"), "an empty store is readable");
		// A bad row is left out, whole: no half-read family; a key the table lost is left out too.
		tracker.restore("{\"version\": 1, \"families\": [{\"key\": \"ghost\", \"name\": \"Ghost\", \"kills\": \"many\", \"at\": 5},"
				+ " {\"key\": \"no_such_family\", \"name\": \"Gone\", \"tier\": 3, \"source\": \"MENU\", \"at\": 5},"
				+ " {\"key\": \"bal\", \"name\": \"Old Name\", \"tier\": 4, \"source\": \"MENU\", \"at\": 5}]}");
		assertEquals(List.of("bal"), tracker.families().stream().map(FamilyView::key).toList());
		assertEquals("Bal", tracker.family("bal").name(), "the table's name, not the stored one");
		assertNull(tracker.milestone());
	}

	@Test
	void theKillsTheTierAndTheMilestoneMergeEachByItsOwnTime() {
		// Stored: Ghost kills 12,449 from the widget at 1,000, tier 15; the menu milestone at 2,000.
		String stored = profileA().snapshot();
		BestiaryTracker live = tracker();
		// Live: a chat tier-up at 1,500 (newer than the stored tier, older than nothing stored for kills).
		live.chat(new BestiaryChatEvent.TierUp("Ghost", 15, 16), DWARVEN, 1_500);
		live.chat(new BestiaryChatEvent.Milestone(360, 361, false), DWARVEN, 3_000);
		live.restore(stored);
		FamilyView ghost = live.family("ghost");
		assertEquals(16, ghost.tier(), "the newer chat tier stays");
		assertEquals(BestiarySource.CHAT, ghost.source());
		assertEquals(1_500, ghost.updatedAt());
		assertEquals(new SbNumber(12_449, false), ghost.kills(), "the stored kills fill in");
		assertEquals(BestiarySource.WIDGET, ghost.killsSource());
		assertEquals(1_000L, ghost.killsUpdatedAt());
		assertEquals(361, live.milestone().milestone(), "the newer milestone stays");
	}

	@Test
	void theStoredExactCountSurvivesANewerAbbreviatedOne() {
		// AC-BEST-09 across sessions: the file has 12,449; after the relogin the widget shows only "12.4k".
		BestiaryTracker a = tracker();
		widget(a, "Ghost 15: 12,449/12,500", 1_000);
		String stored = a.snapshot();
		BestiaryTracker again = tracker();
		widget(again, "Ghost 15: 12.4k/12,500", 5_000);
		again.restore(stored);
		assertTrue(again.family("ghost").kills().getApproximate(), "the newer value shows");
		assertTrue(again.snapshot().contains("12449"), "but the file keeps the last exact count: " + again.snapshot());
	}

	@Test
	void aMaxAndTheWidgetsThresholdAreStored() {
		// EC-BEST-09: past the table's last tier the widget shows progress; an unknown family's MAX and threshold.
		BestiaryTracker a = tracker();
		a.widget(List.of(BestiaryWidget.parseLine("Oasis Rabbit 8: 130/200")), null, 1_000);
		widget(a, "Nope Beast 3: 10/20", 1_000);
		widget(a, "Nope Other 5: MAX", 1_000);
		BestiaryTracker again = tracker();
		again.restore(a.snapshot());
		FamilyView rabbit = again.family("oasis_rabbit");
		assertFalse(rabbit.max(), "no false MAX after a relogin");
		assertEquals(200, rabbit.nextThreshold());
		assertEquals(20, again.family("name:nope beast").nextThreshold());
		assertTrue(again.family("name:nope other").max());
	}

	@Test
	void theStoreChangesWhenTheValuesDo() {
		BestiaryTracker tracker = tracker();
		long before = tracker.changes();
		widget(tracker, "Ghost 15: 12,449/12,500", 1_000);
		long after = tracker.changes();
		assertNotEquals(before, after);
		widget(tracker, "Ghost 15: 12,449/12,500", 2_000);
		assertEquals(after, tracker.changes(), "an unchanged widget line changes nothing");
	}
}
