package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.module.bestiary.BestiaryConfig;
import com.k8bas.skyblockutility.module.bestiary.BestiaryLine;
import com.k8bas.skyblockutility.module.bestiary.BestiaryModule;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Checklist;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.SearchIndex;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.widget.Widget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-67 [A] (REQ-UI-61, R60, T5.3c): the checklist option and how the page lays it out, on the Bestiary Tracker card,
 * whose ten line toggles became one checklist (REQ-BEST-08).
 */
class ChecklistTest {
	private static final ToIntFunction<String> WIDTH = text -> text.length() * 6;
	private static final List<String> ITEMS = List.of("family", "kills", "to_next", "to_max", "milestone", "rate", "eta", "session_kills",
			"session_average", "source_age");
	private static final List<String> FIELDS = List.of("lineFamily", "lineKills", "lineToNext", "lineToMax", "lineMilestone", "lineRate", "lineEta",
			"lineSessionKills", "lineSessionAverage", "lineSourceAge");

	private final BestiaryConfig config = new BestiaryConfig();
	private final List<Card> cards = BestiaryModule.cards(config, enabled -> { });

	private Checklist lines() {
		for (Option option : cards.get(0).all()) {
			if (option instanceof Checklist list && list.id().equals("bestiary.lines")) {
				return list;
			}
		}
		throw new AssertionError("the Bestiary Tracker card has no checklist \"bestiary.lines\": "
				+ cards.get(0).all().stream().map(Option::id).toList());
	}

	private static Toggle item(Checklist list, String key) {
		return list.items().stream().filter(item -> item.id().equals("bestiary.line_" + key)).findFirst().orElseThrow();
	}

	/** Lays out the Odds & Trackers page with the checklist open as far as {@code reveal} (0 closed). */
	private ConfigLayout.Page page(float reveal) {
		return ConfigLayout.page(cards, Category.ODDS_TRACKERS, 600, WIDTH, filter(reveal));
	}

	private static ConfigLayout.Filter filter(float reveal) {
		return new ConfigLayout.Filter() {
			@Override
			public boolean shows(Option option) {
				return true;
			}

			@Override
			public boolean expanded(Checklist list) {
				return reveal > 0F;
			}

			@Override
			public float reveal(Checklist list) {
				return reveal;
			}
		};
	}

	private static List<ConfigLayout.CheckRow> checkRows(ConfigLayout.Page page) {
		return page.rows().stream().filter(row -> row instanceof ConfigLayout.CheckRow).map(row -> (ConfigLayout.CheckRow) row).toList();
	}

