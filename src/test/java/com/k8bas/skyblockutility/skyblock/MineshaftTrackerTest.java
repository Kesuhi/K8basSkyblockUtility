package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mineshaft state (T3.0m, REQ-GS-17, AC-GS-15, AC-GS-17 [A]) on a simulated clock: the shaft code from the
 * sidebar, searched for 15 s after entry; the Frozen Corpses list from the tab widget; everything cleared on
 * leaving, a server switch or a disconnect. Sidebar and tab shapes are the G1 captures' (sidebar-mineshaft.txt,
 * tab-mineshaft.txt); `UNLOOTED` and the Umber and Tungsten types were not captured (UNVERIFIED, from SPEC).
 */
class MineshaftTrackerTest {
	private final List<String> unknown = new ArrayList<>();
	private final MineshaftTracker tracker = new MineshaftTracker(Map.of("OPAL_1", "Opal 1", "UMBE_1", "Umber 1")::get, unknown::add);

	private static TabSnapshot corpses(String... lines) {
		return new TabSnapshot(List.of(new TabWidget("Frozen Corpses", "", List.of(lines), false)), List.of());
	}

	@Test
	void theCodeIsReadFromTheSidebarAfterEntry() {
		tracker.enter(0);
		assertTrue(tracker.snapshot().getSearching());
		assertNull(tracker.snapshot().getName(), "no name while the search runs");
		tracker.sidebar(List.of("10/01/26 m000XX UMBE_1", "Autumn 10th"), 1_000);
		MineshaftSnapshot shaft = tracker.snapshot();
		assertEquals("UMBE_1", shaft.getCode());
		assertEquals("Umber 1", shaft.getName());
		assertFalse(shaft.getSearching());
	}

	@Test
	void aCodeNotInTheTableIsShownRaw() {
		// AC-GS-17: `ABCD_1` gives "ABCD_1".
		tracker.enter(0);
		tracker.sidebar(List.of("09/26/26 m000XX ABCD_1"), 500);
		assertEquals("ABCD_1", tracker.snapshot().getName());
	}

	@Test
	void noCodeWithinFifteenSecondsIsAnUnknownShaft() {
		// AC-GS-17: no code line gives "Unknown shaft" after 15 s.
		tracker.enter(0);
		tracker.sidebar(List.of("10/01/26 m000XX", "Autumn 10th"), 1_000);
		tracker.tick(14_999);
		assertTrue(tracker.snapshot().getSearching());
		tracker.tick(15_000);
		assertEquals("Unknown shaft", tracker.snapshot().getName());
		assertNull(tracker.snapshot().getCode());
		assertFalse(tracker.snapshot().getSearching());
		// The search is over: a code shown later is not taken.
		tracker.sidebar(List.of("10/01/26 m000XX OPAL_1"), 16_000);
		assertEquals("Unknown shaft", tracker.snapshot().getName());
	}

	@Test
	void onlyAWholeCodeCounts() {
		tracker.enter(0);
		tracker.sidebar(List.of("Purse: 311,732,284", "ABCDE_12", "ab_1 opal_1"), 100);
		assertNull(tracker.snapshot().getCode());
		assertTrue(tracker.snapshot().getSearching());
	}

	@Test
	void theCorpseListKeepsItsOrderAndReadsUnlootedAsNotLooted() {
		// AC-GS-17: Umber NOT LOOTED, Tungsten LOOTED, Lapis UNLOOTED give two not looted and one looted.
		tracker.enter(0);
		tracker.tab(corpses("Umber: NOT LOOTED", "Tungsten: LOOTED", "Lapis: UNLOOTED"));
		CorpseList list = tracker.snapshot().getCorpses();
		assertEquals(WidgetPresence.PRESENT, list.getPresence());
		assertEquals(List.of(new Corpse("Umber", false), new Corpse("Tungsten", true), new Corpse("Lapis", false)), list.getCorpses());
		assertEquals(2, list.getCorpses().stream().filter(c -> !c.getLooted()).count());
	}

