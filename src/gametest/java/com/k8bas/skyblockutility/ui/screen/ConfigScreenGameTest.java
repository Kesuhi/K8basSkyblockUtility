package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
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
