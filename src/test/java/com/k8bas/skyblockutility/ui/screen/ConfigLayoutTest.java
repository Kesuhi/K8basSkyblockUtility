package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Option;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-01, REQ-UI-02, REQ-UI-03 (T2.4a): the settings screen's geometry, shared by drawing and hit-testing. */
class ConfigLayoutTest {
	private static final ToIntFunction<String> WIDTH = text -> text.length() * 6;

	private static List<Card> catalog() {
		return OptionCatalog.build(new GeneralConfig(), new MobHighlighterConfig(), new NpcSearchConfig(), enabled -> { }, enabled -> { });
	}

	/**
	 * AC-UI-43 [A] (REQ-UI-42, T3.32), a guard: on every category page at several GUI sizes all slider tracks share their start
	 * and end x and their value labels their left x (the track's end + 6 GUI px, where {@code Slider.draw} puts it); moving a
	 * slider through its range moves no track; and a slider on an indented card (a rule's field) gets the same track.
	 */
	@Test
	void sliderTracksAndValuesShareTheirColumn() {
		List<Card> cards = catalog();
		int sliders = 0;
		// Every GUI width from the smallest up, so the widths where a track is shorter than its longest are crossed.
		java.util.List<int[]> sizes = new java.util.ArrayList<>();
		for (int width = 320; width <= 960; width += 8) {
			sizes.add(new int[] {width, 480});
		}
		for (int[] gui : sizes) {
			ConfigLayout.Frame frame = ConfigLayout.frame(gui[0], gui[1]);
			for (Category category : ConfigLayout.categories(cards)) {
				ConfigLayout.Page page = ConfigLayout.page(cards, category, frame.content().w(), WIDTH);
				Integer left = null;
				Integer right = null;
				for (ConfigLayout.Row row : page.rows()) {
					if (!(row instanceof ConfigLayout.OptionRow option) || !(option.option() instanceof com.k8bas.skyblockutility.ui.option.IntSlider slider)) {
						continue;
					}
					sliders++;
					String what = gui[0] + "x" + gui[1] + " " + category + " " + slider.id();
					ConfigLayout.Rect track = ConfigLayout.control(ConfigLayout.card(frame, 0, option), slider, option.indent());
					left = left == null ? track.x() : left;
					right = right == null ? track.right() : right;
					assertEquals(left, track.x(), what + ": the track starts where the page's others do");
					assertEquals(right, track.right(), what + ": and ends there too");
					// Where the slider draws its value: left-aligned 6 GUI px after the track's end.
					com.k8bas.skyblockutility.ui.widget.Slider widget = new com.k8bas.skyblockutility.ui.widget.Slider(
							com.k8bas.skyblockutility.ui.widget.SliderModel.ofInt(slider.min(), slider.max(), slider.step()), () -> 0L, v -> { }, () -> { },
							v -> "", () -> { });
					widget.setBounds(track.x(), track.y(), track.w(), track.h());
					assertEquals(right + 6, widget.labelX(), what + ": its value 6 GUI px after the track's end");
					// Through its range: the layout does not move.
					int stored = slider.binding().get();
					for (int value : new int[] {slider.min(), slider.max()}) {
						slider.binding().set(value);
						ConfigLayout.Page again = ConfigLayout.page(cards, category, frame.content().w(), WIDTH);
						ConfigLayout.OptionRow same = (ConfigLayout.OptionRow) again.rows().get(page.rows().indexOf(row));
						assertEquals(track, ConfigLayout.control(ConfigLayout.card(frame, 0, same), slider, same.indent()), what + " at " + value);
					}
					slider.binding().set(stored);
					// On a card indented as a rule's field, the same track: its column is measured from the content's right edge.
					ConfigLayout.Rect full = ConfigLayout.card(frame, 0, option);
					ConfigLayout.Rect indented = new ConfigLayout.Rect(full.x() + 24, full.y(), full.w() - 24, full.h());
					assertEquals(track, ConfigLayout.control(indented, slider, option.indent() + 24), what + ": an indented card's track");
					// Its title and description end 8 GUI px before that track, where the card draws them.
					assertEquals(track.x() - 8, ConfigLayout.text(indented, slider, option.indent() + 24).right(), what + ": an indented card's text");
					assertEquals(track.x() - 8, ConfigLayout.text(full, slider, option.indent()).right(), what + ": its text");
				}
			}
		}
		assertTrue(sliders >= 4, "the catalogue's sliders were checked: " + sliders);
	}

	/**
	 * AC-UI-43 [A] (REQ-UI-42, T3.32): for any card width and indent, also cards narrower than the catalogue's where the
	 * track gives up length and the half-card limit applies, a slider on the indented card gets the unindented card's track.
	 */
	@Test
	void anIndentedSliderKeepsTheColumnAtAnyWidth() {
		com.k8bas.skyblockutility.ui.option.IntSlider slider = catalog().stream().flatMap(card -> card.options().stream())
				.filter(com.k8bas.skyblockutility.ui.option.IntSlider.class::isInstance).map(com.k8bas.skyblockutility.ui.option.IntSlider.class::cast)
				.findFirst().orElseThrow();
		for (int width = 60; width <= 400; width++) {
			ConfigLayout.Rect full = new ConfigLayout.Rect(10, 20, width, 40);
			ConfigLayout.Rect track = ConfigLayout.control(full, slider);
			for (int indent = 4; indent <= Math.min(80, width - 20); indent += 4) {
				ConfigLayout.Rect indented = new ConfigLayout.Rect(full.x() + indent, full.y(), full.w() - indent, full.h());
				assertEquals(track, ConfigLayout.control(indented, slider, indent), "width " + width + ", indent " + indent);
			}
		}
	}

