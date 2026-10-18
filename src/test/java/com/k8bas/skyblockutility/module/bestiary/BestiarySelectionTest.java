package com.k8bas.skyblockutility.module.bestiary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * T5.2e, the family selection [A]: AC-BEST-08 (Ghost +3 and then Goblin +1 → Goblin; Ghost +3 and Goblin +1 in the same
 * update → Ghost; a pinned list of 5 shows exactly those 5, and a sixth pin is refused) and REQ-BEST-09 (the name breaks
 * a tie; an option hides maxed families).
 */
class BestiarySelectionTest {
	private static final String DWARVEN = "Dwarven Mines";

	private final BestiaryTracker tracker = new BestiaryTracker(BestiaryTables.shipped(), line -> { });

	private void update(long now, String... lines) {
		List<WidgetLine> parsed = new ArrayList<>();
		for (String line : lines) {
			parsed.add(BestiaryWidget.parseLine(line));
		}
		tracker.widget(parsed, DWARVEN, now);
	}

	private List<String> auto(boolean hideMaxed) {
		return BestiarySelection.select(tracker, SelectionMode.AUTO, List.of(), hideMaxed).stream().map(FamilyView::key).toList();
	}

	/** Ghost at 12,449 and Goblin at 1,000, both baselines. */
	private void baselines() {
		update(1_000, "Ghost 15: 12,449/12,500", "Goblin 15: 1,000/1,350");
	}

	@Test
	void autoFollowShowsTheFamilyThatRoseLast() {
		baselines();
		update(2_000, "Ghost 15: 12,452/12,500", "Goblin 15: 1,000/1,350");
		assertEquals(List.of("ghost"), auto(false));
		update(3_000, "Ghost 15: 12,452/12,500", "Goblin 15: 1,001/1,350");
		assertEquals(List.of("goblin"), auto(false));
	}

	@Test
	void inOneUpdateTheBiggerRiseWinsThenTheName() {
		baselines();
		update(2_000, "Ghost 15: 12,452/12,500", "Goblin 15: 1,001/1,350");
		assertEquals(List.of("ghost"), auto(false), "+3 over +1");
		update(3_000, "Ghost 15: 12,454/12,500", "Goblin 15: 1,003/1,350");
		assertEquals(List.of("ghost"), auto(false), "both +2: Ghost before Goblin");
		update(4_000, "Goblin 15: 1,006/1,350", "Ghost 15: 12,455/12,500");
		assertEquals(List.of("goblin"), auto(false), "+3 over +1, the bigger rise listed later and named later");
	}

	@Test
	void aTieGoesByNameNotByWhichFamilyCameFirst() {
		// Goblin seen first, then both +2 in one update: Ghost, by its name.
		update(1_000, "Goblin 15: 1,000/1,350", "Ghost 15: 12,449/12,500");
		update(2_000, "Goblin 15: 1,002/1,350", "Ghost 15: 12,451/12,500");
		assertEquals(List.of("ghost"), auto(false));
	}

	@Test
	void whatCountsAsAChange() {
		baselines();
		update(2_000, "Ghost 15: 12,452/12,500", "Goblin 15: 1,000/1,350");
		// A fall is no change (a resync, EC-BEST-05): Ghost stays.
		update(3_000, "Ghost 15: 12,452/12,500", "Goblin 15: 990/1,350");
		assertEquals(List.of("ghost"), auto(false));
		// A rise over 50 (the widget back after a while) is one, though it counts no kills (EC-BEST-04).
		update(4_000, "Ghost 15: 12,452/12,500", "Goblin 15: 1,100/1,350");
		assertEquals(List.of("goblin"), auto(false));
		// An abbreviated count that changes is one too, though it is never a delta (REQ-BEST-17).
		update(5_000, "Ghost 15: 12.4k/12,500", "Goblin 15: 1,100/1,350");
		update(6_000, "Ghost 15: 12.5k/12,500", "Goblin 15: 1,100/1,350");
		assertEquals(List.of("ghost"), auto(false));
	}

