package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The menu reader's core (T3.0e, REQ-GS-06, AC-GS-06 [A], EC-GS-06, EC-GS-07, EC-GS-08, EC-GS-09): recorded
 * open, content, slot-update, page-change and close sequences give exactly one snapshot per stable state.
 * The menus are synthetic; the slot shapes follow the G1 captures (containers/, see its README).
 */
class MenuTrackerTest {
	private final MenuTracker tracker = new MenuTracker(() -> { });
	private final List<String> events = new ArrayList<>();
	private int reads;

	private static MenuSnapshot menu(String title, int stateId, String... names) {
		List<MenuSlot> slots = new ArrayList<>();
		for (int i = 0; i < names.length; i++) {
			slots.add(new MenuSlot(i, "minecraft:paper", 1, names[i], List.of("lore of " + names[i]), null));
		}
		return new MenuSnapshot(title, stateId, 54, List.copyOf(slots));
	}

	private OpenMenu open(Object screen, MenuSnapshot contents) {
		return new OpenMenu(screen, contents.getTitle(), () -> {
			reads++;
			return contents;
		});
	}

	private Consumer<MenuEvent> record(String who) {
		return event -> events.add(who + ":" + (event instanceof MenuEvent.Shown shown
				? shown.getMenu().getTitle() + "#" + shown.getMenu().getStateId() : "closed"));
	}

	private void subscribe(String who, BooleanSupplier active, String title) {
		tracker.subscribe(who, active, title::equals, record(who));
	}

	private void ticks(int n, OpenMenu open) {
		for (int i = 0; i < n; i++) {
			tracker.tick(open);
		}
	}

	@Test
	void aRecordedSequenceGivesOneSnapshotPerStableState() {
		subscribe("a", () -> true, "SkyBlock Leveling");
		Object page1 = new Object();
		Object page2 = new Object();
		ticks(2, open(page1, menu("SkyBlock Leveling", 0)));                 // opened, nothing arrived yet
		ticks(3, open(page1, menu("SkyBlock Leveling", 3, "Level", "Tasks")));  // content, then 2 quiet ticks
		ticks(1, open(page1, menu("SkyBlock Leveling", 4, "Level", "Task")));   // a slot update, not stable yet
		ticks(3, open(page1, menu("SkyBlock Leveling", 5, "Level", "Tasks!"))); // the next one is
		ticks(3, open(page2, menu("SkyBlock Leveling", 1, "Page 2")));          // same title, next page (EC-GS-06)
		ticks(5, open(page2, menu("SkyBlock Leveling", 1, "Page 2")));          // unchanged: nothing more
		tracker.tick(null);
		tracker.tick(null);
		assertEquals(List.of("a:SkyBlock Leveling#3", "a:SkyBlock Leveling#5", "a:SkyBlock Leveling#1", "a:closed"), events);
	}

	@Test
	void theSnapshotIsStableAfterContentAndTwoQuietTicks() {
		subscribe("a", () -> true, "Pets");
		OpenMenu pets = open(new Object(), menu("Pets", 2, "Pet"));
		tracker.tick(pets);
		tracker.tick(pets);
		assertEquals(List.of(), events, "one quiet tick is not enough");
		assertNull(tracker.current());
		tracker.tick(pets);
		assertEquals(List.of("a:Pets#2"), events);
		assertEquals("Pets", tracker.current().getTitle());
	}

	@Test
	void aMenuClosedBeforeItIsStableGivesNothing() {
		// EC-GS-07: no snapshot and no partial data; there was nothing to close.
		subscribe("a", () -> true, "Bestiary");
		ticks(2, open(new Object(), menu("Bestiary", 4, "Zombie")));
		ticks(3, null);
		assertEquals(List.of(), events);
	}

	@Test
	void anEmptyMenuWithoutServerStateIsNeverShown() {
		subscribe("a", () -> true, "Bestiary");
		ticks(10, open(new Object(), menu("Bestiary", 0)));
		assertEquals(List.of(), events);
	}

	@Test
	void aNonMatchingTitleIsNotRead() {
		subscribe("a", () -> true, "Bestiary");
		ticks(10, open(new Object(), menu("Ender Chest", 3, "Sword")));
		assertEquals(List.of(), events);
		assertEquals(0, reads, "a menu nobody asked for is not read");
	}

