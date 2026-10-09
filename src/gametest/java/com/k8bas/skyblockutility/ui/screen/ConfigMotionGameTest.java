package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.render.ScaleAbout;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * REQ-UI-18 (T2.9), the settings screen's motion on a clock the test steps, so each mid-animation frame is exact
 * (the other gametests run with the motion off, -Dk8bas.ui.motion=off):
 * <ul>
 *   <li>AC-UI-02 during the open: at scale 0.90 the drawn panel lies inside the laid-out one, so inside the window
 *       with 4 px free, at two sizes and across a resize; nothing is drawn outside it; no render error is logged.</li>
 *   <li>AC-UI-03 during the open: a click at a control's drawn place (scaled) reaches it.</li>
 *   <li>AC-UI-02/03 during a category switch: the content slides 6 px and is veiled at alpha 131 at 40 ms, the veil
 *       stays in the content area, and a click on the slid card reaches it.</li>
 *   <li>An overlay opened mid-switch settles the motion first, and the dropdown that opened it keeps its place (the
 *       rest of the slide goes into the scroll), so its list stays open.</li>
 * </ul>
 */
public class ConfigMotionGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		String start = "k8bas config motion start " + System.nanoTime();
		String end = "k8bas config motion end " + System.nanoTime();
		AtomicLong clock = new AtomicLong(10_000);
		context.getInput().resizeWindow(1280, 960);
		try {
			context.runOnClient(client -> client.options.guiScale().set(2));
			LOGGER.info(start);
			context.setScreen(() -> new ConfigScreen(Minecraft.getInstance().gui.screen(), OptionCatalog.live(), clock::get, () -> true));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(3);
			duringTheOpen(context, clock);
			clock.addAndGet(1_000);
			context.waitTicks(2);
			check(!context.computeOnClient(client -> screen(client).motion().animating()), "the open has ended");
			duringASwitch(context, clock);
			anOverlaySettlesTheMotion(context);
			clock.addAndGet(1_000);
			context.waitTicks(2);
			check(!context.computeOnClient(client -> screen(client).motion().animating()), "settled");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			check(!(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen), "Esc closes the screen");
			context.waitTicks(5);
			LOGGER.info(end);
			List<String> errors = errorsBetween(context, start, end);
			check(errors.isEmpty(), "no exception or render error logged during the motion: " + errors);
		} finally {
			context.runOnClient(client -> {
				ModuleManager.modules().forEach(module -> module.setEnabled(true));
				ConfigManager.save();
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** The clock stands still at the first frame: the panel stays at 0.90. */
	private static void duringTheOpen(ClientGameTestContext context, AtomicLong clock) {
		float scale = context.computeOnClient(client -> screen(client).motion().scale());
		check(scale == 0.90F, "the open starts at 0.90: " + scale);
		checkScaledPanel(context, "640x480");
		Path shot = context.takeScreenshot("t2.9-open-at-90");
		// Inside the laid-out panel's corner but outside the drawn (scaled) one: not the panel; deep in the sidebar: the sidebar.
		int[] corner = context.computeOnClient(client -> {
			ConfigLayout.Rect panel = screen(client).frame().panel();
			return new int[] {panel.x() + 2, panel.y() + 2};
		});
		int outside = pixel(context, shot, corner[0], corner[1]);
		check(outside != (Theme.PANEL & 0xFFFFFF) && outside != (Theme.SIDEBAR & 0xFFFFFF),
				"outside the drawn panel the backdrop shows: " + Integer.toHexString(outside));
		int[] sidebar = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.Rect bar = screen.frame().sidebar();
			return drawn(screen, bar.x() + bar.w() / 2, bar.bottom() - 12);
		});
		int inSidebar = pixel(context, shot, sidebar[0], sidebar[1]);
		check(inSidebar == (Theme.SIDEBAR & 0xFFFFFF), "the scaled sidebar is drawn: " + Integer.toHexString(inSidebar));

		// AC-UI-03 during the open: a toggle card's title clicked where it is drawn flips that toggle (scrolled into view first,
		// still at 0.90: the clock stands still).
		context.runOnClient(client -> {
			ConfigLayout.OptionRow row = toggleRow(screen(client));
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
		});
		context.waitTicks(2);
		boolean before = context.computeOnClient(client -> toggleValue(screen(client)));
		int[] title = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.Rect card = ConfigLayout.card(screen.frame(), screen.scrollArea().scroll(), toggleRow(screen));
			return drawn(screen, card.x() + 12, card.y() + card.h() / 2);
		});
		click(context, title);
		check(context.computeOnClient(client -> toggleValue(screen(client))) != before, "a click on the drawn toggle card flips it");
		check(context.computeOnClient(client -> screen(client).motion().scale()) == 0.90F, "still mid-open");
		click(context, title);
		check(context.computeOnClient(client -> toggleValue(screen(client))) == before, "and back");

		// Resized half-way through the open: the panel is centred again, still inside the window, at the same scale.
		clock.addAndGet(110);
		context.waitTicks(2);
		float halfway = context.computeOnClient(client -> screen(client).motion().scale());
		check(halfway > 0.90F && halfway < 1F, "half-way through the open: " + halfway);
		context.getInput().resizeWindow(854, 480);
		context.runOnClient(client -> client.options.guiScale().set(1));
		context.waitTicks(3);
		check(context.computeOnClient(client -> screen(client).motion().scale()) == halfway, "a resize does not restart or end the open");
		checkScaledPanel(context, "854x480 after a resize");
		context.takeScreenshot("t2.9-open-at-90-resized");
		context.getInput().resizeWindow(1280, 960);
		context.runOnClient(client -> client.options.guiScale().set(2));
		context.waitTicks(3);
	}

	/** AC-UI-02 at the screen's current size: the drawn panel inside the window with 4 px free, every widget in the panel. */
	private static void checkScaledPanel(ClientGameTestContext context, String where) {
		String problem = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			float scale = screen.motion().scale();
			ConfigLayout.Rect panel = screen.frame().panel();
			double left = ScaleAbout.toScreen(panel.x(), screen.width / 2.0, scale);
			double right = ScaleAbout.toScreen(panel.right(), screen.width / 2.0, scale);
			double top = ScaleAbout.toScreen(panel.y(), screen.height / 2.0, scale);
			double bottom = ScaleAbout.toScreen(panel.bottom(), screen.height / 2.0, scale);
			List<String> problems = new ArrayList<>();
			if (left < 4 || top < 4 || right > screen.width - 4 || bottom > screen.height - 4) {
				problems.add("drawn panel " + left + "," + top + "-" + right + "," + bottom + " in " + screen.width + "x" + screen.height);
			}
			String widgets = outsideThePanel(screen);
			if (!widgets.isEmpty()) {
				problems.add(widgets);
			}
			return String.join("; ", problems);
		});
		check(problem.isEmpty(), where + ": " + problem);
	}

	/** 40 ms into a switch to Highlights: slid 6 px, veiled at 131, in the content only; a click on the slid card works. */
	private static void duringASwitch(ClientGameTestContext context, AtomicLong clock) {
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		context.waitTicks(2);
		clock.addAndGet(40);
		context.waitTicks(2);
		int slide = context.computeOnClient(client -> screen(client).motion().slideOffset());
		int veil = context.computeOnClient(client -> screen(client).motion().veilAlpha());
		check(slide == 6 && veil == 131, "40 ms in: slide " + slide + ", veil " + veil);
		String problem = context.computeOnClient(client -> outsideThePanel(screen(client)));
		check(problem.isEmpty(), "while sliding: " + problem);
		Path shot = context.takeScreenshot("t2.9-switch-at-40ms");
		int[] points = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			Widget card = screen.page().card(toggleRow(screen).option());
			ConfigLayout.Rect bar = screen.frame().sidebar();
			// The card's plain background: halfway across, below its text.
			return new int[] {card.x() + card.width() / 2, card.y() + card.height() - 4, bar.x() + bar.w() / 2, bar.bottom() - 12};
		});
		int veiled = pixel(context, shot, points[0], points[1]);
		check(between(veiled, Theme.CARD, Theme.PANEL), "the card is veiled, between card and panel: " + Integer.toHexString(veiled));
		int sidebar = pixel(context, shot, points[2], points[3]);
		check(sidebar == (Theme.SIDEBAR & 0xFFFFFF), "the sidebar is not veiled: " + Integer.toHexString(sidebar));

		String before = context.computeOnClient(client -> settings());
		int[] title = context.computeOnClient(client -> {
			Widget card = screen(client).page().card(toggleRow(screen(client)).option());
			return new int[] {card.x() + 12, card.y() + card.height() / 2};
		});
		click(context, title);
		String after = context.computeOnClient(client -> settings());
		check(after.contains("mob_highlighter=false") && before.contains("mob_highlighter=true"), "the slid toggle card flips Mob Highlighter: " + after);
		click(context, title);
		check(context.computeOnClient(client -> settings()).equals(before), "and back");
	}

	/** A dropdown opened mid-switch settles the motion, so the list is placed at its settled box. */
	private static void anOverlaySettlesTheMotion(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).motion().animating()), "switching to General");
		context.runOnClient(client -> {
			ConfigLayout.OptionRow row = choiceRow(screen(client));
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
		});
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).motion().animating()), "still switching");
		int[] dropdown = context.computeOnClient(client -> {
			Widget control = screen(client).page().control(choiceRow(screen(client)).option());
			return new int[] {control.x() + control.width() / 2, control.y() + control.height() / 2, control.y()};
		});
		click(context, dropdown);
		context.waitTicks(2);
		int settledY = context.computeOnClient(client -> screen(client).page().control(choiceRow(screen(client)).option()).y());
		check(settledY == dropdown[2], "the dropdown keeps its place when the motion settles: " + dropdown[2] + " -> " + settledY);
		check(context.computeOnClient(client -> screen(client).overlay()) != null, "the dropdown opened and stayed open");
		check(!context.computeOnClient(client -> screen(client).motion().animating()), "and settled the motion");
		check(context.computeOnClient(client -> screen(client).motion().scale()) == 1F, "unscaled");
		context.takeScreenshot("t2.9-dropdown-settles");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).overlay()) == null, "Esc closed the dropdown, not the screen");
	}

	private static ConfigLayout.OptionRow choiceRow(ConfigScreen screen) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option && option.option() instanceof Choice<?>)
				.map(row -> (ConfigLayout.OptionRow) row).findFirst().orElseThrow();
	}

	private static boolean toggleValue(ConfigScreen screen) {
		return ((Toggle) toggleRow(screen).option()).binding().get();
	}

	private static ConfigLayout.OptionRow toggleRow(ConfigScreen screen) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option && option.option() instanceof Toggle)
				.map(row -> (ConfigLayout.OptionRow) row).findFirst().orElseThrow();
	}

	/** Where a layout point is drawn now, as a GUI pixel. */
	private static int[] drawn(ConfigScreen screen, int x, int y) {
		float scale = screen.motion().scale();
		return new int[] {(int) Math.floor(ScaleAbout.toScreen(x + 0.5, screen.width / 2.0, scale)),
				(int) Math.floor(ScaleAbout.toScreen(y + 0.5, screen.height / 2.0, scale))};
	}

	/** Each channel strictly between the two colours' (in either order). */
	private static boolean between(int rgb, int a, int b) {
		for (int shift : new int[] {16, 8, 0}) {
			int value = rgb >> shift & 0xFF;
			int low = Math.min(a >> shift & 0xFF, b >> shift & 0xFF);
			int high = Math.max(a >> shift & 0xFF, b >> shift & 0xFF);
			if (low != high && (value <= low || value >= high)) {
				return false;
			}
		}
		return rgb != (a & 0xFFFFFF) && rgb != (b & 0xFFFFFF);
	}

	private static String settings() {
		StringBuilder state = new StringBuilder();
		ModuleManager.modules().forEach(module -> state.append(module.id()).append('=').append(module.isEnabled()).append(' '));
		var general = ConfigManager.general();
		state.append("range=").append(general.mobScanRangeBlocks).append(" updates=").append(general.autoUpdateCheckEnabled);
		return state.toString();
	}

	/** Every widget's drawable part (its bounds, cut to its clip region) lies in the laid-out panel. */
	private static String outsideThePanel(ConfigScreen screen) {
		ConfigLayout.Rect panel = screen.frame().panel();
		List<String> problems = new ArrayList<>();
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

	private static void click(ClientGameTestContext context, int[] gui) {
		int[] window = context.computeOnClient(client -> window(gui[0], gui[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		// Off the panel again, so the next screenshot shows no hover.
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(1);
	}

	private static int[] window(int guiX, int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return new int[] {(int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth()),
				(int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight())};
	}

	/** The screenshot's pixel at the middle of a GUI pixel, mapped by the image's own size. */
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
