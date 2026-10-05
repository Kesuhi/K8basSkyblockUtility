package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.notice.NoticePosition;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.widget.ColorSwatch;
import com.k8bas.skyblockutility.ui.widget.Dropdown;
import com.k8bas.skyblockutility.ui.widget.KeybindButton;
import com.k8bas.skyblockutility.ui.widget.Slider;
import com.k8bas.skyblockutility.ui.widget.TextField;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * REQ-UI-18 (T2.9b), keyboard navigation of the settings screen, with AC-UI-05/07 and the Esc order of REQ-UI-08/14/22
 * kept: Tab and Shift+Tab move the focus (the sidebar, the search box, then the page in reading order, scrolled into
 * view), Space/Enter activate, the arrows operate the focused control; the accent ring marks it; a modal keeps Tab
 * inside and gives the focus back when it closes; a click moves the focus to what was clicked.
 */
public class KeyboardNavGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		NoticePosition noticeBefore = ConfigManager.general().noticePosition;
		try {
			context.runOnClient(client -> client.options.guiScale().set(2));
			open(context);
			nothingFocusedAtFirst(context);
			sidebarAndSearch(context);
			reopenedAtASmallSizeTabScrollsThePage(context);
			toggleWithSpaceAndEnter(context);
			dropdown(context);
			keybind(context);
			colourPicker(context);
			aClickMovesTheFocus(context);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
		} finally {
			context.runOnClient(client -> {
				ModuleManager.modules().forEach(module -> module.setEnabled(true));
				ConfigManager.general().mobScanRangeBlocks = 64;
				ConfigManager.general().noticePosition = noticeBefore;
				ConfigManager.save();
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static void open(ClientGameTestContext context) {
		context.setScreen(() -> new ConfigScreen(Minecraft.getInstance().gui.screen()));
		context.waitForScreen(ConfigScreen.class);
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
	}

	/** Nothing has the focus on a fresh screen, so an Enter (held over from the chat that opened it) does nothing. */
	private static void nothingFocusedAtFirst(ClientGameTestContext context) {
		check(context.computeOnClient(client -> screen(client).navFocus()) == null, "no focus at first");
		key(context, GLFW.GLFW_KEY_ENTER);
		check(context.computeOnClient(client -> screen(client).overlay() == null && screen(client).keyboardFocus() == null
				&& screen(client).selected() == Category.GENERAL), "Enter does nothing without a focus");
	}

	private static void sidebarAndSearch(ClientGameTestContext context) {
		key(context, GLFW.GLFW_KEY_TAB);
		check(context.computeOnClient(client -> screen(client).navFocus() == screen(client).tabs()), "Tab: the sidebar");
		Path shot = context.takeScreenshot("t2.9b-focus-sidebar");
		int[] ringTop = context.computeOnClient(client -> {
			int[] ring = new int[4];
			screen(client).tabs().focusRing(ring);
			return new int[] {ring[0] + ring[2] / 2, ring[1]};
		});
		int pixel = pixel(context, shot, ringTop[0], ringTop[1]);
		check(pixel == (Theme.current().accent() & 0xFFFFFF), "the ring is in the accent colour: " + Integer.toHexString(pixel));

		key(context, GLFW.GLFW_KEY_DOWN);
		check(context.computeOnClient(client -> screen(client).selected()) == Category.HIGHLIGHTS, "Down: the next category");
		key(context, GLFW.GLFW_KEY_UP);
		check(context.computeOnClient(client -> screen(client).selected()) == Category.GENERAL, "Up: back");

		// AC-UI-07 by keyboard: Tab into the search box, type, and the hit's category shows.
		key(context, GLFW.GLFW_KEY_TAB);
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == screen(client).search()), "Tab: the search box takes the keyboard");
		context.getInput().typeChars("range");
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).selected()) == Category.HIGHLIGHTS, "the search shows Mob scan range");
		key(context, GLFW.GLFW_KEY_TAB);
		check(context.computeOnClient(client -> screen(client).navFocus() instanceof Slider && screen(client).keyboardFocus() == null),
				"Tab leaves the box for the slider");
		int saves = context.computeOnClient(client -> ConfigManager.saveRequests());
		key(context, GLFW.GLFW_KEY_RIGHT);
		check(context.computeOnClient(client -> ConfigManager.general().mobScanRangeBlocks) == 65, "Right: one step up");
		key(context, GLFW.GLFW_KEY_LEFT);
		check(context.computeOnClient(client -> ConfigManager.general().mobScanRangeBlocks) == 64, "Left: one step down");
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves, "live, written when the screen closes");
		// Not typing: one Esc closes the screen (the keyboard focus is no Esc step of its own) and writes once.
		key(context, GLFW.GLFW_KEY_ESCAPE);
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen), "Esc closes the screen");
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves + 1, "and writes once");
	}

	/** At 427x240 the General page overflows: Tab reaches the controls below the fold and scrolls each into view; it wraps. */
	private static void reopenedAtASmallSizeTabScrollsThePage(ClientGameTestContext context) {
		context.getInput().resizeWindow(854, 480);
		context.runOnClient(client -> client.options.guiScale().set(2));
		open(context);
		boolean scrolled = false;
		int stops = 0;
		for (int i = 0; i < 40; i++) {
			key(context, GLFW.GLFW_KEY_TAB);
			Widget focused = context.computeOnClient(client -> screen(client).navFocus());
			if (focused == null) {
				continue;
			}
			stops++;
			boolean onPage = context.computeOnClient(client -> screen(client).page().rowOf(focused) != null);
			if (onPage) {
				check(context.computeOnClient(client -> focused.fullyShowing()), "the focused control is in view: " + focused.getClass().getSimpleName());
			}
			if (context.computeOnClient(client -> screen(client).scrollArea().scroll()) > 0) {
				scrolled = true;
			}
			if (focused == context.computeOnClient(client -> screen(client).tabs()) && i > 0) {
				break;
			}
		}
		check(scrolled, "the page scrolled to show a control below the fold");
		check(context.computeOnClient(client -> screen(client).navFocus() == screen(client).tabs()), "Tab wraps back to the sidebar after " + stops);
		context.getInput().holdShift();
		key(context, GLFW.GLFW_KEY_TAB);
		context.getInput().releaseShift();
		check(context.computeOnClient(client -> screen(client).navFocus() != screen(client).tabs()
				&& screen(client).page().rowOf(screen(client).navFocus()) != null), "Shift+Tab from the sidebar: the page's last control");
		check(context.computeOnClient(client -> screen(client).navFocus().fullyShowing() && screen(client).scrollArea().scroll() > 0),
				"scrolled down to it, in full view");
		context.getInput().resizeWindow(1280, 960);
		context.runOnClient(client -> client.options.guiScale().set(2));
		context.waitTicks(3);
	}

	/** Space flips a toggle once (and types no space anywhere), Enter flips it back. */
	private static void toggleWithSpaceAndEnter(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		context.waitTicks(2);
		tabUntil(context, widget -> widget instanceof ToggleSwitch, "the Mob Highlighter switch");
		boolean before = context.computeOnClient(client -> ((ToggleSwitch) screen(client).navFocus()).value());
		key(context, GLFW.GLFW_KEY_SPACE);
		check(context.computeOnClient(client -> ((ToggleSwitch) screen(client).navFocus()).value()) != before, "Space flips it");
		check(context.computeOnClient(client -> screen(client).search().model().text()).isEmpty(), "no space typed anywhere");
		key(context, GLFW.GLFW_KEY_ENTER);
		check(context.computeOnClient(client -> ((ToggleSwitch) screen(client).navFocus()).value()) == before, "Enter flips it back");
	}

	/** Enter opens the list, Down and Enter pick; the arrows on the closed box pick at once; Esc closes the list, then the screen. */
	private static void dropdown(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		tabUntil(context, widget -> widget instanceof Dropdown<?>, "the notice position dropdown");
		Widget box = context.computeOnClient(client -> screen(client).navFocus());
		Object before = context.computeOnClient(client -> shownChoice(screen(client)));
		key(context, GLFW.GLFW_KEY_ENTER);
		check(context.computeOnClient(client -> screen(client).overlay()) != null, "Enter opens the list");
		key(context, GLFW.GLFW_KEY_DOWN);
		key(context, GLFW.GLFW_KEY_ENTER);
		Object picked = context.computeOnClient(client -> shownChoice(screen(client)));
		check(!Objects.equals(picked, before), "Down, Enter picks the next: " + before + " -> " + picked);
		check(context.computeOnClient(client -> screen(client).overlay() == null && screen(client).navFocus() == box), "closed, the box has the focus again");
		key(context, GLFW.GLFW_KEY_UP);
		check(Objects.equals(context.computeOnClient(client -> shownChoice(screen(client))), before), "Up on the closed box picks at once");
		key(context, GLFW.GLFW_KEY_ENTER);
		key(context, GLFW.GLFW_KEY_ESCAPE);
		check(context.computeOnClient(client -> screen(client).overlay() == null), "Esc closes the list");
		check(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen, "and only the list");
		check(Objects.equals(context.computeOnClient(client -> shownChoice(screen(client))), before), "unchanged");
	}

	/** Enter arms the capture, the next key binds (REQ-UI-14), Delete resets to the default (unbound). */
	private static void keybind(ClientGameTestContext context) {
		tabUntil(context, widget -> widget instanceof KeybindButton, "the Open settings key");
		key(context, GLFW.GLFW_KEY_ENTER);
		check(context.computeOnClient(client -> screen(client).keyboardFocus() instanceof KeybindButton button && button.capturesKeys()),
				"Enter arms the capture");
		key(context, GLFW.GLFW_KEY_F7);
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == null), "F7 bound it and disarmed");
		String bound = context.computeOnClient(client -> boundKey(client));
		check(bound.equals("key.keyboard.f7"), "bound to F7: " + bound);
		key(context, GLFW.GLFW_KEY_DELETE);
		String reset = context.computeOnClient(client -> boundKey(client));
		check(reset.equals(Keybind.UNBOUND), "Delete resets it to its default, unbound: " + reset);
		// The Esc order (REQ-UI-14): armed, Esc unbinds and disarms; the screen stays open.
		key(context, GLFW.GLFW_KEY_ENTER);
		key(context, GLFW.GLFW_KEY_F7);
		key(context, GLFW.GLFW_KEY_ENTER);
		key(context, GLFW.GLFW_KEY_ESCAPE);
		check(context.computeOnClient(client -> boundKey(client)).equals(Keybind.UNBOUND), "Esc on the armed capture unbinds");
		check(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen && context.computeOnClient(client -> screen(client).keyboardFocus() == null),
				"disarmed, and the screen is still open");
	}

	/** In the modal colour picker Tab stays inside; Esc leaves the hex field first, then closes the picker; the swatch has the focus again. */
	private static void colourPicker(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).scrollArea().setScroll(0));
		context.waitTicks(2);
		tabUntil(context, widget -> widget instanceof ColorSwatch, "the accent swatch");
		Widget swatch = context.computeOnClient(client -> screen(client).navFocus());
		Integer accent = context.computeOnClient(client -> ConfigManager.general().accentColor);
		key(context, GLFW.GLFW_KEY_ENTER);
		check(context.computeOnClient(client -> screen(client).overlay() != null && screen(client).overlay().modal()), "Enter opens the picker");
		key(context, GLFW.GLFW_KEY_TAB);
		check(context.computeOnClient(client -> screen(client).keyboardFocus() instanceof TextField), "Tab: the hex field");
		Widget first = context.computeOnClient(client -> screen(client).navFocus());
		boolean back = false;
		for (int i = 0; i < 25 && !back; i++) {
			key(context, GLFW.GLFW_KEY_TAB);
			Widget now = context.computeOnClient(client -> screen(client).navFocus());
			check(context.computeOnClient(client -> screen(client).overlay().widgets().contains(now)), "Tab stays in the picker");
			back = now == first;
		}
		check(back, "Tab goes round the picker's controls back to the hex field");
		// A different colour typed into the hex field (Tab selected its text), then Esc twice.
		context.getInput().typeChars("#FF5252");
		context.waitTicks(2);
		key(context, GLFW.GLFW_KEY_ESCAPE);
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == null && screen(client).overlay() != null),
				"Esc leaves the hex field, the picker stays");
		key(context, GLFW.GLFW_KEY_ESCAPE);
		check(context.computeOnClient(client -> screen(client).overlay() == null), "Esc closes the picker");
		check(context.computeOnClient(client -> screen(client).navFocus()) == swatch, "the swatch has the focus again");
		check(Objects.equals(context.computeOnClient(client -> ConfigManager.general().accentColor), accent), "nothing saved");
	}

	/** A click moves the focus to the clicked control; Tab goes on from there. */
	private static void aClickMovesTheFocus(ClientGameTestContext context) {
		int[] point = context.computeOnClient(client -> {
			Widget search = screen(client).search();
			return new int[] {search.x() + 5, search.y() + search.height() / 2};
		});
		int[] window = context.computeOnClient(client -> window(point[0], point[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).navFocus() == screen(client).search()), "the clicked search box has the focus");
		key(context, GLFW.GLFW_KEY_TAB);
		Option firstOnPage = context.computeOnClient(client -> screen(client).page().page().rows().stream()
				.filter(row -> row instanceof ConfigLayout.OptionRow).map(row -> ((ConfigLayout.OptionRow) row).option()).findFirst().orElseThrow());
		check(context.computeOnClient(client -> screen(client).navFocus() == screen(client).page().control(firstOnPage)),
				"Tab goes on from the clicked box to the page's first control");
		LOGGER.info("keyboard navigation: sidebar, search, slider, wrap and scroll, toggle, dropdown, keybind, picker trap and click all as specified");
	}

	/** The option on the page whose control has the keyboard focus. */
	private static Option focusedOption(ConfigScreen screen) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option
				&& screen.page().control(option.option()) == screen.navFocus()).map(row -> ((ConfigLayout.OptionRow) row).option()).findFirst().orElseThrow();
	}

	/** The value the focused dropdown shows (its binding: an unset notice position reads as top right). */
	private static Object shownChoice(ConfigScreen screen) {
		return ((Choice<?>) focusedOption(screen)).binding().get();
	}

	/** The focused keybind's key, as options.txt names it. */
	private static String boundKey(Minecraft client) {
		String name = ((Keybind) focusedOption(screen(client))).keyMappingName();
		for (var mapping : client.options.keyMappings) {
			if (mapping.getName().equals(name)) {
				return mapping.saveString();
			}
		}
		throw new AssertionError("FAILED: no key mapping " + name);
	}

	private static void tabUntil(ClientGameTestContext context, Predicate<Widget> wanted, String what) {
		for (int i = 0; i < 40; i++) {
			key(context, GLFW.GLFW_KEY_TAB);
			if (context.computeOnClient(client -> wanted.test(screen(client).navFocus()))) {
				return;
			}
		}
		throw new AssertionError("FAILED: Tab never reached " + what);
	}

	private static void key(ClientGameTestContext context, int key) {
		context.getInput().pressKey(key);
		context.waitTicks(2);
	}

	private static int[] window(int guiX, int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return new int[] {(int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth()),
				(int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight())};
	}

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
