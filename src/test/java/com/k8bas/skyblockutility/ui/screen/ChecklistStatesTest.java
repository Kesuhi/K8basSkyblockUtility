package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Checklist;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.UiTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-67 [A] (REQ-UI-61, T5.3c), on a test clock: a click opens and closes a checklist over 150 ms (REQ-UI-52), at once with
 * Animations OFF; a search whose query is in an item's label shows it open at once, one only in the card's title does not; a
 * click during the search counts until the query changes, so when the search ends each list is as it was before it (EC-UI-32).
 */
class ChecklistStatesTest {
	private final long[] now = {5_000};
	private final boolean[] motion = {true};
	private final ChecklistStates states = new ChecklistStates(new RuleMotion(UiTime.of(() -> now[0], () -> motion[0])));
	private final Checklist list = new Checklist("test.lines", new OptionText("Lines shown", "Pick the lines.", "", List.of()), List.of(
			item("test.kills", "Kills"), item("test.rate", "Kills per hour"), item("test.session", "Session kills")));

	private static Toggle item(String id, String title) {
		boolean[] on = {false};
		return Toggle.of(id, "test." + id, new OptionText(title, "", "", List.of()), false, Binding.of(() -> on[0], value -> on[0] = value));
	}

	private float reveal() {
		return states.reveal(list, false, "");
	}

	@Test
	void aClickOpensItOver150MsAndClosesItTheSameWay() {
		assertEquals(0F, reveal(), "closed");
		states.toggle(list, false, "");
		assertTrue(states.expanded(list, false, ""));
		assertEquals(0F, reveal(), "0 ms");
		now[0] += 75;
		assertEquals(0.875F, reveal(), 1e-4, "75 ms, ease-out cubic");
		now[0] += 75;
		assertEquals(1F, reveal(), "150 ms");
		states.toggle(list, false, "");
		assertFalse(states.expanded(list, false, ""));
		now[0] += 75;
		float closing = reveal();
		assertTrue(closing > 0F && closing < 1F, "closing, 75 ms: " + closing);
		now[0] += 75;
		assertEquals(0F, reveal(), "closed, 150 ms");
	}

	@Test
	void withAnimationsOffAClickOpensItAtOnce() {
		motion[0] = false;
		reveal();
		states.toggle(list, false, "");
		assertEquals(1F, reveal(), "open at once");
		states.toggle(list, false, "");
		assertEquals(0F, reveal(), "closed at once");
	}

	/** An item's label opens it at once with the motion ON; the search's end closes it at once. */
	@Test
	void aSearchInAnItemsLabelShowsItOpenAtOnce() {
		assertEquals(0F, reveal());
		assertTrue(states.expanded(list, true, "session"));
		assertEquals(1F, states.reveal(list, true, "session"), "at once, no motion");
		states.queryChanged();
		assertFalse(states.expanded(list, false, ""));
		assertEquals(0F, reveal(), "the search ended: closed at once");
	}

	@Test
	void aSearchOnlyInTheCardsTitleLeavesItClosed() {
		assertFalse(states.expanded(list, true, "lines shown"));
		assertFalse(states.expanded(list, true, "pick the"), "nor in its description");
	}

	@Test
	void aClickClosesItDuringTheSearchUntilTheQueryChanges() {
		assertTrue(states.expanded(list, true, "kills"));
		states.toggle(list, true, "kills");
		assertFalse(states.expanded(list, true, "kills"), "closed by the click");
		assertFalse(states.expanded(list, true, "kills"), "and stays closed");
		states.queryChanged();
		assertTrue(states.expanded(list, true, "kills p"), "another query opens it again");
	}

	/** EC-UI-32 as for rules: a list opened by a click during a search closes when it ends; it was not open before. */
	@Test
	void aListOpenedDuringASearchClosesWhenItEnds() {
		states.toggle(list, true, "lines shown");
		assertTrue(states.expanded(list, true, "lines shown"), "opened by the click");
		states.queryChanged();
		assertFalse(states.expanded(list, false, ""), "the search ended: as before it, closed");
	}

	/** A list opened by a click during a search closes when the query changes and does not match its items either. */
	@Test
	void aListOpenedDuringASearchClosesWhenTheQueryChanges() {
		states.toggle(list, true, "lines shown");
		states.queryChanged();
		assertFalse(states.expanded(list, true, "lines"), "the click counted only until the query changed");
	}

	/** And a list open before the search is open again after it, though a click closed it during the search. */
	@Test
	void aListOpenBeforeTheSearchIsOpenAfterIt() {
		states.toggle(list, false, "");
		states.toggle(list, true, "kills");
		assertFalse(states.expanded(list, true, "kills"), "closed during the search");
		states.queryChanged();
		assertTrue(states.expanded(list, false, ""), "the search ended: as before it, open");
	}
}
