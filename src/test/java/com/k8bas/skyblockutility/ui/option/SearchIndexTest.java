package com.k8bas.skyblockutility.ui.option;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-08 / AC-UI-07 [A] (T2.1): the search index over the option declarations. */
class SearchIndexTest {
	private static final boolean[] TOGGLED = {false};
	private static final Binding<Boolean> FLAG = Binding.of(() -> TOGGLED[0], value -> TOGGLED[0] = value);

	private static List<Card> cards() {
		Toggle highlighter = Toggle.of("mob_highlighter.enabled", "modules.mob_highlighter.enabled",
				new OptionText("Mob Highlighter", "Outlines mobs that match your rules.", "Hidden tooltip word: quokka.", List.of("glow")), true, FLAG);
		IntSlider range = IntSlider.of("mob_highlighter.scan_range", "general.mobScanRangeBlocks",
				new OptionText("Mob scan range", "How far around you mobs are checked.", "", List.of("radius")), 64, 0, 128, 1, "blocks", "Unlimited",
				Binding.of(() -> 64, value -> { }));
		Choice<String> channel = Choice.of("updates.channel", "general.updateChannel",
				new OptionText("Update channel", "Which releases you are told about.", "", List.of()), "STABLE", List.of("STABLE", "BETA"),
				value -> value.equals("BETA") ? "Beta (pre-releases)" : "Stable", Binding.of(() -> "STABLE", value -> { }));
		Toggle npc = Toggle.of("npc_search.enabled", "modules.npc_search.enabled",
				new OptionText("NPC Search", "Outlines NPCs and marks fixed NPC positions.", "", List.of("waypoint")), true, FLAG);
		// Not in category order on purpose: results must come in screen order anyway.
		return List.of(
				new Card("npc_search", Category.WAYPOINTS, "NPC Search", npc, List.of()),
				new Card("mob_highlighter", Category.HIGHLIGHTS, "Mob Highlighter", highlighter, List.of(range)),
				new Card("updates", Category.GENERAL, "Updates", null, List.of(channel)));
	}

	@Test
	void everyOptionIsFoundByItsTitle() {
		SearchIndex index = SearchIndex.of(cards());
		for (Card card : cards()) {
			for (Option option : card.all()) {
				assertTrue(index.search(option.text().title()).hits().stream().anyMatch(hit -> hit.id().equals(option.id())), option.id());
			}
		}
	}

	/** AC-UI-07: "range" returns "Mob scan range", and "  RANGE " gives the same result. */
	@Test
	void theQueryIsTrimmedAndCaseInsensitive() {
		SearchIndex index = SearchIndex.of(cards());
		SearchIndex.Result range = index.search("range");
		assertEquals(List.of("mob_highlighter.scan_range"), range.hits().stream().map(SearchIndex.Entry::id).toList());
		assertEquals(range.hits(), index.search("  RANGE ").hits());
	}

	@Test
	void descriptionsKeywordsAndDropdownLabelsMatchButTooltipsDoNot() {
		SearchIndex index = SearchIndex.of(cards());
		assertEquals(List.of("mob_highlighter.scan_range"), ids(index.search("how far")));
		assertEquals(List.of("npc_search.enabled"), ids(index.search("WAYPOINT")));
		assertEquals(List.of("updates.channel"), ids(index.search("pre-rel")), "a dropdown entry's label");
		assertTrue(index.search("quokka").hits().isEmpty(), "the tooltip is not searched");
	}

	/** AC-UI-07: badges equal the per-category hit counts; "zzzz" has no hit anywhere. */
	@Test
	void countsArePerCategory() {
		SearchIndex index = SearchIndex.of(cards());
		SearchIndex.Result outlines = index.search("outlines");
		assertEquals(1, outlines.count(Category.HIGHLIGHTS));
		assertEquals(1, outlines.count(Category.WAYPOINTS));
		assertEquals(0, outlines.count(Category.GENERAL));
		assertEquals(List.of(Category.HIGHLIGHTS, Category.WAYPOINTS), outlines.categoriesWithHits(), "in category order");
		SearchIndex.Result mobs = index.search("mob");
		assertEquals(2, mobs.count(Category.HIGHLIGHTS), "both Mob Highlighter options");
		assertEquals(2, mobs.hits().size());
		// An empty box filters nothing.
		for (String empty : List.of("", "   ")) {
			SearchIndex.Result all = index.search(empty);
			assertEquals(4, all.hits().size());
			assertEquals(2, all.count(Category.HIGHLIGHTS));
			assertEquals(1, all.count(Category.GENERAL));
			assertEquals(1, all.count(Category.WAYPOINTS));
		}
		SearchIndex.Result none = index.search("zzzz");
		assertTrue(none.hits().isEmpty());
		assertTrue(none.categoriesWithHits().isEmpty());
		for (Category category : Category.values()) {
			assertEquals(0, none.count(category));
		}
	}

	@Test
	void theQueryIsCappedAt35Characters() {
		assertEquals(35, SearchIndex.normalize("x".repeat(40)).length());
		assertEquals("range", SearchIndex.normalize("  RANGE  "));
		// The cap applies to what was typed, then the spaces go (EC-UI-10).
		assertEquals("a", SearchIndex.normalize("a" + " ".repeat(40) + "b"));
	}

	/** REQ-UI-09: a card's rules are found by their label and pattern, each by its own id. */
	@Test
	void rulesJoinTheIndex() {
		RuleGroup trinity = new RuleGroup("rule:trinity", () -> "Trinity", () -> 0, () -> null, () -> List.of("Trinity", "Trinity"), List.of());
		List<Card> cards = new java.util.ArrayList<>(cards());
		cards.add(new Card("npc_search", Category.WAYPOINTS, "NPC Search", null, List.of(), () -> List.of(trinity)));
		SearchIndex index = SearchIndex.of(cards);
		SearchIndex.Result trini = index.search("trini");
		assertEquals(List.of("rule:trinity"), ids(trini));
		assertEquals(1, trini.count(Category.WAYPOINTS));
	}

	@Test
	void declarationsRejectWhatTheScreenCannotShow() {
		assertThrowsIllegal(() -> new OptionText(" ", "d", "", List.of()));
		assertThrowsIllegal(() -> IntSlider.of("a", "b", new OptionText("t", "d", "", List.of()), 200, 0, 128, 1, "", "", Binding.of(() -> 0, v -> { })));
		assertThrowsIllegal(() -> IntSlider.of("a", "b", new OptionText("t", "d", "", List.of()), 1, 0, 128, 0, "", "", Binding.of(() -> 0, v -> { })));
		assertFalse(Toggle.of("a", "b", new OptionText("t", "d", "", List.of()), true, FLAG).amber());
		assertTrue(Toggle.of("a", "b", new OptionText("t", "d", "", List.of()), true, FLAG).asAmber().amber());
	}

	private static void assertThrowsIllegal(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("expected an IllegalArgumentException");
	}

	private static List<String> ids(SearchIndex.Result result) {
		return result.hits().stream().map(SearchIndex.Entry::id).toList();
	}
}