	@Test
	void beforeAnyRiseTheNewestFamilyShows() {
		assertEquals(List.of(), auto(false), "nothing seen, nothing shown");
		baselines();
		tracker.menu(new BestiaryPage("Bestiary", DWARVEN, List.of(new MenuFamily("Bal", 11, null, 10.0, null, false)), null), 5_000);
		assertEquals(List.of("bal"), auto(false));
	}

	@Test
	void aPinnedListOfFiveShowsExactlyThoseAndRefusesASixth() {
		List<String> pins = new ArrayList<>();
		for (String key : List.of("ghost", "goblin", "bal", "stoneworm", "glacite_walker")) {
			pins = BestiarySelection.pin(pins, key);
		}
		assertEquals(List.of("ghost", "goblin", "bal", "stoneworm", "glacite_walker"), pins);
		assertNull(BestiarySelection.pin(pins, "enderman_combat_3"), "a sixth is refused");
		assertEquals(pins, BestiarySelection.pin(pins, "bal"), "a family pinned already stays where it is");
		// More than five pins (a list from before the repair) shows the first five.
		List<String> seven = new ArrayList<>(pins);
		seven.addAll(List.of("enderman_combat_3", "bat_dynamic"));
		assertEquals(5, BestiarySelection.select(tracker, SelectionMode.PINNED, seven, false).size());
		baselines();
		List<FamilyView> shown = BestiarySelection.select(tracker, SelectionMode.PINNED, pins, false);
		assertEquals(pins, shown.stream().map(FamilyView::key).toList(), "in the pinned order, also families not seen yet");
		assertEquals("Stoneworm", shown.get(3).name());
		assertNull(shown.get(3).kills(), "not seen: no values yet");
		// A pinned key the table does not know (an old config) is left out.
		assertEquals(List.of("ghost"), BestiarySelection.select(tracker, SelectionMode.PINNED, List.of("ghost", "no_such_family"), false)
				.stream().map(FamilyView::key).toList());
	}

	@Test
	void maxedFamiliesCanBeHidden() {
		baselines();
		update(2_000, "Ghost 15: 12,452/12,500", "Goblin 15: 1,000/1,350");
		update(3_000, "Ghost 20: MAX", "Goblin 15: 1,000/1,350");
		assertEquals(List.of("ghost"), auto(false));
		assertEquals(List.of("goblin"), auto(true), "the newest family that is not maxed");
		assertEquals(List.of("goblin"), BestiarySelection.select(tracker, SelectionMode.PINNED, List.of("ghost", "goblin"), true)
				.stream().map(FamilyView::key).toList());
	}

	@Test
	void theSelectionSettingsAndTheirRepair() {
		BestiaryConfig config = new BestiaryConfig();
		assertEquals(SelectionMode.AUTO, config.selection, "auto-follow by default");
		assertEquals(List.of(), config.pins);
		assertEquals(true, config.hideMaxed, "hide maxed ON (R59)");
		assertEquals(false, config.normalize());
		// A hand-edited file: a repeated pin, more than five, a missing mode and list.
		config.pins = new ArrayList<>(List.of("ghost", "ghost", "bal", "goblin", "stoneworm", "glacite_walker", "enderman_combat_3"));
		config.selection = null;
		assertEquals(true, config.normalize());
		assertEquals(List.of("ghost", "bal", "goblin", "stoneworm", "glacite_walker"), config.pins);
		assertEquals(SelectionMode.AUTO, config.selection);
		config.pins = null;
		config.normalize();
		assertEquals(List.of(), config.pins);
		// A null or blank pin (a JSON null in the file) is dropped, so no frame trips on it.
		config.pins = new ArrayList<>(java.util.Arrays.asList("ghost", null, "", "bal"));
		assertEquals(true, config.normalize());
		assertEquals(List.of("ghost", "bal"), config.pins);
	}
}