	@Test
	void theCapturedWidgetReads() {
		tracker.enter(0);
		tracker.tab(TabParser.parse(CaptureFixtures.tabRows("tab-mineshaft.txt")));
		assertEquals(List.of(new Corpse("Lapis", false), new Corpse("Lapis", true), new Corpse("Lapis", true)), tracker.snapshot().getCorpses().getCorpses());
	}

	@Test
	void anUnknownCorpseLineMakesTheListPartialNotWrong() {
		// REQ-GS-10: never a wrong count; the shape is logged.
		tracker.enter(0);
		tracker.tab(corpses("Umber: NOT LOOTED", "Lapis: GONE"));
		CorpseList list = tracker.snapshot().getCorpses();
		assertEquals(WidgetPresence.PARTIAL, list.getPresence());
		assertEquals(List.of(new Corpse("Umber", false)), list.getCorpses());
		assertEquals(List.of("Lapis: GONE"), unknown);
	}

	@Test
	void anAbsentOrEmptyWidgetIsReportedAsSuch() {
		tracker.enter(0);
		tracker.tab(TabSnapshot.EMPTY);
		assertEquals(WidgetPresence.ABSENT, tracker.snapshot().getCorpses().getPresence());
		tracker.tab(new TabSnapshot(List.of(new TabWidget("Frozen Corpses", "", List.of(), false)), List.of()));
		assertEquals(WidgetPresence.EMPTY, tracker.snapshot().getCorpses().getPresence());
		tracker.tab(new TabSnapshot(List.of(new TabWidget("Frozen Corpses", "", List.of("Umber: LOOTED"), true)), List.of()));
		assertEquals(WidgetPresence.PARTIAL, tracker.snapshot().getCorpses().getPresence(), "cut off at the end of the tab list");
	}

	@Test
	void leavingClearsEverything() {
		// AC-GS-15 / AC-GS-17: after a switch to the Dwarven Mines, Frozen Corpses reports absent at once.
		tracker.enter(0);
		tracker.sidebar(List.of("10/01/26 m000XX UMBE_1"), 100);
		tracker.tab(corpses("Umber: NOT LOOTED"));
		tracker.leave();
		MineshaftSnapshot shaft = tracker.snapshot();
		assertFalse(shaft.getInShaft());
		assertNull(shaft.getCode());
		assertEquals(WidgetPresence.ABSENT, shaft.getCorpses().getPresence());
		assertEquals(MineshaftSnapshot.NONE, shaft);
	}

	@Test
	void outsideAShaftNothingIsRead() {
		tracker.sidebar(List.of("10/01/26 m000XX UMBE_1"), 100);
		tracker.tab(corpses("Umber: NOT LOOTED"));
		assertEquals(MineshaftSnapshot.NONE, tracker.snapshot());
	}

	@Test
	void aNewShaftStartsANewSearch() {
		tracker.enter(0);
		tracker.sidebar(List.of("10/01/26 m000XX UMBE_1"), 100);
		tracker.enter(60_000);
		assertTrue(tracker.snapshot().getSearching());
		assertNull(tracker.snapshot().getCode());
		tracker.sidebar(List.of("10/01/26 m000XX OPAL_1"), 61_000);
		assertEquals("Opal 1", tracker.snapshot().getName());
	}

	@Test
	void aSimulatedShaftSetsCodeAndCorpses() {
		// The dev-only `/ksu debug shaft <code> [Type:STATE...]` (T3.0m) for the T3.2 and T3.9d gametests.
		tracker.simulate("OPAL_1", List.of("Lapis:NOT_LOOTED", "Umber:LOOTED"), 0);
		MineshaftSnapshot shaft = tracker.snapshot();
		assertTrue(shaft.getInShaft());
		assertEquals("Opal 1", shaft.getName());
		assertEquals(List.of(new Corpse("Lapis", false), new Corpse("Umber", true)), shaft.getCorpses().getCorpses());
		// A real tab or sidebar does not overwrite the simulation until it is cleared.
		tracker.tab(TabSnapshot.EMPTY);
		assertEquals(2, tracker.snapshot().getCorpses().getCorpses().size());
		tracker.leave();
		assertEquals(MineshaftSnapshot.NONE, tracker.snapshot());
	}
}
