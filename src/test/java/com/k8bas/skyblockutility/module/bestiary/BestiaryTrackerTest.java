package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.module.bestiary.BestiaryChatEvent.Milestone;
import com.k8bas.skyblockutility.module.bestiary.BestiaryChatEvent.TierUp;
import com.k8bas.skyblockutility.skyblock.SbNumber;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T5.2, live session tracking: AC-BEST-10 (the counting rules), AC-BEST-09 (an abbreviated value is skipped), AC-BEST-03
 * (the widget's threshold wins, logged once a session), AC-BEST-04 (the milestone from chat), AC-BEST-15's display half
 * (an unknown name shows the server's values), and the newer value winning with its source and age (REQ-BEST-10,
 * EC-BEST-16), [A]. AC-BEST-17 does not apply: the G1 captures show the widget's exact counts, so live mode is on.
 */
class BestiaryTrackerTest {
	private static final String DWARVEN = "Dwarven Mines";

	private final List<String> log = new ArrayList<>();
	private final BestiaryTracker tracker = new BestiaryTracker(BestiaryTables.shipped(), log::add);

	private static WidgetLine ghost(String kills) {
		return BestiaryWidget.parseLine("Ghost 15: " + kills + "/12,500");
	}

	private void widget(long now, WidgetLine... lines) {
		tracker.widget(List.of(lines), DWARVEN, now);
	}

	private FamilyView ghostView() {
		FamilyView view = tracker.family("ghost");
		assertNotNull(view, "ghost is tracked");
		return view;
	}

	@Test
	void theCountingRules() {
		// AC-BEST-10: 12,449 → 12,452 → 12,600 → 12,590 → 12,592 gives +3, +0 (a jump over 50), +0 (a decrease), +2.
		List<Long> session = new ArrayList<>();
		long now = 1_000;
		for (String kills : List.of("12,449", "12,452", "12,600", "12,590", "12,592")) {
			widget(now += 1_000, ghost(kills));
			session.add(ghostView().sessionKills());
		}
		assertEquals(List.of(0L, 3L, 3L, 3L, 5L), session);
		assertEquals(5, tracker.sessionTotal());
		// After a world change the first value is a baseline again (EC-BEST-06).
		tracker.worldChanged();
		widget(now += 1_000, ghost("12,640"));
		assertEquals(5, ghostView().sessionKills());
		widget(now + 1_000, ghost("12,641"));
		assertEquals(6, ghostView().sessionKills());
	}

	@Test
	void anAbbreviatedValueIsSkipped() {
		// AC-BEST-09: 12,449 → "12.4k" → 12,452 gives +3; the abbreviated value shows as approximate meanwhile.
		widget(1_000, ghost("12,449"));
		widget(2_000, ghost("12.4k"));
		FamilyView approximate = ghostView();
		assertTrue(approximate.kills().getApproximate());
		assertEquals(0, approximate.sessionKills());
		assertEquals(100, approximate.toNext(), "derived from the approximate value");
		widget(3_000, ghost("12,452"));
		assertEquals(3, ghostView().sessionKills());
		assertFalse(ghostView().kills().getApproximate());
	}

	@Test
	void theWidgetsThresholdWinsAndIsLoggedOnceASession() {
		// AC-BEST-03: the table says 12,500 for tier 16; the widget says 12,600.
		WidgetLine line = BestiaryWidget.parseLine("Ghost 15: 12,449/12,600");
		widget(1_000, line);
		widget(2_000, line);
		tracker.worldChanged();
		widget(3_000, line);
		FamilyView view = ghostView();
		assertEquals(12_600, view.nextThreshold());
		assertEquals(151, view.toNext());
		assertEquals(12_551, view.toMax());
		assertEquals(1, log.stream().filter(line1 -> line1.contains("ghost") && line1.contains("12,600") && line1.contains("12,500")).count(), log.toString());
		// A new session (a profile switch) may log it once more.
		tracker.profileChanged();
		widget(4_000, line);
		assertEquals(2, log.stream().filter(line1 -> line1.contains("12,600")).count(), log.toString());
		// The table's threshold where the widget agrees, or shows none.
		widget(5_000, ghost("12,449"));
		assertEquals(12_500, ghostView().nextThreshold());
	}

	@Test
	void theMilestoneComesOnlyFromChatAndMenu() {
		// AC-BEST-04: no milestone information hides the line.
		widget(1_000, ghost("12,449"));
		assertNull(tracker.milestone());
		// "CCLXI➡CCLXII" reached, then three tier-ups: milestone 262, 7 tiers to the next.
		tracker.chat(new Milestone(261, 262, false), DWARVEN, 2_000);
		for (int i = 0; i < 3; i++) {
			tracker.chat(new TierUp("Glacite Walker", 9 + i, 10 + i), DWARVEN, 3_000 + i);
		}
		assertEquals(new MilestoneView(262, 7, BestiarySource.CHAT, 3_002), tracker.milestone());
		// A claim of milestones already counted changes nothing; a claim beyond them gives the milestone, its tiers unknown.
		tracker.chat(new Milestone(259, 262, true), DWARVEN, 4_000);
		assertEquals(new MilestoneView(262, 7, BestiarySource.CHAT, 3_002), tracker.milestone());
		tracker.chat(new Milestone(262, 265, true), DWARVEN, 5_000);
		assertEquals(new MilestoneView(265, null, BestiarySource.CHAT, 5_000), tracker.milestone());
		// The menu's milestone item says both (G1: milestone 351, 2 tiers to the next).
		tracker.menu(new BestiaryPage("Bestiary", "", List.of(), new MenuMilestone(351, 2, 3_518)), 6_000);
		assertEquals(new MilestoneView(351, 2, BestiarySource.MENU, 6_000), tracker.milestone());
		// Tier-ups count down from there, never below 0.
		tracker.chat(new TierUp("Ghost", 15, 16), DWARVEN, 7_000);
		tracker.chat(new TierUp("Bal", 11, 12), DWARVEN, 7_001);
		tracker.chat(new TierUp("Bal", 12, 13), DWARVEN, 7_002);
		assertEquals(0, tracker.milestone().tiersToNext());
	}

	@Test
	void theNewerValueWinsWithItsSourceAndAge() {
		widget(1_000, ghost("12,449"));
		assertEquals(BestiarySource.WIDGET, ghostView().source());
		assertEquals(1_000, ghostView().updatedAt());
		// A menu snapshot read later is newer (EC-BEST-16); its kills do not count as live kills.
		tracker.menu(new BestiaryPage("Bestiary", DWARVEN, List.of(new MenuFamily("Ghost", 15, new SbNumber(12_470, false), 10.0, null, false)), null), 2_000);
		assertEquals(new SbNumber(12_470, false), ghostView().kills());
		assertEquals(BestiarySource.MENU, ghostView().source());
		assertEquals(0, ghostView().sessionKills());
		// The widget again: it counts from its own last value.
		widget(3_000, ghost("12,475"));
		assertEquals(26, ghostView().sessionKills());
		// A tier-up from chat sets the tier and keeps the kills.
		tracker.chat(new TierUp("Ghost", 15, 16), DWARVEN, 4_000);
		FamilyView view = ghostView();
		assertEquals(16, view.tier());
		assertEquals(new SbNumber(12_475, false), view.kills());
		assertEquals(BestiarySource.CHAT, view.source());
		assertEquals(15_000, view.nextThreshold(), "the table's threshold for tier 17; the widget's was for tier 16");
	}

	@Test
	void maxAndTheEnd() {
		// EC-BEST-02: a MAX line keeps the last count, hides "to next" and shows 0 to max.
		widget(1_000, ghost("24,990"));
		widget(2_000, BestiaryWidget.parseLine("Ghost 20: MAX"));
		FamilyView view = ghostView();
		assertTrue(view.max());
		assertNull(view.toNext());
		assertNull(view.nextThreshold());
		assertEquals(0, view.toMax());
		assertEquals(new SbNumber(24_990, false), view.kills());
	}

	@Test
	void anUnknownNameShowsTheServersValues() {
		// AC-BEST-15, the display half: no table family, the widget's kills, tier and next threshold.
		widget(1_000, BestiaryWidget.parseLine("Nope Beast 3: 10/20"));
		widget(2_000, BestiaryWidget.parseLine("Nope Beast 3: 12/20"));
		List<FamilyView> views = tracker.families();
		assertEquals(1, views.size());
		FamilyView view = views.get(0);
		assertNull(view.family());
		assertEquals("Nope Beast", view.name());
		assertEquals(new SbNumber(12, false), view.kills());
		assertEquals(3, view.tier());
		assertEquals(20, view.nextThreshold());
		assertEquals(8, view.toNext());
		assertNull(view.toMax());
		assertEquals(2, view.sessionKills(), "counted all the same");
		assertEquals(1, log.size(), log.toString());
	}

	@Test
	void aSharedNameUsesTheWidgetsThresholdAndTheIsland() {
		// End Endermen listed on the player's island: 1,500 is the End family's threshold (T5.1b).
		tracker.widget(List.of(BestiaryWidget.parseLine("Enderman 20: 1,234/1,500")), "Private Island", 1_000);
		assertNotNull(tracker.family("enderman_combat_3"));
		assertNull(tracker.family("enderman_dynamic"));
		// A menu page names its area.
		tracker.menu(new BestiaryPage("Bestiary", "Your Island", List.of(new MenuFamily("Enderman", 3, new SbNumber(20, false), 40.0, null, false)), null), 2_000);
		assertEquals(3, tracker.family("enderman_dynamic").tier());
	}

	@Test
	void aProfileSwitchForgetsEverything() {
		widget(1_000, ghost("12,449"));
		widget(2_000, ghost("12,452"));
		tracker.chat(new Milestone(261, 262, false), DWARVEN, 2_500);
		tracker.profileChanged();
		assertEquals(List.of(), tracker.families());
		assertNull(tracker.milestone());
		assertEquals(0, tracker.sessionTotal());
		widget(3_000, ghost("12,460"));
		assertEquals(0, ghostView().sessionKills(), "a new baseline");
	}

	@Test
	void theLimitsOfALiveCount() {
		// REQ-BEST-11: a rise of exactly 50 counts; 51 resyncs.
		widget(1_000, ghost("12,000"));
		widget(2_000, ghost("12,050"));
		assertEquals(50, ghostView().sessionKills());
		widget(3_000, ghost("12,101"));
		assertEquals(50, ghostView().sessionKills());
		widget(4_000, ghost("12,102"));
		assertEquals(51, ghostView().sessionKills());
	}

	@Test
	void aProgressLineBeyondTheTablesLastTierIsNoMax() {
		// EC-BEST-09: the table ends Oasis Rabbit at tier 8 (cap 125, UNVERIFIED); the widget shows progress past it.
		tracker.widget(List.of(BestiaryWidget.parseLine("Oasis Rabbit 8: 130/200")), null, 1_000);
		FamilyView view = tracker.family("oasis_rabbit");
		assertNotNull(view);
		assertFalse(view.max());
		assertEquals(200, view.nextThreshold());
		assertEquals(70, view.toNext());
		assertEquals(70, view.toMax(), "never less than to the next tier");
		assertEquals(1, log.stream().filter(line -> line.contains("oasis_rabbit") && line.contains("tier 8")).count(), log.toString());
	}

	@Test
	void aChatTierUpPastTheTablesLastTierIsNoMax() {
		// EC-BEST-09 (G5): the widget showed a tier after Oasis Rabbit's table end, so reaching IX proves the table short.
		tracker.widget(List.of(BestiaryWidget.parseLine("Oasis Rabbit 8: 149/150")), null, 1_000);
		tracker.chat(new TierUp("Oasis Rabbit", 8, 9), null, 2_000);
		FamilyView rabbit = tracker.family("oasis_rabbit");
		assertEquals(9, rabbit.tier());
		assertFalse(rabbit.max(), "past the table's tier 8 the last tier is not known");
		// Reaching the table's last tier still is MAX.
		int last = BestiaryTables.shipped().family("ghost").maxTier();
		tracker.chat(new TierUp("Ghost", last - 1, last), DWARVEN, 3_000);
		assertTrue(ghostView().max());
	}

	@Test
	void anUnchangedWidgetLineDoesNotOverrideANewerValue() {
		// The tab list changes about every second for other reasons; an unchanged Bestiary line is no new value (EC-BEST-16).
		WidgetLine approximate = ghost("12.4k");
		widget(1_000, approximate);
		tracker.menu(new BestiaryPage("Bestiary", DWARVEN, List.of(new MenuFamily("Ghost", 15, new SbNumber(12_470, false), 10.0, null, false)), null), 2_000);
		widget(3_000, approximate);
		assertEquals(new SbNumber(12_470, false), ghostView().kills(), "the exact menu value stays (EC-BEST-17)");
		assertEquals(BestiarySource.MENU, ghostView().source());
		tracker.chat(new TierUp("Ghost", 15, 16), DWARVEN, 4_000);
		widget(5_000, approximate);
		assertEquals(16, ghostView().tier(), "the tier-up stays until the widget changes");
		widget(6_000, ghost("12,480"));
		assertEquals(new SbNumber(12_480, false), ghostView().kills(), "a changed line is new");
		assertEquals(BestiarySource.WIDGET, ghostView().source());
	}

	@Test
	void aFamilyListedTwiceInOneUpdateCountsOnce() {
		// Both Bat lines resolve to the island's family; their counts must not take turns as each other's baseline.
		for (int update = 0; update < 3; update++) {
			tracker.widget(List.of(BestiaryWidget.parseLine("Bat 3: 20/25"), BestiaryWidget.parseLine("Bat 2: 12/15")), "Private Island", 1_000 + update);
		}
		assertEquals(0, tracker.sessionTotal());
	}

	@Test
	void aSharedNameKeepsItsEntryWhereTheIslandCannotTell() {
		tracker.widget(List.of(BestiaryWidget.parseLine("Bat 3: 20/25")), "Private Island", 1_000);
		// On the Hub neither the threshold (25 is both families') nor the island decides; the Bat seen before is meant.
		tracker.widget(List.of(BestiaryWidget.parseLine("Bat 3: 21/25")), "Hub", 2_000);
		assertEquals(List.of("bat_dynamic"), tracker.families().stream().map(FamilyView::key).toList());
		assertEquals(1, tracker.family("bat_dynamic").sessionKills());
	}

	@Test
	void aProfileSwitchIsAnotherNameOrAnotherId() {
		widget(1_000, ghost("12,449"));
		tracker.profile("account", "apple", null);
		tracker.profile("account", "apple", "1234");
		assertNotNull(tracker.family("ghost"), "the same profile, its id now known");
		tracker.profile("account", "banana", null);
		assertNull(tracker.family("ghost"), "another profile");
		widget(2_000, ghost("12,449"));
		tracker.profile("account", "banana", "5678");
		tracker.profile("account", "banana", "9999");
		assertNull(tracker.family("ghost"), "a profile made again under the same name");
	}

	@Test
	void theKillsKeepTheirOwnSourceAndAge() {
		widget(1_000, ghost("12,449"));
		tracker.chat(new TierUp("Ghost", 15, 16), DWARVEN, 2_000);
		FamilyView view = ghostView();
		assertEquals(BestiarySource.CHAT, view.source());
		assertEquals(2_000, view.updatedAt());
		assertEquals(BestiarySource.WIDGET, view.killsSource());
		assertEquals(1_000L, view.killsUpdatedAt());
	}

	@Test
	void withoutTheTableOnlyTheServersValuesShow() {
		List<String> quiet = new ArrayList<>();
		BestiaryTracker bare = new BestiaryTracker(null, quiet::add);
		bare.widget(List.of(ghost("12,449")), DWARVEN, 1_000);
		FamilyView view = bare.family("name:ghost");
		assertNotNull(view);
		assertNull(view.family());
		assertEquals(12_500, view.nextThreshold());
		assertNull(view.toMax());
		assertEquals(List.of(), quiet);
	}
}
