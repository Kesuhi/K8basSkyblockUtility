package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.widget.Widget;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * T2.4a, the settings screen in a real client, opened from the title screen (EC-UI-04):
 * <ul>
 *   <li>AC-UI-01: at 1920x1080 and GUI scale 1, a centred 660x440 panel, the 38 px header with its
 *       accent line, the 160 px sidebar and 44 px cards, read from the screenshot;</li>
 *   <li>AC-UI-02: at 854x480 (scales 1, 2) and 1920x1080 (scales 1-4), scrolled to the end of the longest
 *       category, the panel keeps 4 px free and no widget can draw outside it; a resize while open
 *       keeps the category and clamps the scroll (EC-UI-01);</li>
 *   <li>AC-UI-03 [C]: clicks on a toggle card's text and on a slider's track change only that setting;</li>
 *   <li>AC-UI-04: every category opens; switching a feature off dims its sub-options;</li>
 *   <li>EC-UI-12: the wheel over the sidebar does not scroll the content; over the content it does;</li>
 *   <li>Esc closes it back to the title screen.</li>
 * </ul>
 */
public class ConfigScreenGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		try {
			context.runOnClient(client -> client.options.guiScale().set(1));
			context.setScreen(() -> new ConfigScreen(Minecraft.getInstance().gui.screen()));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(3);
			metrics(context);
			everyCategory(context);
			sizes(context);
			context.getInput().resizeWindow(1280, 960);
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
			clicks(context);
			wheel(context);
			focusAndTooltip(context);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			Screen after = context.computeOnClient(client -> client.gui.screen());
			check(after instanceof TitleScreen, "Esc goes back to the title screen: " + after);
			context.setScreen(() -> new ConfigScreen(Minecraft.getInstance().gui.screen()));
			context.waitForScreen(ConfigScreen.class);
			context.waitTicks(2);
			search(context);
			saveModel(context);
			generalCategory(context);
		} finally {
			context.runOnClient(client -> {
				ModuleManager.modules().forEach(module -> module.setEnabled(true));
				ConfigManager.general().mobScanRangeBlocks = 64;
				ConfigManager.save();
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** AC-UI-01. */
	private static void metrics(ClientGameTestContext context) {
		ConfigLayout.Frame frame = context.computeOnClient(client -> screen(client).frame());
		int[] gui = context.computeOnClient(client -> new int[] {screen(client).width, screen(client).height});
		check(frame.panel().w() == 660 && frame.panel().h() == 440, "660x440 panel: " + frame.panel());
		check(Math.abs(frame.panel().x() * 2 + 660 - gui[0]) <= 1 && Math.abs(frame.panel().y() * 2 + 440 - gui[1]) <= 1, "centred: " + frame.panel());
		Path shot = context.takeScreenshot("t2.4a-metrics-1920x1080-scale-1");
		int accent = context.computeOnClient(client -> Theme.current().accent()) & 0xFFFFFF;
		LOGGER.info("config screen pixels: accent line {}, above {}, below {}, expected {}", Integer.toHexString(pixel(context, shot, frame.header().x() + 300, frame.header().bottom() - 1)),
				Integer.toHexString(pixel(context, shot, frame.header().x() + 300, frame.header().bottom() - 2)),
				Integer.toHexString(pixel(context, shot, frame.header().x() + 300, frame.header().bottom())), Integer.toHexString(accent));
		check(pixel(context, shot, frame.header().x() + 300, frame.header().bottom() - 1) == accent, "the 1 px accent line under the header");
		check(pixel(context, shot, frame.header().x() + 300, frame.header().bottom() - 2) == (Theme.HEADER & 0xFFFFFF), "the header above it");
		check(pixel(context, shot, frame.header().x() + 300, frame.header().bottom()) != accent, "and only 1 px");
		check(pixel(context, shot, frame.sidebar().right() - 3, frame.sidebar().bottom() - 3) == (Theme.SIDEBAR & 0xFFFFFF), "the 160 px sidebar");
		check(pixel(context, shot, frame.sidebar().right() + 1, frame.sidebar().bottom() - 3) == (Theme.PANEL & 0xFFFFFF), "and the content right of it");
		ConfigLayout.OptionRow first = context.computeOnClient(client -> firstOptionRow(screen(client)));
		ConfigLayout.Rect card = ConfigLayout.card(frame, 0, first);
		check(card.h() == ConfigLayout.CARD, "44 px cards");
		check(pixel(context, shot, card.x() + card.w() / 2, card.y() + 1) == (Theme.CARD & 0xFFFFFF), "a card's top row");
		check(pixel(context, shot, card.x() + card.w() / 2, card.bottom() - 2) == (Theme.CARD & 0xFFFFFF), "its bottom row");
		check(pixel(context, shot, card.x() + card.w() / 2, card.bottom() + 1) == (Theme.PANEL & 0xFFFFFF), "the gap below it");
		LOGGER.info("config screen metrics: panel {}, header {}, sidebar {}, first card {}", frame.panel(), frame.header(), frame.sidebar(), card);
	}

	/** AC-UI-04: each category opens with its cards; a feature switched off dims its sub-options. */
	private static void everyCategory(ClientGameTestContext context) {
		List<Category> categories = context.computeOnClient(client -> screen(client).categories());
		check(categories.equals(List.of(Category.GENERAL, Category.HIGHLIGHTS, Category.WAYPOINTS)), "the categories with cards: " + categories);
		for (Category category : categories) {
			context.runOnClient(client -> screen(client).select(category));
			context.waitTicks(2);
			context.takeScreenshot("t2.4a-category-" + category.id());
			int toggles = context.computeOnClient(client -> (int) screen(client).page().page().rows().stream()
					.filter(row -> row instanceof ConfigLayout.OptionRow option && option.featureToggle()).count());
			if (category != Category.GENERAL) {
				check(toggles >= 1, category + ": a feature card with its own toggle");
			}
		}
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		context.runOnClient(client -> ModuleManager.modules().forEach(module -> {
			if (module.id().equals("mob_highlighter")) {
				module.setEnabled(false);
			}
		}));
		context.waitTicks(2);
		boolean dimmed = context.computeOnClient(client -> {
			ConfigPage page = screen(client).page();
			return page.page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option && !option.featureToggle())
					.allMatch(row -> page.control(((ConfigLayout.OptionRow) row).option()).isDimmed());
		});
		check(dimmed, "Mob Highlighter off: its sub-options are dimmed");
		context.takeScreenshot("t2.4a-feature-off-dimmed");
		context.runOnClient(client -> ModuleManager.modules().forEach(module -> module.setEnabled(true)));
		LOGGER.info("config screen categories: {}; a feature off dims its sub-options", categories);
	}

	/** AC-UI-02 and EC-UI-01, between two log markers: nothing may be logged as an error while drawing at any size. */
	private static void sizes(ClientGameTestContext context) {
		String start = "k8bas config screen sizes start " + System.nanoTime();
		String end = "k8bas config screen sizes end " + System.nanoTime();
		LOGGER.info(start);
		int[][] cases = {{854, 480, 1}, {854, 480, 2}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}};
		context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
		StringBuilder log = new StringBuilder();
		for (int[] size : cases) {
			resize(context, size[0], size[1], size[2]);
			context.runOnClient(client -> screen(client).scrollTo(Integer.MAX_VALUE));
			context.waitTicks(2);
			String name = size[0] + "x" + size[1] + "-scale-" + size[2];
			context.takeScreenshot("t2.4a-end-of-waypoints-" + name);
			String problem = context.computeOnClient(client -> outsideThePanel(screen(client)));
			check(problem.isEmpty(), name + ": " + problem);
			int scroll = context.computeOnClient(client -> screen(client).scrollArea().scroll());
			int max = context.computeOnClient(client -> screen(client).scrollArea().maxScroll());
			check(scroll == max, name + ": scrolled to the end, " + scroll + " of " + max);
			log.append(' ').append(name).append(" scroll ").append(scroll);
		}
		// Resized while open, the category stays and the scroll is held to the new page: scrolled to the
		// end where the page overflows most, then made large (nothing left to scroll), then small again.
		resize(context, 854, 480, 2);
		context.runOnClient(client -> screen(client).scrollTo(Integer.MAX_VALUE));
		context.waitTicks(2);
		int before = context.computeOnClient(client -> screen(client).scrollArea().scroll());
		check(before > 0, "scrolled before the resize: " + before);
		resize(context, 1920, 1080, 1);
		int large = context.computeOnClient(client -> screen(client).scrollArea().scroll());
		int largeMax = context.computeOnClient(client -> screen(client).scrollArea().maxScroll());
		check(large == largeMax && large < before, "made large, the scroll is held to the page: " + large + " of " + largeMax);
		resize(context, 854, 480, 1);
		Category kept = context.computeOnClient(client -> screen(client).selected());
		int scroll = context.computeOnClient(client -> screen(client).scrollArea().scroll());
		int max = context.computeOnClient(client -> screen(client).scrollArea().maxScroll());
		check(kept == Category.WAYPOINTS && scroll <= max, "after the resizes: " + kept + ", scroll " + scroll + " of " + max);
		String problem = context.computeOnClient(client -> outsideThePanel(screen(client)));
		check(problem.isEmpty(), "after the resize: " + problem);
		context.takeScreenshot("t2.4a-resized-to-854x480");
		context.waitTicks(5);
		LOGGER.info(end);
		List<String> errors = errorsBetween(context, start, end);
		check(errors.isEmpty(), "no exception or render error logged at any size: " + errors);
		LOGGER.info("config screen sizes:{}; resized: scroll {} at 854x480 scale 2, {} of {} at 1920x1080, {} of {} back at 854x480; no errors logged",
				log, before, large, largeMax, scroll, max);
	}

	private static void resize(ClientGameTestContext context, int width, int height, int scale) {
		context.getInput().resizeWindow(width, height);
		context.runOnClient(client -> client.options.guiScale().set(scale));
		context.waitTicks(3);
	}

	/**
	 * The render thread's errors and warnings, and any exception, logged between two markers. Other threads'
	 * known noise in a test client without an account (authentication, Realms) is left out.
	 */
	private static List<String> errorsBetween(ClientGameTestContext context, String start, String end) {
		String log = "";
		for (int tries = 0; tries < 40 && !log.contains(end); tries++) {
			context.waitTick();
			log = latestLog();
		}
		check(log.contains(start) && log.contains(end), "both markers reached latest.log");
		String between = log.substring(log.indexOf(start), log.indexOf(end));
		return between.lines().filter(line -> line.contains("[Render thread/ERROR]") || line.contains("[Render thread/WARN]")
				|| line.contains("Exception") && !line.contains("MinecraftClientHttpException") && !line.contains("buildHttpException")).toList();
	}

	private static String latestLog() {
		try {
			return java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(Path.of("logs", "latest.log")));
		} catch (IOException e) {
			throw new AssertionError("cannot read latest.log", e);
		}
	}

	/** Every widget's drawable part (its bounds, cut to its clip region) lies in the panel, which keeps 4 px free. */
	private static String outsideThePanel(ConfigScreen screen) {
		ConfigLayout.Rect panel = screen.frame().panel();
		List<String> problems = new ArrayList<>();
		if (panel.x() < 4 || panel.y() < 4 || panel.right() > screen.width - 4 || panel.bottom() > screen.height - 4) {
			problems.add("panel " + panel + " in " + screen.width + "x" + screen.height);
		}
		for (Widget widget : screen.allWidgets()) {
			int x0 = widget.x();
			int y0 = widget.y();
			int x1 = widget.x() + widget.width();
			int y1 = widget.y() + widget.height();
			ClipRect clip = widget.clip();
			if (clip != null) {
				x0 = Math.max(x0, clip.x());
				y0 = Math.max(y0, clip.y());
				x1 = Math.min(x1, clip.right());
				y1 = Math.min(y1, clip.bottom());
			}
			if (x1 <= x0 || y1 <= y0) {
				continue;
			}
			if (x0 < panel.x() || y0 < panel.y() || x1 > panel.right() || y1 > panel.bottom()) {
				problems.add(widget.getClass().getSimpleName() + " " + x0 + "," + y0 + "-" + x1 + "," + y1);
			}
		}
		return String.join("; ", problems);
	}

	/** AC-UI-03 [C]. */
	private static void clicks(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		context.runOnClient(client -> screen(client).scrollTo(0));
		context.waitTicks(2);
		String before = context.computeOnClient(client -> settings());
		// The toggle card's title, far from its switch, flips the feature.
		int[] title = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = optionRow(screen, option -> option instanceof Toggle);
			ConfigLayout.Rect card = ConfigLayout.card(screen.frame(), 0, row);
			return new int[] {card.x() + 20, card.y() + 10};
		});
		click(context, title);
		String afterToggle = context.computeOnClient(client -> settings());
		check(afterToggle.contains("mob_highlighter=false") && afterToggle.replace("mob_highlighter=false", "mob_highlighter=true").equals(before),
				"a click on the toggle card's title switches the feature off and nothing else: " + afterToggle);
		click(context, title);
		check(context.computeOnClient(client -> settings()).equals(before), "and back on");
		// Three quarters along the range slider's track.
		int[] track = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = optionRow(screen, option -> option instanceof IntSlider);
			ConfigLayout.Rect control = ConfigLayout.control(ConfigLayout.card(screen.frame(), 0, row), row.option());
			return new int[] {control.x() + control.w() * 3 / 4, control.y() + control.h() / 2};
		});
		click(context, track);
		int range = context.computeOnClient(client -> ConfigManager.general().mobScanRangeBlocks);
		String afterSlider = context.computeOnClient(client -> settings());
		check(range > 64 && afterSlider.equals(before.replace("range=64", "range=" + range)),
				"a click on the slider's track sets only the range: " + range + ", " + afterSlider);
		LOGGER.info("config screen clicks: the toggle card's title switched Mob Highlighter off and on, the slider track set the range to {}", range);
	}

	/** EC-UI-12. */
	private static void wheel(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
		context.runOnClient(client -> screen(client).scrollTo(0));
		context.waitTicks(2);
		int[] sidebar = context.computeOnClient(client -> {
			ConfigLayout.Rect rect = screen(client).frame().sidebar();
			return new int[] {rect.x() + rect.w() / 2, rect.bottom() - 10};
		});
		move(context, sidebar);
		context.getInput().scroll(-1.0);
		context.waitTick();
		check(context.computeOnClient(client -> screen(client).scrollArea().scroll()) == 0, "the wheel over the sidebar leaves the content");
		int[] content = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.Rect rect = screen.frame().content();
			// Over the heading, where no control takes the wheel.
			return new int[] {rect.x() + 20, rect.y() + 12};
		});
		move(context, content);
		context.getInput().scroll(-1.0);
		context.waitTick();
		int scrolled = context.computeOnClient(client -> screen(client).scrollArea().scroll());
		check(scrolled > 0, "the wheel over the content scrolls it: " + scrolled);
		LOGGER.info("config screen wheel: sidebar left the content at 0, content scrolled to {}", scrolled);
	}

	/** EC-UI-01: a focused field keeps the keyboard through a resize; REQ-UI-19: a control shows its card's tooltip. */
	private static void focusAndTooltip(ClientGameTestContext context) {
		int[] box = context.computeOnClient(client -> {
			ConfigLayout.Rect search = screen(client).frame().search();
			return new int[] {search.x() + search.w() / 2, search.y() + search.h() / 2};
		});
		click(context, box);
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == screen(client).search()), "a click focuses the search box");
		resize(context, 1280, 720, 2);
		boolean kept = context.computeOnClient(client -> screen(client).keyboardFocus() == screen(client).search());
		boolean guard = context.computeOnClient(client -> screen(client).getFocused() instanceof net.minecraft.client.gui.components.EditBox);
		check(kept && guard, "after a resize the field keeps the keyboard (" + kept + ") and the game sees a text box focused (" + guard + ")");
		context.getInput().typeChars("ab");
		context.waitTick();
		String typed = context.computeOnClient(client -> screen(client).search().model().text());
		check("ab".equals(typed), "typing still reaches it: " + typed);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();
		context.runOnClient(client -> screen(client).search().model().setText(""));

		// Over NPC Search's own switch, the card's AMBER tooltip shows.
		context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
		context.runOnClient(client -> screen(client).scrollTo(0));
		context.waitTicks(2);
		int[] toggle = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = optionRow(screen, option -> option instanceof Toggle);
			ConfigLayout.Rect control = ConfigLayout.control(ConfigLayout.card(screen.frame(), 0, row), row.option());
			return new int[] {control.x() + control.w() / 2, control.y() + control.h() / 2};
		});
		move(context, toggle);
		context.waitTicks(2);
		String expected = context.computeOnClient(client -> optionRow(screen(client), option -> option instanceof Toggle).option().text().tooltip());
		var shown = context.computeOnClient(client -> screen(client).tooltipShown());
		check(!expected.isBlank() && shown != null && String.join(" ", shown.lines()).startsWith(expected.substring(0, 20)),
				"hovering the switch shows its card's tooltip: " + shown);
		context.takeScreenshot("t2.4a-tooltip-over-a-switch");
		LOGGER.info("config screen focus and tooltip: the search box kept the keyboard through a resize, typed {}; the switch showed {} lines",
				typed, shown.lines().size());
	}

	/** T2.4b, AC-UI-07 [C] with real keys: Ctrl+F, typing, the category switch, nothing found, Esc, ×, and the query kept across categories. */
	private static void search(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		int[] cross = context.computeOnClient(client -> {
			ConfigLayout.Rect box = screen(client).frame().search();
			return new int[] {box.right() - ClearButton.SIZE / 2 - 2, box.y() + box.h() / 2};
		});
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_F);
		context.getInput().releaseControl();
		context.waitTick();
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == screen(client).search()), "Ctrl+F focuses the search box");
		context.getInput().typeChars("range");
		context.waitTicks(2);
		SearchView range = context.computeOnClient(client -> screen(client).view());
		Category shown = context.computeOnClient(client -> screen(client).selected());
		List<String> rows = context.computeOnClient(client -> optionIds(screen(client)));
		check(range.categories().equals(List.of(Category.HIGHLIGHTS)) && shown == Category.HIGHLIGHTS, "\"range\" leaves Highlights and switches to it: "
				+ range.categories() + ", " + shown);
		check(range.badge(Category.HIGHLIGHTS) == 1 && rows.equals(List.of("mob_highlighter.scan_range")), "one hit, the scan range: " + rows);
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == screen(client).search()), "the switch kept the box focused");
		context.takeScreenshot("t2.4b-search-range");
		// × while typing: the box empties and keeps the keyboard, so the next query can be typed at once.
		click(context, cross);
		check(context.computeOnClient(client -> screen(client).query()).isEmpty(), "× while typing empties the box");
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == screen(client).search()), "and the box keeps the keyboard");
		context.getInput().typeChars("range");
		context.waitTicks(2);

		replaceQuery(context, "  RANGE ");
		check(context.computeOnClient(client -> optionIds(screen(client))).equals(rows), "\"  RANGE \" finds the same");

		replaceQuery(context, "zzzz");
		SearchView none = context.computeOnClient(client -> screen(client).view());
		check(none.nothingFound() && none.categories().isEmpty(), "\"zzzz\" finds nothing and empties the sidebar");
		check(context.computeOnClient(client -> optionIds(screen(client))).isEmpty(), "and no card shows");
		context.takeScreenshot("t2.4b-search-nothing-found");

		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == null), "Esc leaves the box");
		check(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen, "and keeps the screen open");
		check("zzzz".equals(context.computeOnClient(client -> screen(client).query())), "with the query");

		click(context, cross);
		check(context.computeOnClient(client -> screen(client).query()).isEmpty(), "× empties the box");
		check(context.computeOnClient(client -> screen(client).view().categories().size()) == 3, "and every category is back");
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == screen(client).search()), "and the box has the keyboard again");

		// A query matching several categories survives a click on another tab.
		context.getInput().typeChars("npc");
		context.waitTicks(2);
		List<Category> withNpc = context.computeOnClient(client -> screen(client).view().categories());
		check("npc".equals(context.computeOnClient(client -> screen(client).query())), "typed into the box");
		check(withNpc.size() >= 2, "\"npc\" matches more than one category: " + withNpc);
		Category other = withNpc.get(withNpc.size() - 1) == context.computeOnClient(client -> screen(client).selected()) ? withNpc.get(0)
				: withNpc.get(withNpc.size() - 1);
		int[] tab = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			int row = screen.view().categories().indexOf(other);
			return new int[] {screen.tabs().x() + 20, screen.tabs().y() + row * (ConfigLayout.TAB + ConfigLayout.TAB_GAP) + ConfigLayout.TAB / 2};
		});
		click(context, tab);
		check(context.computeOnClient(client -> screen(client).selected()) == other, "the tab switches to " + other);
		String kept = context.computeOnClient(client -> screen(client).query());
		check("npc".equals(kept), "and the query stays: " + kept);
		click(context, cross);
		LOGGER.info("config screen search: Ctrl+F focused the box; \"range\" showed {} in {}; \"zzzz\" emptied the sidebar; Esc kept the screen; "
				+ "× cleared it; \"npc\" in {} survived a switch to {}", rows, shown, withNpc, other);
		searchKeepsTheKeysInAWorld(context);
	}

	/** AC-UI-05 [C]: in a world, with the search box focused, E and the bound "Open settings" key open nothing. */
	private static void searchKeepsTheKeysInAWorld(ClientGameTestContext context) {
		context.setScreen(() -> null);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> {
				SettingsKeybind.OPEN_SETTINGS_KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_KP_7));
				KeyMapping.resetMapping();
			});
			try {
				context.setScreen(() -> new ConfigScreen(null));
				context.waitForScreen(ConfigScreen.class);
				context.waitTicks(2);
				ConfigScreen opened = context.computeOnClient(client -> screen(client));
				int[] box = context.computeOnClient(client -> {
					ConfigLayout.Rect search = screen(client).frame().search();
					return new int[] {search.x() + 20, search.y() + search.h() / 2};
				});
				// The control: with nothing focused, F3 on this screen toggles the debug overlay.
				boolean overlayBefore = context.computeOnClient(client -> client.debugEntries.isOverlayVisible());
				context.getInput().pressKey(GLFW.GLFW_KEY_F3);
				context.waitTicks(2);
				check(context.computeOnClient(client -> client.debugEntries.isOverlayVisible()) != overlayBefore, "control: F3 toggles the overlay");
				context.getInput().pressKey(GLFW.GLFW_KEY_F3);
				context.waitTicks(2);
				click(context, box);
				context.getInput().pressKey(GLFW.GLFW_KEY_F3);
				context.waitTicks(2);
				check(context.computeOnClient(client -> client.debugEntries.isOverlayVisible()) == overlayBefore, "the focused box takes F3");
				context.getInput().pressKey(options -> options.keyInventory);
				context.getInput().typeChars("e");
				context.getInput().pressKey(SettingsKeybind.OPEN_SETTINGS_KEY);
				context.waitTicks(3);
				Screen after = context.computeOnClient(client -> client.gui.screen());
				check(after == opened, "E and the settings key open nothing while the box is focused: " + after);
				String typed = context.computeOnClient(client -> screen(client).search().model().text());
				check(typed.startsWith("e"), "E went into the box: " + typed);
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTick();
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitFor(client -> client.gui.screen() == null, 40);
				LOGGER.info("config screen in a world: E and the settings key went to the search box ({}), Esc left it, Esc closed", typed);
			} finally {
				context.runOnClient(client -> {
					SettingsKeybind.OPEN_SETTINGS_KEY.setKey(InputConstants.UNKNOWN);
					KeyMapping.resetMapping();
				});
			}
		}
	}

	/** T2.4c, AC-UI-14 with real input, counted on the config's save path. */
	private static void saveModel(ClientGameTestContext context) {
		context.setScreen(() -> new ConfigScreen(Minecraft.getInstance().gui.screen()));
		context.waitForScreen(ConfigScreen.class);
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		context.waitTicks(2);
		// A feature switch applies at once.
		int[] title = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.Rect card = ConfigLayout.card(screen.frame(), 0, optionRow(screen, option -> option instanceof Toggle));
			return new int[] {card.x() + 20, card.y() + 10};
		});
		click(context, title);
		check(!context.computeOnClient(client -> mobHighlighterOn()), "the switch turned Mob Highlighter off at once");
		click(context, title);
		check(context.computeOnClient(client -> mobHighlighterOn()), "and on again");

		// A drag along the whole track: no write while it moves, one on release.
		int[][] track = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = optionRow(screen, option -> option instanceof IntSlider);
			ConfigLayout.Rect control = ConfigLayout.control(ConfigLayout.card(screen.frame(), 0, row), row.option());
			return new int[][] {{control.x(), control.y() + control.h() / 2}, {control.right() - 1, control.y() + control.h() / 2}};
		});
		move(context, track[0]);
		int beforeDrag = context.computeOnClient(client -> ConfigManager.saveRequests());
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (int step = 1; step <= 60; step++) {
			int x = track[0][0] + (track[1][0] - track[0][0]) * step / 60;
			move(context, new int[] {x, track[0][1]});
			seen.add(context.computeOnClient(client -> ConfigManager.general().mobScanRangeBlocks));
		}
		int duringDrag = context.computeOnClient(client -> ConfigManager.saveRequests()) - beforeDrag;
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		int afterRelease = context.computeOnClient(client -> ConfigManager.saveRequests()) - beforeDrag;
		check(seen.size() >= 50, "the drag crossed " + seen.size() + " values");
		check(duringDrag == 0 && afterRelease == 1, "no write during the drag, one on release: " + duringDrag + ", " + afterRelease);

		// Esc: exactly one write.
		int beforeClose = context.computeOnClient(client -> ConfigManager.saveRequests());
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		int onEsc = context.computeOnClient(client -> ConfigManager.saveRequests()) - beforeClose;
		check(onEsc == 1, "Esc writes once: " + onEsc);
		context.runOnClient(client -> ConfigManager.general().mobScanRangeBlocks = 64);

		// Replaced by another screen (EC-UI-02): exactly one write, as on Esc.
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		int beforeReplace = context.computeOnClient(client -> ConfigManager.saveRequests());
		context.setScreen(() -> new net.minecraft.client.gui.screens.TitleScreen());
		context.waitTicks(2);
		int onReplace = context.computeOnClient(client -> ConfigManager.saveRequests()) - beforeReplace;
		check(onReplace == 1, "replaced by another screen, it writes once: " + onReplace);
		LOGGER.info("config screen save model: switch applied at once; drag over {} values wrote {} during and {} on release; Esc wrote {}, "
				+ "being replaced wrote {}", seen.size(), duringDrag, afterRelease, onEsc, onReplace);
		moduleOptionReachesTheFile(context);
	}

	/**
	 * A module's sub-option changed in the screen applies at once and is in the file after the close
	 * (REQ-CFG-10); the same screen shown again after another one saves again on its own close.
	 */
	private static void moduleOptionReachesTheFile(ClientGameTestContext context) {
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		ConfigScreen opened = context.computeOnClient(client -> screen(client));
		context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
		context.waitTicks(2);
		int[] beams = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = optionRow(screen, option -> option.id().equals("npc_search.show_beams"));
			ConfigLayout.Rect control = ConfigLayout.control(ConfigLayout.card(screen.frame(), 0, row), row.option());
			return new int[] {control.x() + control.w() / 2, control.y() + control.h() / 2};
		});
		click(context, beams);
		check(!context.computeOnClient(client -> com.k8bas.skyblockutility.module.npcsearch.NpcWaypointMarkers.settings().showBeams()),
				"the waypoints drop their beams at once, with the screen still open");
		// Covered by another screen and shown again: the close after that still saves.
		context.setScreen(() -> new net.minecraft.client.gui.screens.TitleScreen());
		context.waitTicks(2);
		context.setScreen(() -> opened);
		context.waitForScreen(ConfigScreen.class);
		int beforeEsc = context.computeOnClient(client -> ConfigManager.saveRequests());
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		int onEsc = context.computeOnClient(client -> ConfigManager.saveRequests()) - beforeEsc;
		check(onEsc == 1, "shown again, its Esc still writes once: " + onEsc);
		context.runOnClient(client -> ConfigManager.flush());
		String file = configFile(context);
		check(file.replaceAll("\\s", "").contains("\"showBeams\":false"), "\"Show beacon beams\" off is in the file");
		// Back on, the same way, so later tests start from the defaults.
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
		context.waitTicks(2);
		click(context, beams);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		context.runOnClient(client -> ConfigManager.flush());
		check(configFile(context).replaceAll("\\s", "").contains("\"showBeams\":true"), "and back on");
		LOGGER.info("config screen module option: Show beacon beams applied at once, was written on close, also after being shown again");
	}

	private static String configFile(ClientGameTestContext context) {
		Path file = context.computeOnClient(client -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("k8bas_skyblock_utility.json"));
		try {
			return java.nio.file.Files.readString(file);
		} catch (IOException e) {
			throw new AssertionError("cannot read " + file, e);
		}
	}

	/** T2.4d: General › Interface and Keybinds with real input (AC-UI-16 [C], AC-UI-13 [C], R28). */
	private static void generalCategory(ClientGameTestContext context) {
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.runOnClient(client -> screen(client).scrollTo(0));
		context.waitTicks(2);
		check(context.computeOnClient(client -> Theme.current().accent() & 0xFFFFFF) == 0x29B6B2, "a fresh config has the teal accent");

		// The accent through the picker: #FF5252, applied at once, written, and the error red stays.
		click(context, controlCentre(context, "interface.accent"));
		check(context.computeOnClient(client -> screen(client).overlay()) instanceof com.k8bas.skyblockutility.ui.widget.ColorPickerOverlay,
				"the swatch opens the picker");
		int[] hex = context.computeOnClient(client -> {
			var picker = (com.k8bas.skyblockutility.ui.widget.ColorPickerOverlay) screen(client).overlay();
			return centreOf(picker.widgets().stream().filter(widget -> widget instanceof com.k8bas.skyblockutility.ui.widget.TextField)
					.findFirst().orElseThrow());
		});
		click(context, hex);
		context.getInput().typeChars("#FF5252");
		context.waitTicks(2);
		int[] save = context.computeOnClient(client -> {
			var picker = (com.k8bas.skyblockutility.ui.widget.ColorPickerOverlay) screen(client).overlay();
			List<Widget> buttons = picker.widgets().stream().filter(widget -> widget instanceof com.k8bas.skyblockutility.ui.widget.Button).toList();
			return centreOf(buttons.get(buttons.size() - 1));
		});
		int beforeSave = context.computeOnClient(client -> ConfigManager.saveRequests());
		click(context, save);
		check(context.computeOnClient(client -> ConfigManager.general().accentColor) == 0xFF5252, "Save stores #FF5252");
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) - beforeSave == 1, "and writes it at once (a discrete commit)");
		context.waitTicks(2);
		Path shot = context.takeScreenshot("t2.4d-accent-ff5252");
		ConfigLayout.Frame frame = context.computeOnClient(client -> screen(client).frame());
		check(pixel(context, shot, frame.header().x() + 300, frame.header().bottom() - 1) == 0xFF5252, "the header line takes it at once");
		check(Theme.ERROR == 0xFFFF5555 && Theme.DESTRUCTIVE == 0xFFC83737, "error and destructive red are fixed, not the accent");
		context.runOnClient(client -> ConfigManager.flush());
		check(configFile(context).replaceAll("\\s", "").contains("\"accentColor\":" + 0xFF5252), "the accent is in the file for the next start");

		// Notices: the position dropdown opens inside the content and moves them; the slider sets how long they stay.
		click(context, controlCentre(context, "interface.notice_position"));
		check(context.computeOnClient(client -> screen(client).overlay()) != null, "the position list opens");
		ConfigLayout.Rect content = frame.content();
		int[] row = context.computeOnClient(client -> {
			Widget rows = screen(client).overlay().widgets().get(0);
			check(rows.x() >= content.x() && rows.y() >= content.y() && rows.x() + rows.width() <= content.right()
					&& rows.y() + rows.height() <= content.bottom(), "the list stays inside the content: " + rows.y());
			// The fourth entry: Bottom left.
			return new int[] {rows.x() + rows.width() / 2, rows.y() + 3 * com.k8bas.skyblockutility.ui.widget.Dropdown.ROW_HEIGHT
					+ com.k8bas.skyblockutility.ui.widget.Dropdown.ROW_HEIGHT / 2};
		});
		click(context, row);
		check(context.computeOnClient(client -> ConfigManager.general().noticePosition()) == com.k8bas.skyblockutility.ui.notice.NoticePosition.BOTTOM_LEFT,
				"Bottom left picked");
		move(context, controlCentre(context, "interface.notice_seconds"));
		context.getInput().scroll(1.0);
		context.waitTick();
		check(context.computeOnClient(client -> ConfigManager.general().noticeSeconds()) == 6, "the wheel adds a second");

		// AC-UI-13: the "Open settings" capture: F7 binds and is in options.txt; Esc unbinds; a right click resets.
		click(context, controlCentre(context, "keybinds.open_settings"));
		context.getInput().pressKey(GLFW.GLFW_KEY_F7);
		context.waitTicks(2);
		check("key.keyboard.f7".equals(context.computeOnClient(client -> SettingsKeybind.OPEN_SETTINGS_KEY.saveString())), "F7 binds");
		check(optionsTxt(context).contains("key_" + SettingsKeybind.NAME + ":key.keyboard.f7"), "and is in options.txt");
		click(context, controlCentre(context, "keybinds.open_settings"));
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check("key.keyboard.unknown".equals(context.computeOnClient(client -> SettingsKeybind.OPEN_SETTINGS_KEY.saveString())), "Esc unbinds");
		check(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen, "and keeps the screen");
		click(context, controlCentre(context, "keybinds.open_settings"));
		context.getInput().pressKey(GLFW.GLFW_KEY_F7);
		context.waitTicks(2);
		int[] key = controlCentre(context, "keybinds.open_settings");
		move(context, key);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(2);
		check("key.keyboard.unknown".equals(context.computeOnClient(client -> SettingsKeybind.OPEN_SETTINGS_KEY.saveString())),
				"a right click resets to the default (unbound)");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		context.runOnClient(client -> {
			ConfigManager.general().accentColor = null;
			ConfigManager.general().noticePosition = null;
			ConfigManager.general().noticeSeconds = null;
			ConfigManager.save();
		});
		LOGGER.info("config screen General: accent #FF5252 applied at once and written; notices moved bottom left, 6 s; Open settings key "
				+ "bound to F7 in options.txt, unbound by Esc, reset by a right click");
	}

	private static int[] controlCentre(ClientGameTestContext context, String optionId) {
		return context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = optionRow(screen, option -> option.id().equals(optionId));
			ConfigLayout.Rect control = ConfigLayout.control(ConfigLayout.card(screen.frame(), screen.scrollArea().scroll(), row), row.option());
			return new int[] {control.x() + control.w() / 2, control.y() + control.h() / 2};
		});
	}

	private static int[] centreOf(Widget widget) {
		return new int[] {widget.x() + widget.width() / 2, widget.y() + widget.height() / 2};
	}

	private static String optionsTxt(ClientGameTestContext context) {
		Path file = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("options.txt"));
		try {
			return java.nio.file.Files.readString(file);
		} catch (IOException e) {
			throw new AssertionError("cannot read " + file, e);
		}
	}

	private static boolean mobHighlighterOn() {
		return ModuleManager.modules().stream().filter(module -> module.id().equals("mob_highlighter")).findFirst().orElseThrow().isEnabled();
	}

	private static void replaceQuery(ClientGameTestContext context, String query) {
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_A);
		context.getInput().releaseControl();
		context.getInput().typeChars(query);
		context.waitTicks(2);
	}

	/** The options laid out on the shown page, by id. */
	private static List<String> optionIds(ConfigScreen screen) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow)
				.map(row -> ((ConfigLayout.OptionRow) row).option().id()).toList();
	}

	private static String settings() {
		StringBuilder state = new StringBuilder();
		ModuleManager.modules().forEach(module -> state.append(module.id()).append('=').append(module.isEnabled()).append(' '));
		var general = ConfigManager.general();
		state.append("range=").append(general.mobScanRangeBlocks).append(" updates=").append(general.autoUpdateCheckEnabled);
		return state.toString();
	}

	private static ConfigLayout.OptionRow firstOptionRow(ConfigScreen screen) {
		return optionRow(screen, option -> true);
	}

	private static ConfigLayout.OptionRow optionRow(ConfigScreen screen, java.util.function.Predicate<Option> kind) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option && kind.test(option.option()))
				.map(row -> (ConfigLayout.OptionRow) row).findFirst().orElseThrow();
	}

	private static void move(ClientGameTestContext context, int[] gui) {
		int[] window = context.computeOnClient(client -> window(gui[0], gui[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTicks(2);
	}

	private static void click(ClientGameTestContext context, int[] gui) {
		move(context, gui);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static int[] window(int guiX, int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return new int[] {(int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth()),
				(int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight())};
	}

	/** The screenshot's pixel at the middle of a GUI pixel, mapped by the image's own size (exact at GUI scale 1). */
	private static int pixel(ClientGameTestContext context, Path screenshot, int guiX, int guiY) {
		int[] gui = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
		try {
			var image = javax.imageio.ImageIO.read(screenshot.toFile());
			int x = (int) Math.floor((guiX + 0.5) * image.getWidth() / gui[0]);
			int y = (int) Math.floor((guiY + 0.5) * image.getHeight() / gui[1]);
			return image.getRGB(x, y) & 0xFFFFFF;
		} catch (IOException e) {
			throw new AssertionError("cannot read " + screenshot, e);
		}
	}

	private static ConfigScreen screen(Minecraft client) {
		if (!(client.gui.screen() instanceof ConfigScreen screen)) {
			throw new AssertionError("FAILED: the settings screen is not open: " + client.gui.screen());
		}
		return screen;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
