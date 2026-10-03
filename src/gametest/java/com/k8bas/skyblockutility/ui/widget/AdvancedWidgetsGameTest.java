package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * T2.3b, the dropdown and keybind capture in a real client:
 * <ul>
 *   <li>every state at GUI scales 1-4; a long list open, and one near the bottom opening upward;</li>
 *   <li>AC-UI-06 with real input: a pick applies and closes; a click outside closes it unchanged and
 *       does not reach the toggle under it (which the same click flips with no list open); the arrow
 *       keys and Enter pick under a still pointer; the wheel scrolls the list;</li>
 *   <li>REQ-UI-14 and R27 on the real "Open settings" mapping: F7 binds and is written to options.txt
 *       (absent before), F2 is not captured, Esc unbinds and is written, a left click while armed
 *       cancels, a right click resets (to a real default on a second widget); E binds and shows the
 *       conflict with the inventory key; F3 binds without toggling the debug overlay.</li>
 * </ul>
 */
public class AdvancedWidgetsGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		try {
			context.setScreen(AdvancedWidgetTestScreen::new);
			context.waitForScreen(AdvancedWidgetTestScreen.class);
			context.getInput().setCursorPos(1, 1);
			for (int scale = 1; scale <= 4; scale++) {
				int s = scale;
				context.runOnClient(client -> client.options.guiScale().set(s));
				context.waitTicks(3);
				context.takeScreenshot("t2.3b-widgets-scale-" + s);
			}
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
			dropdowns(context);
			keybinds(context);
			colourPicker(context);
		} finally {
			context.runOnClient(client -> {
				SettingsKeybind.OPEN_SETTINGS_KEY.setKey(InputConstants.UNKNOWN);
				KeyMapping.resetMapping();
				client.options.save();
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static void dropdowns(ClientGameTestContext context) {
		click(context, screen -> screen.centre(screen.longDropdown));
		check(context.computeOnClient(client -> screen(client).longDropdown.isOpen()), "a click opens the list");
		context.takeScreenshot("t2.3b-dropdown-open");
		click(context, screen -> screen.visibleRow(screen.longDropdown, 2));
		String picked = context.computeOnClient(client -> screen(client).longValue);
		check("Entry 3".equals(picked) && !context.computeOnClient(client -> screen(client).listOpen()), "a pick applies and closes: " + picked);

		// The control: with no list open, the same click flips the toggle.
		click(context, screen -> screen.centre(screen.toggle));
		check(context.computeOnClient(client -> screen(client).toggled), "control: a click flips the toggle with no list open");
		click(context, screen -> screen.centre(screen.longDropdown));
		click(context, screen -> screen.centre(screen.toggle));
		boolean open = context.computeOnClient(client -> screen(client).listOpen());
		String unchanged = context.computeOnClient(client -> screen(client).longValue);
		boolean toggled = context.computeOnClient(client -> screen(client).toggled);
		check(!open && "Entry 3".equals(unchanged) && toggled, "a click outside closes the list unchanged and is not passed on: open " + open
				+ ", value " + unchanged + ", toggle still on " + toggled);

		// Keys under a still pointer: hover row 6, two steps down, Enter picks Entry 8.
		click(context, screen -> screen.centre(screen.longDropdown));
		move(context, screen -> screen.visibleRow(screen.longDropdown, 5));
		context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
		context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
		context.waitTick();
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTick();
		String byKeys = context.computeOnClient(client -> screen(client).longValue);
		check("Entry 8".equals(byKeys), "the arrow keys keep their highlight under a still pointer: " + byKeys);

		// The wheel scrolls the list by one row: the first visible row is then Entry 2.
		click(context, screen -> screen.centre(screen.longDropdown));
		move(context, screen -> screen.visibleRow(screen.longDropdown, 0));
		context.getInput().scroll(-1.0);
		context.waitTick();
		click(context, screen -> screen.visibleRow(screen.longDropdown, 0));
		String afterScroll = context.computeOnClient(client -> screen(client).longValue);
		check("Entry 2".equals(afterScroll), "one wheel notch scrolls one row: " + afterScroll);

		click(context, screen -> screen.centre(screen.bottomDropdown));
		int[] firstRow = context.computeOnClient(client -> screen(client).visibleRow(screen(client).bottomDropdown, 0));
		int[] box = context.computeOnClient(client -> screen(client).centre(screen(client).bottomDropdown));
		context.takeScreenshot("t2.3b-dropdown-near-bottom");
		check(firstRow[1] < box[1], "near the bottom the list opens upward");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();
		Screen afterEscape = context.computeOnClient(client -> client.gui.screen());
		check(afterEscape instanceof AdvancedWidgetTestScreen && !((AdvancedWidgetTestScreen) afterEscape).listOpen(),
				"Esc closes the list and keeps the screen");
		LOGGER.info("dropdown: picked {}, outside click closed it, keys picked {}, wheel scrolled to {}, near the bottom it opened upward, "
				+ "Esc closed it", picked, byKeys, afterScroll);
	}

	private static void keybinds(ClientGameTestContext context) {
		KeyMapping key = SettingsKeybind.OPEN_SETTINGS_KEY;
		String f7Line = "key_" + key.getName() + ":key.keyboard.f7";
		String unboundLine = "key_" + key.getName() + ":" + Keybind.UNBOUND;
		check(!optionsFile(context).contains(f7Line), "baseline: options.txt has no F7 binding yet");

		click(context, screen -> screen.centre(screen.liveKeybind));
		check(context.computeOnClient(client -> screen(client).liveKeybind.armed()), "a click arms it");
		context.getInput().pressKey(GLFW.GLFW_KEY_F7);
		context.waitTick();
		String f7 = context.computeOnClient(client -> key.saveString());
		check("key.keyboard.f7".equals(f7), "F7 binds: " + f7);
		check(optionsFile(context).contains(f7Line), "the binding is in options.txt at once");

		// F2 (screenshot) is taken by the game before any screen; the widget stays armed (REQ-UI-14).
		click(context, screen -> screen.centre(screen.liveKeybind));
		context.getInput().pressKey(GLFW.GLFW_KEY_F2);
		context.waitTick();
		check(context.computeOnClient(client -> screen(client).liveKeybind.armed()), "F2 is not captured; still armed");
		check("key.keyboard.f7".equals(context.computeOnClient(client -> key.saveString())), "and the binding is unchanged");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();
		check(Keybind.UNBOUND.equals(context.computeOnClient(client -> key.saveString())), "Esc unbinds");
		check(context.computeOnClient(client -> client.gui.screen()) instanceof AdvancedWidgetTestScreen, "and the screen stays open");
		check(optionsFile(context).contains(unboundLine), "the unbinding is written too");

		click(context, screen -> screen.centre(screen.liveKeybind));
		context.getInput().pressKey(GLFW.GLFW_KEY_F8);
		context.waitTick();
		click(context, screen -> screen.centre(screen.liveKeybind));
		click(context, screen -> screen.centre(screen.liveKeybind));
		check("key.keyboard.f8".equals(context.computeOnClient(client -> key.saveString())), "a left click while armed cancels");
		check(!context.computeOnClient(client -> screen(client).liveKeybind.armed()), "and disarms");
		rightClick(context, screen -> screen.centre(screen.liveKeybind));
		check(Keybind.UNBOUND.equals(context.computeOnClient(client -> key.saveString())), "a right click resets (this mapping's default is unbound)");
		check(optionsFile(context).contains(unboundLine), "the reset is written");

		// A real default tells a reset from an unbind: armed, then a right click.
		click(context, screen -> screen.centre(screen.resettable));
		check(context.computeOnClient(client -> screen(client).resettable.armed()), "armed");
		rightClick(context, screen -> screen.centre(screen.resettable));
		String reset = context.computeOnClient(client -> screen(client).resettableBound());
		check("key.keyboard.j".equals(reset) && !context.computeOnClient(client -> screen(client).resettable.armed()),
				"a right click while armed resets to the default J and disarms: " + reset);

		click(context, screen -> screen.centre(screen.liveKeybind));
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitTicks(2);
		check("key.keyboard.e".equals(context.computeOnClient(client -> key.saveString())), "E binds");
		check(context.computeOnClient(client -> screen(client).liveKeybind.conflicts()).contains("key.inventory"), "the conflict with the inventory is known");
		context.takeScreenshot("t2.3b-keybind-conflict");

		boolean overlayBefore = context.computeOnClient(client -> client.debugEntries.isOverlayVisible());
		click(context, screen -> screen.centre(screen.liveKeybind));
		context.getInput().pressKey(GLFW.GLFW_KEY_F3);
		context.waitTicks(2);
		check("key.keyboard.f3".equals(context.computeOnClient(client -> key.saveString())), "F3 binds");
		check(context.computeOnClient(client -> client.debugEntries.isOverlayVisible()) == overlayBefore, "without toggling the debug overlay");
		LOGGER.info("keybind: F7 bound and saved, F2 not captured, Esc unbound and saved, left click cancelled, right click reset (also to J), "
				+ "E bound with a conflict, F3 bound");
	}

	/** AC-UI-12 with real input: hex typed and saved, Cancel and Esc discard, an invalid hex is marked, alpha only where stored. */
	private static void colourPicker(ClientGameTestContext context) {
		click(context, screen -> screen.centre(screen.ruleSwatch));
		check(!context.computeOnClient(client -> screen(client).picker().hasAlphaControl()), "a rule colour shows no alpha control");
		for (int scale : new int[] {4, 2}) {
			context.runOnClient(client -> client.options.guiScale().set(scale));
			context.waitTicks(3);
			Path shot = context.takeScreenshot("t2.3b-picker-rgb-scale-" + scale);
			// The whole wheel shows: red at the left edge, cyan at the right, the panel in the box's corner.
			int[] left = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.06, 0.5));
			int[] right = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.94, 0.5));
			int[] corner = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.02, 0.02));
			int l = pixel(shot, left), r = pixel(shot, right), c = pixel(shot, corner);
			check((l >> 16 & 0xFF) > (l >> 8 & 0xFF) + 40 && (l >> 16 & 0xFF) > (l & 0xFF) + 40, "scale " + scale + ": red at the left: " + Integer.toHexString(l));
			check((r & 0xFF) > (r >> 16 & 0xFF) + 40 && (r >> 8 & 0xFF) > (r >> 16 & 0xFF) + 40, "scale " + scale + ": cyan at the right: " + Integer.toHexString(r));
			check(c == (Theme.PANEL & 0xFFFFFF), "scale " + scale + ": outside the circle is the panel: " + Integer.toHexString(c));
		}
		// Typed and saved: applies once, with one commit.
		click(context, screen -> screen.centre(screen.picker().hexField()));
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_A);
		context.getInput().releaseControl();
		context.getInput().typeChars("#GG1234");
		context.waitTick();
		check(!context.computeOnClient(client -> screen(client).picker().state().hexValid()), "#GG1234 is marked invalid");
		check(context.computeOnClient(client -> screen(client).picker().state().color()) == 0x0AA351, "and keeps the last valid colour");
		Path invalidShot = context.takeScreenshot("t2.3b-picker-invalid-hex");
		int[] fieldTop = context.computeOnClient(client -> screen(client).point(screen(client).picker().hexField(), 0.5, 0));
		int outline = pixel(invalidShot, fieldTop);
		check(outline == (Theme.ERROR & 0xFFFFFF), "the invalid field has the error outline: " + Integer.toHexString(outline));
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_A);
		context.getInput().releaseControl();
		context.getInput().typeChars("#1A2B3C");
		context.waitTick();
		check(context.computeOnClient(client -> screen(client).ruleColour) == 0x0AA351, "nothing is applied before Save");
		click(context, screen -> screen.centre(screen.picker().saveButton()));
		int saved = context.computeOnClient(client -> screen(client).ruleColour);
		int commits = context.computeOnClient(client -> screen(client).ruleCommits);
		check(saved == 0x1A2B3C && commits == 1, "Save applies #1A2B3C with one commit: " + Integer.toHexString(saved) + ", " + commits);
		check(!context.computeOnClient(client -> screen(client).listOpen()), "and closes the picker");

		// A pick on the wheel, then Cancel: nothing changes.
		click(context, screen -> screen.centre(screen.ruleSwatch));
		click(context, screen -> screen.wheelPoint(0.6));
		check(context.computeOnClient(client -> screen(client).picker().state().changed()), "the wheel changed the picker's colour");
		click(context, screen -> screen.centre(screen.picker().cancelButton()));
		check(context.computeOnClient(client -> screen(client).ruleColour) == 0x1A2B3C, "Cancel leaves the stored colour");
		check(!context.computeOnClient(client -> screen(client).listOpen()), "Cancel closes the picker");
		click(context, screen -> screen.centre(screen.ruleSwatch));
		check(!context.computeOnClient(client -> screen(client).picker().state().changed()), "a picker opened again starts from the stored colour");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();
		// Esc as well, and the screen stays open behind the modal.
		click(context, screen -> screen.centre(screen.ruleSwatch));
		click(context, screen -> screen.wheelPoint(-0.6));
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();
		check(!context.computeOnClient(client -> screen(client).listOpen()), "Esc closes the picker");
		check(context.computeOnClient(client -> screen(client).ruleColour) == 0x1A2B3C, "without applying");
		check(context.computeOnClient(client -> screen(client).ruleCommits) == 1, "and without a commit");

		// AC-UI-12: brightness dragged to 0 and back keeps the hue and saturation picked in the dialog.
		click(context, screen -> screen.centre(screen.ruleSwatch));
		context.runOnClient(client -> screen(client).picker().state().setWheel(200F / 360F, 0.8F));
		drag(context, screen -> screen.point(screen.picker().brightnessSlider(), 0.5, 0.5),
				screen -> screen.point(screen.picker().brightnessSlider(), -0.5, 0.5), false);
		int dark = context.computeOnClient(client -> screen(client).picker().state().color());
		check(dark == 0, "brightness dragged past the left end gives black: " + Integer.toHexString(dark));
		move(context, screen -> screen.point(screen.picker().brightnessSlider(), 1.5, 0.5));
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		float hue = context.computeOnClient(client -> screen(client).picker().state().hsv().h());
		float saturation = context.computeOnClient(client -> screen(client).picker().state().hsv().s());
		check(Math.abs(hue - 200F / 360F) <= 1F / 360F && Math.abs(saturation - 0.8F) <= 0.01F,
				"and back to full keeps hue 200 and saturation 0.8: " + hue * 360 + ", " + saturation);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();

		// R27: eight digits on a colour without alpha drop the alpha. The click into the field selects all.
		click(context, screen -> screen.centre(screen.ruleSwatch));
		click(context, screen -> screen.centre(screen.picker().hexField()));
		context.getInput().typeChars("#80FF0000");
		context.waitTick();
		click(context, screen -> screen.centre(screen.picker().saveButton()));
		int dropped = context.computeOnClient(client -> screen(client).ruleColour);
		check(dropped == 0xFF0000, "#80FF0000 on a rule colour saves opaque red: " + Integer.toHexString(dropped));

		// Save with an invalid hex applies the last valid colour.
		click(context, screen -> screen.centre(screen.ruleSwatch));
		click(context, screen -> screen.centre(screen.picker().hexField()));
		context.getInput().typeChars("#GG1234");
		context.waitTick();
		click(context, screen -> screen.centre(screen.picker().saveButton()));
		int kept = context.computeOnClient(client -> screen(client).ruleColour);
		int commitsAfter = context.computeOnClient(client -> screen(client).ruleCommits);
		check(kept == 0xFF0000 && commitsAfter == 3, "Save with #GG1234 keeps the last valid colour, with a commit: "
				+ Integer.toHexString(kept) + ", " + commitsAfter);
		check(!context.computeOnClient(client -> screen(client).listOpen()), "and closes the picker");

		// A preset sets the colour and the hex text.
		click(context, screen -> screen.centre(screen.ruleSwatch));
		click(context, screen -> screen.centre(screen.picker().preset(12)));
		int preset = context.computeOnClient(client -> screen(client).picker().state().color());
		String presetHex = context.computeOnClient(client -> screen(client).picker().hexField().model().text());
		check(preset == 0xFF5555 && "#FF5555".equals(presetHex), "preset 12 picks red: " + Integer.toHexString(preset) + ", " + presetHex);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();

		// Alpha: only where stored; dragged to 0 and saved keeps the colour; typed key by key keeps its alpha.
		click(context, screen -> screen.centre(screen.alphaSwatch));
		check(context.computeOnClient(client -> screen(client).picker().hasAlphaControl()), "a colour with alpha shows the alpha control");
		context.takeScreenshot("t2.3b-picker-alpha");
		drag(context, screen -> screen.point(screen.picker().alphaSlider(), 0.5, 0.5),
				screen -> screen.point(screen.picker().alphaSlider(), -0.5, 0.5), true);
		click(context, screen -> screen.centre(screen.picker().saveButton()));
		int clear = context.computeOnClient(client -> screen(client).alphaColour);
		int alphaCommits = context.computeOnClient(client -> screen(client).alphaCommits);
		check(clear >>> 24 == 0 && (clear & 0xFFFFFF) == 0x29B6B2 && alphaCommits == 1,
				"alpha dragged to 0 saves a clear #29B6B2 with one commit: " + Integer.toHexString(clear) + ", " + alphaCommits);
		click(context, screen -> screen.centre(screen.alphaSwatch));
		click(context, screen -> screen.centre(screen.picker().hexField()));
		context.getInput().typeChars("#4029B6B2");
		context.waitTick();
		click(context, screen -> screen.centre(screen.picker().saveButton()));
		int typed = context.computeOnClient(client -> screen(client).alphaColour);
		check(typed == 0x4029B6B2, "#4029B6B2 typed key by key saves with its alpha: " + Integer.toHexString(typed));
		LOGGER.info("colour picker: #GG1234 marked, #1A2B3C saved with one commit, the wheel then Cancel and Esc applied nothing, brightness "
				+ "to 0 and back kept hue and saturation, #80FF0000 dropped its alpha, an invalid hex saved the last valid colour, a preset "
				+ "set the hex, alpha only where stored and saved at 0 and typed");
	}

	/** Presses at one point and moves to another; releases there if asked. */
	private static void drag(ClientGameTestContext context, Locate from, Locate to, boolean release) {
		move(context, from);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		move(context, to);
		if (release) {
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
		}
	}

	private interface Locate {
		int[] at(AdvancedWidgetTestScreen screen);
	}

	private static void move(ClientGameTestContext context, Locate where) {
		int[] point = context.computeOnClient(client -> where.at(screen(client)));
		context.getInput().setCursorPos(point[0], point[1]);
		context.waitTicks(2);
	}

	private static void click(ClientGameTestContext context, Locate where) {
		move(context, where);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static void rightClick(ClientGameTestContext context, Locate where) {
		move(context, where);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTick();
	}

	private static AdvancedWidgetTestScreen screen(Minecraft client) {
		if (!(client.gui.screen() instanceof AdvancedWidgetTestScreen screen)) {
			throw new AssertionError("FAILED: the test screen is not open: " + client.gui.screen());
		}
		return screen;
	}

	private static int pixel(Path screenshot, int[] point) {
		try {
			return javax.imageio.ImageIO.read(screenshot.toFile()).getRGB(point[0], point[1]) & 0xFFFFFF;
		} catch (IOException e) {
			throw new AssertionError("cannot read " + screenshot, e);
		}
	}

	private static String optionsFile(ClientGameTestContext context) {
		Path file = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("options.txt"));
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new AssertionError("cannot read " + file, e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