	@Test
	void anInactiveSubscriberGetsNothingAndNothingIsRead() {
		// REQ-GS-16: with every consumer off, the reader reads nothing.
		subscribe("a", () -> false, "Bestiary");
		ticks(10, open(new Object(), menu("Bestiary", 3, "Zombie")));
		assertEquals(List.of(), events);
		assertEquals(0, reads);
	}

	@Test
	void eachSubscriberHearsOnlyItsOwnTitles() {
		subscribe("best", () -> true, "Bestiary");
		subscribe("pets", () -> true, "Pets");
		ticks(3, open(new Object(), menu("Pets", 2, "Pet")));
		ticks(3, open(new Object(), menu("Bestiary", 2, "Zombie")));
		tracker.tick(null);
		assertEquals(List.of("pets:Pets#2", "pets:closed", "best:Bestiary#2", "best:closed"), events);
	}

	@Test
	void aSubscriberTurnedOnLaterGetsTheOpenMenuOnce() {
		boolean[] on = {false};
		subscribe("a", () -> true, "Pets");
		subscribe("b", () -> on[0], "Pets");
		OpenMenu pets = open(new Object(), menu("Pets", 2, "Pet"));
		ticks(3, pets);
		on[0] = true;
		ticks(3, pets);
		assertEquals(List.of("a:Pets#2", "b:Pets#2"), events);
		on[0] = false;
		tracker.tick(pets);
		assertEquals("b:closed", events.get(events.size() - 1), "a subscriber turned off hears the menu is gone for it");
	}

	@Test
	void aClearClosesAndReadsTheMenuStillOpenAfresh() {
		// EC-GS-08 / EC-GS-09: a disconnect or a profile switch while a menu is open: close is signalled and the
		// snapshot dropped; a menu still open is read again from the start, so a late clear loses nothing.
		subscribe("a", () -> true, "Pets");
		OpenMenu pets = open(new Object(), menu("Pets", 2, "Pet"));
		ticks(3, pets);
		tracker.clear();
		assertNull(tracker.current());
		tracker.tick(pets);
		assertNull(tracker.current(), "not stable again before two quiet ticks");
		ticks(2, pets);
		assertEquals(List.of("a:Pets#2", "a:closed", "a:Pets#2"), events);
	}

	@Test
	void aChangedStateIdAloneIsNoNewSnapshot() {
		// A slot of the player's inventory changed under the menu, or the server resent the same contents.
		subscribe("a", () -> true, "Pets");
		Object screen = new Object();
		ticks(3, open(screen, menu("Pets", 2, "Pet")));
		ticks(5, open(screen, menu("Pets", 3, "Pet")));
		assertEquals(List.of("a:Pets#2"), events);
	}

	@Test
	void aRefusedClickThatComesBackUnchangedIsNoNewSnapshot() {
		// The client empties the clicked pane for a tick; the server refuses and resends the same contents.
		subscribe("a", () -> true, "Pets");
		Object screen = new Object();
		ticks(3, open(screen, menu("Pets", 2, "Pane", "Pet")));
		ticks(1, open(screen, menu("Pets", 2, "Pet")));
		ticks(5, open(screen, menu("Pets", 4, "Pane", "Pet")));
		assertEquals(List.of("a:Pets#2"), events);
		assertEquals(4, tracker.current().getStateId(), "the current snapshot has the latest state id");
	}

	@Test
	void aFailingListenerDoesNotStopTheOthers() {
		tracker.subscribe("bad", () -> true, "Pets"::equals, event -> {
			throw new IllegalStateException("boom");
		});
		subscribe("a", () -> true, "Pets");
		ticks(3, open(new Object(), menu("Pets", 2, "Pet")));
		assertEquals(List.of("a:Pets#2"), events);
	}

	@Test
	void theSameSnapshotGoesToEverySubscriber() {
		List<MenuSnapshot> seen = new ArrayList<>();
		for (String who : List.of("a", "b")) {
			tracker.subscribe(who, () -> true, "Pets"::equals, event -> {
				if (event instanceof MenuEvent.Shown shown) {
					seen.add(shown.getMenu());
				}
			});
		}
		ticks(3, open(new Object(), menu("Pets", 2, "Pet")));
		assertEquals(2, seen.size());
		assertSame(seen.get(0), seen.get(1));
		assertEquals(1, reads / 3, "read once per tick, not once per subscriber");
	}
}
