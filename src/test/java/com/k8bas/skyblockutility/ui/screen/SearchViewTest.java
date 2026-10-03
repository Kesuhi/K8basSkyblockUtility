package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.SearchIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-08, AC-UI-07, EC-UI-10 (T2.4b): what the screen shows for a query. */
class SearchViewTest {
	private static final List<Card> CARDS = OptionCatalog.build(new GeneralConfig(), new MobHighlighterConfig(), new NpcSearchConfig(),
			enabled -> { }, enabled -> { });
	private static final SearchIndex INDEX = SearchIndex.of(CARDS);
	private static final List<Category> ALL = ConfigLayout.categories(CARDS);

	private static Option option(String id) {
		return CARDS.stream().flatMap(card -> card.all().stream()).filter(option -> option.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void anEmptyQueryShowsEverythingWithOptionCounts() {
		SearchView view = SearchView.of(INDEX, ALL, "", Category.WAYPOINTS);
		assertEquals(ALL, view.categories());
		assertEquals(Category.WAYPOINTS, view.selected());
		assertFalse(view.filtering());
		assertFalse(view.nothingFound());
		for (Category category : ALL) {
			int options = CARDS.stream().filter(card -> card.category() == category).mapToInt(card -> card.all().size()).sum();
			assertEquals(options, view.badge(category), category.toString());
		}
		CARDS.stream().flatMap(card -> card.all().stream()).forEach(option -> assertTrue(view.shows(option), option.id()));
	}

	/** AC-UI-07: "range" finds the scan range; the active category without a hit switches to the first with one. */
	@Test
	void aQueryFiltersAndSwitchesToTheFirstCategoryWithAHit() {
		SearchView view = SearchView.of(INDEX, ALL, "range", Category.GENERAL);
		assertTrue(view.filtering());
		assertEquals(List.of(Category.HIGHLIGHTS), view.categories());
		assertEquals(Category.HIGHLIGHTS, view.selected(), "General has no hit");
		assertEquals(1, view.badge(Category.HIGHLIGHTS), "the badge is the hit count");
		assertTrue(view.shows(option("mob_highlighter.scan_range")), "Mob scan range shows");
		assertFalse(view.shows(option("mob_highlighter.enabled")), "the feature's switch does not match");
		SearchView spaced = SearchView.of(INDEX, ALL, "  RANGE ", Category.GENERAL);
		assertEquals(view.categories(), spaced.categories(), "EC-UI-10: trimmed, any case");
		assertEquals(view.selected(), spaced.selected());
	}

	@Test
	void theActiveCategoryStaysWhileItHasAHit() {
		SearchView view = SearchView.of(INDEX, ALL, "npc", Category.WAYPOINTS);
		assertTrue(view.categories().contains(Category.WAYPOINTS));
		assertEquals(Category.WAYPOINTS, view.selected());
	}

	/** AC-UI-07: "zzzz" finds nothing: an empty sidebar and the message. */
	@Test
	void nothingFoundEmptiesTheSidebar() {
		SearchView view = SearchView.of(INDEX, ALL, "zzzz", Category.HIGHLIGHTS);
		assertTrue(view.nothingFound());
		assertTrue(view.categories().isEmpty());
		assertEquals("No settings found for \"zzzz\"", view.message());
		assertEquals(Category.HIGHLIGHTS, view.selected(), "the category stays for when the query changes again");
	}

	/** The message repeats the query as typed (trimmed), not lower-cased. */
	@Test
	void theMessageKeepsTheTypedCase() {
		assertEquals("No settings found for \"Glowing ZZ\"", SearchView.of(INDEX, ALL, "  Glowing ZZ ", Category.GENERAL).message());
	}

	/** EC-UI-10: only the first 35 characters count. */
	@Test
	void theQueryIsCappedAt35Characters() {
		String typed = "range" + " ".repeat(40) + "zzzz";
		SearchView view = SearchView.of(INDEX, ALL, typed, Category.GENERAL);
		assertFalse(view.nothingFound(), "the part past 35 characters is ignored");
	}
}
