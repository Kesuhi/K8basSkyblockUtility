package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * T2.3a, the basic widgets in a real client:
 * <ul>
 *   <li>every widget state on one screen, screenshotted at GUI scales 1-4;</li>
 *   <li>AC-UI-06 with real input: 3 wheel notches over a 0.1-step slider raise it by 0.3, Shift gives
 *       the fine step, and the wheel commits nothing;</li>
 *   <li>REQ-UI-15 with real input: a drag along the track follows live and commits once, on release;</li>
 *   <li>a text field takes typing and Ctrl+A, Backspace;</li>
 *   <li>AC-UI-05 [C]: in a world, a focused field takes E and the bound "Open settings" key, so no
 *       inventory or other screen opens; it also takes F3, which toggles the debug overlay on the
 *       same screen while no field is focused (the control that shows the keys are really taken);
 *       the first Esc leaves the field, the second closes the screen.</li>
 * </ul>
 */
public class WidgetsGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		try {
			context.setScreen(WidgetTestScreen::new);
			context.waitForScreen(WidgetTestScreen.class);
			// Away from every widget, so only the forced looks show hover.
			context.getInput().setCursorPos(1, 1);
			for (int scale = 1; scale <= 4; scale++) {
				int s = scale;
				context.runOnClient(client -> client.options.guiScale().set(s));
				context.waitTicks(3);
				context.takeScreenshot("t2.3a-widgets-scale-" + s);
			}
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
			wheelSteps(context);
			dragCommitsOnce(context);
			typingAndShortcuts(context);
			context.setScreen(() -> null);
			focusedFieldTakesTheKeys(context);
		} finally {
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** AC-UI-06 with real wheel events. */
	private static void wheelSteps(ClientGameTestContext context) {
		int[] centres = context.computeOnClient(client -> screen(client.gui.screen()).liveCentres());
		context.getInput().setCursorPos(centres[0], centres[1]);
		context.waitTick();
		for (int i = 0; i < 3; i++) {
			context.getInput().scroll(1.0);
		}
		context.waitTick();
		double afterThree = context.computeOnClient(client -> screen(client.gui.screen()).decimalValue) / 100.0;
		context.getInput().holdShift();
		context.getInput().scroll(1.0);
		context.getInput().releaseShift();
		context.waitTick();
		double afterFine = context.computeOnClient(client -> screen(client.gui.screen()).decimalValue) / 100.0;
		int commits = context.computeOnClient(client -> screen(client.gui.screen()).decimalCommits);
		LOGGER.info("decimal slider: 1.0 + 3 notches = {}, + 1 Shift notch = {}, commits {}", afterThree, afterFine, commits);
		check(afterThree == 1.3, "3 notches at step 0.1 give 1.3: " + afterThree);
		check(afterFine == 1.31, "Shift gives the fine step of 0.01: " + afterFine);
		check(commits == 0, "the wheel commits nothing");
	}

	/** REQ-UI-15: a drag through the real screen follows the pointer and commits once, on release. */
	private static void dragCommitsOnce(ClientGameTestContext context) {
		int[] start = context.computeOnClient(client -> screen(client.gui.screen()).sliderPoint(0.1));
		context.getInput().setCursorPos(start[0], start[1]);
		context.waitTick();
		int commitsBefore = context.computeOnClient(client -> screen(client.gui.screen()).decimalCommits);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		for (double fraction = 0.2; fraction <= 0.9; fraction += 0.1) {
			double f = fraction;
			int[] to = context.computeOnClient(client -> screen(client.gui.screen()).sliderPoint(f));
			context.getInput().setCursorPos(to[0], to[1]);
			context.waitTick();
		}
		boolean dragging = context.computeOnClient(client -> screen(client.gui.screen()).sliderDragging());
		int duringDrag = context.computeOnClient(client -> screen(client.gui.screen()).decimalCommits);
		double atEnd = context.computeOnClient(client -> screen(client.gui.screen()).decimalValue) / 100.0;
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		int afterRelease = context.computeOnClient(client -> screen(client.gui.screen()).decimalCommits);
		LOGGER.info("decimal slider drag: value {} while dragging, commits {} during and {} after", atEnd, duringDrag - commitsBefore,
				afterRelease - commitsBefore);
		check(dragging, "a press on the track starts a drag");
		check(duringDrag == commitsBefore, "no commit while dragging");
		check(Math.abs(atEnd - 2.75) <= 0.05, "the value follows the pointer to 90% of 0.5..3.0: " + atEnd);
		check(afterRelease == commitsBefore + 1, "one commit on release");
	}

	private static void typingAndShortcuts(ClientGameTestContext context) {
		int[] centres = context.computeOnClient(client -> screen(client.gui.screen()).liveCentres());
		context.getInput().setCursorPos(centres[2], centres[3]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(context.computeOnClient(client -> screen(client.gui.screen()).probeFocused()), "a click focuses the field");
		context.getInput().typeChars("hello world");
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_A);
		context.getInput().releaseControl();
		context.waitTick();
		String selected = context.computeOnClient(client -> screen(client.gui.screen()).probeSelection());
		context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
		context.waitTick();
		String after = context.computeOnClient(client -> screen(client.gui.screen()).probeText());
		LOGGER.info("text field: Ctrl+A selected \"{}\", after Backspace \"{}\"", selected, after);
		check("hello world".equals(selected), "Ctrl+A selects all: " + selected);
		check(after.isEmpty(), "Backspace deletes the selection: " + after);
	}

	/** AC-UI-05 [C]. */
	private static void focusedFieldTakesTheKeys(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			KeyMapping openSettings = SettingsKeybind.OPEN_SETTINGS_KEY;
			context.runOnClient(client -> {
				openSettings.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_KP_7));
				KeyMapping.resetMapping();
			});
			try {
				context.setScreen(WidgetTestScreen::new);
				context.waitForScreen(WidgetTestScreen.class);
				// Opening a screen in a world releases the mouse and centres it; place the cursor after that.
				context.waitTicks(2);
				// The control: with no field focused, F3 on this screen toggles the debug overlay.
				boolean overlayBefore = context.computeOnClient(client -> client.debugEntries.isOverlayVisible());
				context.getInput().pressKey(GLFW.GLFW_KEY_F3);
				context.waitTicks(2);
				boolean overlayUnfocused = context.computeOnClient(client -> client.debugEntries.isOverlayVisible());
				check(overlayUnfocused != overlayBefore, "control: F3 toggles the debug overlay while no field is focused");
				context.getInput().pressKey(GLFW.GLFW_KEY_F3);
				context.waitTicks(2);
				int[] centres = context.computeOnClient(client -> screen(client.gui.screen()).liveCentres());
				context.getInput().setCursorPos(centres[2], centres[3]);
				context.waitTick();
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				context.waitTick();
				check(context.computeOnClient(client -> screen(client.gui.screen()).probeFocused()), "the field is focused");
				context.getInput().pressKey(options -> options.keyInventory);
				context.getInput().typeChars("e");
				context.getInput().pressKey(openSettings);
				boolean overlayFocusedBefore = context.computeOnClient(client -> client.debugEntries.isOverlayVisible());
				context.getInput().pressKey(GLFW.GLFW_KEY_F3);
				context.waitTicks(3);
				boolean overlayFocusedAfter = context.computeOnClient(client -> client.debugEntries.isOverlayVisible());
				check(overlayFocusedAfter == overlayFocusedBefore, "a focused field takes F3: the debug overlay does not toggle");
				Screen afterKeys = context.computeOnClient(client -> client.gui.screen());
				check(afterKeys instanceof WidgetTestScreen, "E and the settings key open nothing while the field is focused: " + afterKeys);
				String typed = context.computeOnClient(client -> screen(client.gui.screen()).probeText());
				check("e".equals(typed), "E went into the field: " + typed);

				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(2);
				Screen afterEscape = context.computeOnClient(client -> client.gui.screen());
				check(afterEscape instanceof WidgetTestScreen && !screen(afterEscape).probeFocused(), "the first Esc leaves the field and keeps the screen");
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitFor(client -> client.gui.screen() == null, 40);
				LOGGER.info("focused field: keys taken, \"{}\" typed, Esc left the field, the second Esc closed the screen", typed);
			} finally {
				context.runOnClient(client -> {
					openSettings.setKey(InputConstants.UNKNOWN);
					KeyMapping.resetMapping();
				});
			}
		}
	}

	private static WidgetTestScreen screen(Screen screen) {
		if (!(screen instanceof WidgetTestScreen widgets)) {
			throw new AssertionError("FAILED: the widget test screen is not open: " + screen);
		}
		return widgets;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
