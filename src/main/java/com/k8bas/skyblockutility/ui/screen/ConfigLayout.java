package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.TooltipLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The settings screen's geometry (REQ-UI-01): one pass that both drawing and hit-testing use, so a
 * click lands on the control that is drawn there (REQ-UI-03). It reruns every frame, so a resize or a
 * GUI-scale change is simply the next layout (EC-UI-01). Clicks reach the widgets placed from these
 * rectangles, so there is no second, click-only geometry. Every size is held to the window: the panel
 * keeps 4 px free, and parts that do not fit get smaller, never negative (REQ-UI-02). Pure arithmetic;
 * written from the spec, no AlpakaAddons code (REQ-UI-24).
 */
public final class ConfigLayout {
	public static final int MARGIN = 4;
	public static final int MIN_WIDTH = 480;
	public static final int MAX_WIDTH = 660;
	public static final int MIN_HEIGHT = 340;
	public static final int MAX_HEIGHT = 440;
	public static final int HEADER = 38;
	public static final int SIDEBAR = 160;
	public static final int CARD = 44;
	public static final int CARD_GAP = 6;
	public static final int SECTION = 22;
	public static final int SECTION_GAP = 8;
	public static final int PAD = 10;
	public static final int SCROLLBAR = 6;
	public static final int TAB = 22;
	public static final int TAB_GAP = 2;
	public static final int SEARCH_WIDTH = 140;
	public static final int SEARCH_HEIGHT = 16;
	public static final int CONTROL_HEIGHT = 16;
	/** The category description shows at most this many lines. */
	public static final int DESCRIPTION_LINES = 2;

	private ConfigLayout() {
	}

	/** A rectangle in GUI pixels; width and height are never negative. */
	public record Rect(int x, int y, int w, int h) {
		public Rect {
			w = Math.max(0, w);
			h = Math.max(0, h);
		}

		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}