	private static ConfigLayout.OptionRow optionRow(ConfigLayout.Page page, String id) {
		return page.rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow o && o.option().id().equals(id))
				.map(row -> (ConfigLayout.OptionRow) row).findFirst().orElseThrow(() -> new AssertionError("no row " + id));
	}

	/** The option after the checklist on the card, whose row the open list pushes down. */
	private String next() {
		List<Option> all = cards.get(0).all();
		return all.get(all.indexOf(lines()) + 1).id();
	}

	/** REQ-BEST-08, R60: the ten lines are one checklist, each item the toggle it was, with its id, config field and default. */
	@Test
	void theBestiaryLinesAreOneChecklist() {
		Checklist lines = lines();
		assertEquals("Lines shown", lines.text().title());
		assertEquals(List.of(), cards.get(0).all().stream().filter(option -> option instanceof Toggle).map(Option::id)
				.filter(id -> id.startsWith("bestiary.line_")).toList(), "no line toggle of its own on the card");
		assertEquals(ITEMS.stream().map(key -> "bestiary.line_" + key).toList(), lines.items().stream().map(Toggle::id).toList());
		assertEquals(FIELDS.stream().map(field -> "modules.bestiary." + field).toList(), lines.items().stream().map(Toggle::storageKey).toList());
		assertEquals(Arrays.stream(BestiaryLine.values()).map(BestiaryLine::getLabel).toList(), lines.items().stream().map(item -> item.text().title()).toList(),
				"each item is labelled as its line");
		assertEquals(Arrays.stream(BestiaryLine.values()).map(BestiaryLine::getDefaultShown).toList(), lines.items().stream().map(Toggle::defaultValue).toList());
		assertEquals(List.of("bestiary.line_family", "bestiary.line_kills", "bestiary.line_to_next", "bestiary.line_milestone", "bestiary.line_rate",
				"bestiary.line_session_kills"), lines.defaultValue(), "the default is the items on in a fresh config");
		// Each item still reaches its field: To max on puts it into the HUD's layout.
		assertFalse(config.layout().shown().contains(BestiaryLine.TO_MAX));
		item(lines, "to_max").binding().set(true);
		assertTrue(config.lineToMax && config.layout().shown().contains(BestiaryLine.TO_MAX));
		// AC-BEST-07: a line hidden in the checklist leaves the HUD's layout.
		item(lines, "kills").binding().set(false);
		assertFalse(config.lineKills || config.layout().shown().contains(BestiaryLine.KILLS), "Kills hidden");
	}

	/** The button: a keyboard stop; a click, Space or Enter opens or closes the list; Right opens, Left closes; its chevron. */
	@Test
	void theButtonOpensAndClosesTheList() {
		boolean[] open = {false};
		List<Integer> clicks = new ArrayList<>();
		ChecklistButton button = new ChecklistButton(lines(), () -> open[0] = !open[0], () -> open[0], () -> clicks.add(1));
		button.setBounds(10, 10, ConfigLayout.CHECKLIST_BUTTON, ConfigLayout.CONTROL_HEIGHT);
		assertTrue(button.focusable(), "a keyboard stop");
		assertEquals("6/10", button.label());
		assertEquals(com.k8bas.skyblockutility.ui.render.IconShapes.Icon.CHEVRON_DOWN, button.chevron(), "closed: pointing down");
		assertTrue(button.press(12, 12, 0), "a click");
		button.release(12, 12);
		assertTrue(open[0], "opens it");
		assertEquals(com.k8bas.skyblockutility.ui.render.IconShapes.Icon.CHEVRON_UP, button.chevron(), "open: pointing up");
		assertTrue(button.activate(), "Space or Enter");
		assertFalse(open[0], "closes it");
		assertTrue(button.navKey(com.k8bas.skyblockutility.ui.widget.NavKey.LEFT, false));
		assertFalse(open[0], "Left leaves a closed list closed");
		assertTrue(button.navKey(com.k8bas.skyblockutility.ui.widget.NavKey.RIGHT, false));
		assertTrue(open[0], "Right opens it");
		assertTrue(button.navKey(com.k8bas.skyblockutility.ui.widget.NavKey.RIGHT, false));
		assertTrue(open[0], "Right leaves an open list open");
		assertTrue(button.navKey(com.k8bas.skyblockutility.ui.widget.NavKey.LEFT, false));
		assertFalse(open[0], "Left closes it");
		assertEquals(4, clicks.size(), "a click sound each time it opens or closes");
		assertFalse(button.press(12, 12, 1), "not the right button");
		button.setEnabled(false);
		assertFalse(button.activate(), "disabled: nothing");
		assertFalse(open[0]);
	}

	/** The button counts the items on out of all. */
	@Test
	void theButtonCountsTheLinesOn() {
		Checklist lines = lines();
		assertEquals("6/10", lines.summary(), "a fresh config");
		item(lines, "to_max").binding().set(true);
		assertEquals("7/10", lines.summary());
		item(lines, "kills").binding().set(false);
		item(lines, "family").binding().set(false);
		assertEquals("5/10", lines.summary());
	}

	/** REQ-UI-08: the item labels are searched; a query in an item's label opens the list, one only in the card's text does not. */
	@Test
	void itsItemLabelsAreSearched() {
		Checklist lines = lines();
		assertEquals(List.of("bestiary.lines"), SearchIndex.of(cards).search("Session kills").hits().stream().map(SearchIndex.Entry::id)
				.filter(id -> id.startsWith("bestiary.line")).toList(), "the checklist is found by an item's label");
		assertTrue(lines.matches(SearchIndex.normalize("  SESSION kills ")));
		assertTrue(lines.matches("to max"));
		assertFalse(lines.matches(SearchIndex.normalize("Lines shown")), "only the card's own title");
		assertFalse(lines.matches(""), "no query opens nothing");
	}

	@Test
	void aClosedChecklistLaysOutNoItemRows() {
		assertEquals(List.of(), checkRows(page(0F)));
	}

	/** Open: one row per item under the card, indented as a rule's fields; the rows below move down by the list's height. */
	@Test
	void anOpenChecklistListsItsItemsUnderItsCard() {
		ConfigLayout.Page closed = page(0F);
		ConfigLayout.Page open = page(1F);
		List<ConfigLayout.CheckRow> rows = checkRows(open);
		assertEquals(lines().items(), rows.stream().map(ConfigLayout.CheckRow::item).toList(), "one row per item, in order");
		ConfigLayout.OptionRow card = optionRow(open, "bestiary.lines");
		int start = card.y() + ConfigLayout.CARD + ConfigLayout.CARD_GAP;
		for (int i = 0; i < rows.size(); i++) {
			assertEquals(start + i * ConfigLayout.CHECK_ROW, rows.get(i).y(), "row " + i);
			assertEquals(Integer.MAX_VALUE, rows.get(i).clipBottom(), "open: nothing cut");
		}
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		ConfigLayout.Rect cardRect = ConfigLayout.row(frame, 0, card);
		ConfigLayout.Rect rowRect = ConfigLayout.row(frame, 0, rows.get(0));
		assertEquals(cardRect.x() + ConfigLayout.RULE_INDENT, rowRect.x(), "indented as a rule's fields");
		assertEquals(cardRect.right(), rowRect.right());
		assertEquals(ConfigLayout.CHECK_ROW, rowRect.h());
		int height = 10 * ConfigLayout.CHECK_ROW + ConfigLayout.CARD_GAP;
		assertEquals(optionRow(closed, next()).y() + height, optionRow(open, next()).y(), "the next card moves down by the list's height");
		assertEquals(closed.height() + height, open.height());
	}

	/** REQ-UI-52 as for a rule card: half open, the rows past the eased height are left out and the last is cut there. */
	@Test
	void aHalfOpenChecklistIsCutAtItsEasedHeight() {
		ConfigLayout.Page closed = page(0F);
		ConfigLayout.Page partly = page(0.3F);
		int start = optionRow(partly, "bestiary.lines").y() + ConfigLayout.CARD + ConfigLayout.CARD_GAP;
		int limit = start + Math.round(0.3F * (10 * ConfigLayout.CHECK_ROW + ConfigLayout.CARD_GAP));
		List<ConfigLayout.CheckRow> rows = checkRows(partly);
		assertEquals(4, rows.size(), "the rows starting above " + limit + ": " + rows);
		for (ConfigLayout.CheckRow row : rows) {
			assertTrue(row.y() < limit, row + " shows in part at least");
			assertEquals(limit, row.clipBottom(), row + " is cut at the eased height");
		}
		assertEquals(optionRow(closed, next()).y() + (limit - start), optionRow(partly, next()).y(), "the next card follows the eased height");
	}

	/**
	 * Placed: each row is a widget under the button; the rows are keyboard stops right after the button, in order (REQ-UI-18);
	 * a click on a row flips its item and Space or Enter flips it back; with the feature off they are dimmed and still work.
	 */
	@Test
	void theRowsAreKeyboardStopsAfterTheButtonAndFlipTheirItem() {
		List<Integer> clicks = new ArrayList<>();
		ConfigPage page = page(clicks);
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		page.layout(frame, 0, WIDTH, filter(1F));
		Checklist lines = lines();
		List<Widget> order = new ArrayList<>();
		page.focusOrder(order);
		int button = order.indexOf(page.control(lines));
		assertTrue(button >= 0 && page.control(lines) instanceof ChecklistButton stop && stop.focusable(), "the button is a stop");
		List<Widget> expected = lines.items().stream().map(item -> (Widget) page.checkRow(item)).toList();
		assertEquals(expected, order.subList(button + 1, Math.min(order.size(), button + 1 + expected.size())), "then the rows, in order");

		CheckRowWidget toMax = page.checkRow(item(lines, "to_max"));
		assertNotNull(toMax);
		assertTrue(toMax.isDimmed() && toMax.isEnabled(), "the tracker is OFF: dimmed, still editable (REQ-UI-05)");
		assertTrue(toMax.focusable());
		assertTrue(toMax.press(toMax.x() + toMax.width() - 4, toMax.y() + 2, 0), "a click anywhere on the row");
		toMax.release(toMax.x() + toMax.width() - 4, toMax.y() + 2);
		assertTrue(config.lineToMax, "flipped To max on");
		assertTrue(toMax.activate(), "Space or Enter");
		assertFalse(config.lineToMax, "and off again");
		assertEquals(2, clicks.size(), "a click sound each time");
		assertFalse(toMax.press(toMax.x() + 4, toMax.y() + 2, 1), "not the right button");
		assertEquals(item(lines, "to_max").text().description(), toMax.tooltipAt(toMax.x() + 4, toMax.y() + 2), "its tooltip is the item's description");
	}

	/** REQ-UI-52: half open, the last row placed takes input only above the list's eased height, where it shows. */
	@Test
	void aHalfOpenChecklistsRowTakesInputOnlyWhereItShows() {
		ConfigPage page = page(new ArrayList<>());
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		ConfigLayout.Page laid = page.layout(frame, 0, WIDTH, filter(0.3F));
		ConfigLayout.CheckRow last = checkRows(laid).get(checkRows(laid).size() - 1);
		CheckRowWidget row = page.checkRow(last.item());
		int cut = frame.content().y() + last.clipBottom();
		assertTrue(row.y() < cut && cut < row.y() + row.height(), "the row is cut: " + row.y() + " < " + cut);
		assertTrue(row.inClip(row.x() + 20, cut - 1), "above the cut");
		assertFalse(row.inClip(row.x() + 20, cut + 1), "below the cut, where it does not show");
	}

	/** The page with the real checklist button and plain focusable widgets for the other controls. */
	private ConfigPage page(List<Integer> clicks) {
		return new ConfigPage(Category.ODDS_TRACKERS, cards, option -> option instanceof Checklist list
				? new ChecklistButton(list, () -> { }, () -> true, () -> { })
				: new Widget() {
					@Override
					public boolean focusable() {
						return true;
					}

					@Override
					public void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
					}
				}, rule -> { }, () -> "", () -> clicks.add(1));
	}
}