	/**
	 * REQ-UI-18 (T2.9b): what the page scrolls into view for a control the keyboard reached: its row with the gaps
	 * around it; for a card's first option also its section title; for the page's first section from the top.
	 */
	@Test
	void theRevealSpanCoversARowAndItsSectionTitle() {
		ConfigLayout.Page page = ConfigLayout.page(catalog(), Category.GENERAL, 500, WIDTH);
		List<ConfigLayout.Row> rows = page.rows();
		int firstOption = -1;
		int laterOption = -1;
		int laterSectionOption = -1;
		int sections = 0;
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i) instanceof ConfigLayout.Section) {
				sections++;
			}
			if (rows.get(i) instanceof ConfigLayout.OptionRow) {
				if (firstOption < 0) {
					firstOption = i;
				} else if (rows.get(i - 1) instanceof ConfigLayout.OptionRow && laterOption < 0) {
					laterOption = i;
				} else if (rows.get(i - 1) instanceof ConfigLayout.Section && sections >= 2 && laterSectionOption < 0) {
					laterSectionOption = i;
				}
			}
		}
		assertTrue(firstOption > 0 && laterOption > 0 && laterSectionOption > 0, "the General page has those rows");

		int[] first = ConfigLayout.revealSpan(page, rows.get(firstOption));
		assertEquals(0, first[0], "the page's first section: from the top, heading and all");
		assertEquals(rows.get(firstOption).y() + rows.get(firstOption).height() + ConfigLayout.CARD_GAP, first[1]);

		ConfigLayout.Row later = rows.get(laterOption);
		assertArrayEquals(new int[] {later.y() - ConfigLayout.CARD_GAP, later.y() + later.height() + ConfigLayout.CARD_GAP},
				ConfigLayout.revealSpan(page, later));

		ConfigLayout.Row titled = rows.get(laterSectionOption);
		int[] span = ConfigLayout.revealSpan(page, titled);
		assertEquals(rows.get(laterSectionOption - 1).y(), span[0], "a later card's first option brings its section title along");
		assertEquals(titled.y() + titled.height() + ConfigLayout.CARD_GAP, span[1]);
	}

	/** AC-UI-01: at 1920x1080 and GUI scale 1, a centred 660x440 panel, 38 px header, 160 px sidebar. */
	@Test
	void theLargeWindowGetsTheFullSizePanel() {
		ConfigLayout.Frame frame = ConfigLayout.frame(1920, 1080);
		assertEquals(new ConfigLayout.Rect(630, 320, 660, 440), frame.panel());
		assertEquals(38, frame.header().h());
		assertEquals(660, frame.header().w());
		assertEquals(160, frame.sidebar().w());
		assertEquals(frame.panel().bottom(), frame.sidebar().bottom());
		assertEquals(frame.sidebar().right(), frame.content().x());
		assertEquals(frame.panel().right(), frame.content().right());
		assertTrue(frame.search().right() <= frame.header().right() && frame.search().x() > frame.header().x() + frame.header().w() / 2,
				"the search box at the header's right end");
	}

	@Test
	void thePanelFollowsThePreferredShareInsideItsClamp() {
		// 70 % x 68 % of 800x600 = 560x408.
		assertEquals(new ConfigLayout.Rect(120, 96, 560, 408), ConfigLayout.frame(800, 600).panel());
		// Below the minimum the window wins, with 4 px kept free (REQ-UI-02 over REQ-UI-01).
		assertEquals(new ConfigLayout.Rect(4, 4, 312, 232), ConfigLayout.frame(320, 240).panel());
	}

	/** AC-UI-02 (geometry): at every listed size the panel keeps 4 px free and its parts stay inside it. */
	@Test
	void everyRegionStaysInsideThePanelAtEverySize() {
		int[][] windows = {{854, 480}, {1920, 1080}};
		for (int[] window : windows) {
			for (int scale = 1; scale <= 4; scale++) {
				int w = window[0] / scale;
				int h = window[1] / scale;
				if (w < 320 || h < 240) {
					continue;
				}
				ConfigLayout.Frame frame = ConfigLayout.frame(w, h);
				String where = w + "x" + h;
				ConfigLayout.Rect panel = frame.panel();
				assertTrue(panel.x() >= 4 && panel.y() >= 4 && panel.right() <= w - 4 && panel.bottom() <= h - 4, where + ": " + panel);
				for (ConfigLayout.Rect part : List.of(frame.header(), frame.sidebar(), frame.content(), frame.search())) {
					assertTrue(part.x() >= panel.x() && part.y() >= panel.y() && part.right() <= panel.right() && part.bottom() <= panel.bottom(),
							where + ": " + part + " in " + panel);
				}
				ConfigLayout.Page page = ConfigLayout.page(catalog(), Category.HIGHLIGHTS, frame.content().w(), WIDTH);
				for (ConfigLayout.Row row : page.rows()) {
					if (row instanceof ConfigLayout.OptionRow option) {
						ConfigLayout.Rect card = ConfigLayout.card(frame, 0, option);
						ConfigLayout.Rect control = ConfigLayout.control(card, option.option());
						assertTrue(card.x() >= frame.content().x() && card.right() <= frame.content().right(), where + ": card " + card);
						assertTrue(control.x() > card.x() && control.right() <= card.right() && control.y() >= card.y()
								&& control.bottom() <= card.bottom(), where + ": control " + control + " in " + card);
					}
				}
			}
		}
	}

	/** REQ-UI-01: section headers, then 44 px cards with 6 px gaps; the feature toggle comes first. */
	@Test
	void eachFeatureIsASectionOfCards() {
		List<Card> cards = catalog();
		ConfigLayout.Page page = ConfigLayout.page(cards, Category.WAYPOINTS, 500, WIDTH);
		assertTrue(page.rows().get(0) instanceof ConfigLayout.Heading);
		Card npcSearch = cards.stream().filter(card -> card.category() == Category.WAYPOINTS).findFirst().orElseThrow();
		ConfigLayout.Section section = (ConfigLayout.Section) page.rows().get(1);
		assertSame(npcSearch, section.card());
		ConfigLayout.OptionRow first = (ConfigLayout.OptionRow) page.rows().get(2);
		assertSame(npcSearch.toggle(), first.option());
		assertTrue(first.featureToggle(), "the feature's own toggle");
		ConfigLayout.OptionRow second = (ConfigLayout.OptionRow) page.rows().get(3);
		assertEquals(ConfigLayout.CARD + ConfigLayout.CARD_GAP, second.y() - first.y());
		assertEquals(npcSearch.all().size(), page.rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow o && o.card() == npcSearch).count());
		ConfigLayout.Row last = page.rows().get(page.rows().size() - 1);
		assertEquals(last.y() + last.height() + ConfigLayout.PAD, page.height());
	}

	@Test
	void aCategoryWithoutCardsIsHidden() {
		List<Category> shown = ConfigLayout.categories(catalog());
		assertEquals(List.of(Category.GENERAL, Category.HIGHLIGHTS, Category.WAYPOINTS, Category.MINING, Category.FISHING), shown);
	}

	@Test
	void longTitlesLeaveRoomForTheControl() {
		ConfigLayout.Frame frame = ConfigLayout.frame(320, 240);
		ConfigLayout.Page page = ConfigLayout.page(catalog(), Category.HIGHLIGHTS, frame.content().w(), WIDTH);
		for (ConfigLayout.Row row : page.rows()) {
			if (row instanceof ConfigLayout.OptionRow option) {
				ConfigLayout.Rect card = ConfigLayout.card(frame, 0, option);
				Option o = option.option();
				ConfigLayout.Rect text = ConfigLayout.text(card, o);
				assertTrue(text.w() > 0 && text.right() < ConfigLayout.control(card, o).x(), o.id() + ": the text ends before the control");
			}
		}
	}

	/** REQ-UI-07: at most 2 lines; when more was needed, the second ends in "..." and the text is marked cut (its tooltip has it all). */
	@Test
	void descriptionsFitTwoLinesOrAreCutWithDots() {
		ConfigLayout.Lines fits = ConfigLayout.fitLines("Short enough.", 120, 2, WIDTH);
		assertEquals(List.of("Short enough."), fits.lines());
		assertFalse(fits.cut());
		String long_ = "A rather long description of an option that needs far more than two lines in a narrow card to be read whole.";
		ConfigLayout.Lines cut = ConfigLayout.fitLines(long_, 120, 2, WIDTH);
		assertEquals(2, cut.lines().size());
		assertTrue(cut.cut());
		assertTrue(cut.lines().get(1).endsWith("..."), cut.lines().get(1));
		cut.lines().forEach(line -> assertTrue(WIDTH.applyAsInt(line) <= 120, line));
		assertTrue(long_.startsWith(cut.lines().get(0)));
	}

	/** At the narrowest window the Waypoints description no longer fits its 2 lines: it is cut with dots. */
	@Test
	void aCategoryDescriptionThatDoesNotFitIsCut() {
		ConfigLayout.Frame frame = ConfigLayout.frame(320, 240);
		ConfigLayout.Heading heading = (ConfigLayout.Heading) ConfigLayout.page(catalog(), Category.WAYPOINTS, frame.content().w(), WIDTH).rows().get(0);
		assertTrue(heading.cut());
		assertEquals(2, heading.description().size());
		assertTrue(heading.description().get(1).endsWith("..."));
		ConfigLayout.Heading wide = (ConfigLayout.Heading) ConfigLayout.page(catalog(), Category.WAYPOINTS, 500, WIDTH).rows().get(0);
		assertFalse(wide.cut());
	}
}