		public boolean contains(double px, double py) {
			return px >= x && py >= y && px < right() && py < bottom();
		}
	}

	public record Frame(Rect panel, Rect header, Rect sidebar, Rect content, Rect search) {
	}

	/** One row of a category's page, at {@code y} from the page's top. */
	public sealed interface Row permits Heading, Section, OptionRow {
		int y();

		int height();
	}

	/** The category's name and description, at the top; {@code cut} when the description did not fit (its tooltip has it all). */
	public record Heading(Category category, List<String> description, boolean cut, int y, int height) implements Row {
	}

	/** Text wrapped to at most a few lines; when more was needed, the last line ends in "..." and {@code cut} is set. */
	public record Lines(List<String> lines, boolean cut) {
	}

	/** Wraps text to {@code maxLines} lines of {@code width}, cutting the last one with "..." if it does not all fit (REQ-UI-07). */
	public static Lines fitLines(String text, int width, int maxLines, ToIntFunction<String> measure) {
		if (maxLines <= 0) {
			return new Lines(List.of(), !text.isBlank());
		}
		List<String> all = TooltipLayout.wrap(text, Math.max(1, width), measure);
		if (all.size() <= maxLines) {
			return new Lines(List.copyOf(all), false);
		}
		List<String> kept = new ArrayList<>(all.subList(0, maxLines));
		kept.set(maxLines - 1, Ellipsis.fit(String.join(" ", all.subList(maxLines - 1, all.size())), width, measure));
		return new Lines(List.copyOf(kept), true);
	}

	/** A feature's (or a General section's) header. */
	public record Section(Card card, int y) implements Row {
		@Override
		public int height() {
			return SECTION;
		}
	}

	/** One option's card; {@code featureToggle} for the feature's own switch, which a click anywhere on it flips. */
	public record OptionRow(Card card, Option option, boolean featureToggle, int y) implements Row {
		@Override
		public int height() {
			return CARD;
		}
	}

	public record Page(Category category, List<Row> rows, int height) {
	}

	public static Frame frame(int guiWidth, int guiHeight) {
		int width = Math.min(clamp(Math.round(guiWidth * 0.70F), MIN_WIDTH, MAX_WIDTH), guiWidth - 2 * MARGIN);
		int height = Math.min(clamp(Math.round(guiHeight * 0.68F), MIN_HEIGHT, MAX_HEIGHT), guiHeight - 2 * MARGIN);
		Rect panel = new Rect((guiWidth - width) / 2, (guiHeight - height) / 2, width, height);
		int headerHeight = Math.min(HEADER, panel.h());
		Rect header = new Rect(panel.x(), panel.y(), panel.w(), headerHeight);
		// 160 px, unless the panel is so narrow that the content would get less than 3/5 of it.
		int sidebarWidth = Math.min(SIDEBAR, panel.w() * 2 / 5);
		Rect sidebar = new Rect(panel.x(), header.bottom(), sidebarWidth, panel.h() - headerHeight);
		Rect content = new Rect(sidebar.right(), header.bottom(), panel.w() - sidebarWidth, panel.h() - headerHeight);
		int searchWidth = Math.min(SEARCH_WIDTH, panel.w() / 3);
		int searchHeight = Math.min(SEARCH_HEIGHT, headerHeight);
		Rect search = new Rect(header.right() - PAD - searchWidth, header.y() + (headerHeight - searchHeight) / 2, searchWidth, searchHeight);
		return new Frame(panel, header, sidebar, content, search);
	}

	/** The categories that have a card, in screen order (REQ-UI-04: an empty one is hidden). */
	public static List<Category> categories(List<Card> cards) {
		List<Category> shown = new ArrayList<>();
		for (Category category : Category.values()) {
			if (cards.stream().anyMatch(card -> card.category() == category)) {
				shown.add(category);
			}
		}
		return shown;
	}

	/** The rows of one category, for a content area {@code contentWidth} wide. */
	public static Page page(List<Card> cards, Category category, int contentWidth, ToIntFunction<String> width) {
		return page(cards, category, contentWidth, width, option -> true);
	}

	/**
	 * The rows of one category showing only the options {@code shown} accepts (a search, REQ-UI-08): a
	 * section appears when at least one of its options does. A feature's switch keeps its whole-card
	 * click wherever it shows.
	 */
	public static Page page(List<Card> cards, Category category, int contentWidth, ToIntFunction<String> width, Predicate<Option> shown) {
		List<Row> rows = new ArrayList<>();
		int textWidth = Math.max(1, contentWidth - 2 * PAD - SCROLLBAR);
		Lines description = fitLines(category.description(), textWidth, DESCRIPTION_LINES, width);
		int y = PAD;
		int headingHeight = 12 + description.lines().size() * 10 + 6;
		rows.add(new Heading(category, description.lines(), description.cut(), y, headingHeight));
		y += headingHeight;
		for (Card card : cards) {
			if (card.category() != category || card.all().stream().noneMatch(shown)) {
				continue;
			}
			rows.add(new Section(card, y));
			y += SECTION;
			for (Option option : card.all()) {
				if (!shown.test(option)) {
					continue;
				}
				rows.add(new OptionRow(card, option, option == card.toggle(), y));
				y += CARD + CARD_GAP;
			}
			y += SECTION_GAP - CARD_GAP;
		}
		Row last = rows.get(rows.size() - 1);
		return new Page(category, List.copyOf(rows), last.y() + last.height() + PAD);
	}

	/** A row's rectangle on screen, the page scrolled by {@code scroll}. */
	public static Rect row(Frame frame, int scroll, Row row) {
		Rect content = frame.content();
		return new Rect(content.x() + PAD, content.y() + row.y() - scroll, content.w() - 2 * PAD - SCROLLBAR, row.height());
	}

	public static Rect card(Frame frame, int scroll, OptionRow row) {
		return row(frame, scroll, row);
	}

	/**
	 * The control on the right of a card: a switch, dropdown, key button or colour swatch, or a slider's track (its
	 * value label sits in the {@link #SLIDER_LABEL} px to the track's right, which a press does not reach).
	 */
	public static Rect control(Rect card, Option option) {
		int width;
		int height = CONTROL_HEIGHT;
		int right = card.right() - PAD;
		if (option instanceof Toggle) {
			width = 22;
			height = 12;
		} else if (option instanceof IntSlider) {
			// A narrow card gives up track length first, so the title keeps some room.
			width = Math.max(30, Math.min(80, card.w() - 2 * PAD - SLIDER_LABEL - 60));
			right -= SLIDER_LABEL;
		} else if (option instanceof Choice<?>) {
			width = 120;
		} else if (option instanceof ColorOption) {
			width = 24;
			height = 14;
		} else {
			width = 100;
		}
		width = Math.min(width, Math.max(0, card.w() / 2 - PAD));
		return new Rect(right - width, card.y() + (card.h() - height) / 2, width, height);
	}

	/** Room for a slider's value ("Unlimited", "128 blocks") right of its track. */
	public static final int SLIDER_LABEL = 62;

	/** Where a card's title, description and status go: from its left edge to just before the control. */
	public static Rect text(Rect card, Option option) {
		Rect control = control(card, option);
		return new Rect(card.x() + PAD, card.y(), control.x() - 8 - (card.x() + PAD), card.h());
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
