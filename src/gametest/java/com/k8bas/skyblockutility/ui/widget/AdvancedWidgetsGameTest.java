package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.HairlineProbe;
import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.ui.color.Hsv;
import com.k8bas.skyblockutility.ui.color.WheelGeometry;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.render.CornerMask;
import com.k8bas.skyblockutility.ui.render.IconProbe;
import com.k8bas.skyblockutility.ui.render.IconShapes;
import com.k8bas.skyblockutility.ui.render.ShapeRenderState;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
 *   <li>REQ-UI-30 (T3.18): the colour wheel's marker is two discs of the disc mask at GUI scale 2.</li>
 *   <li>AC-UI-32 (T3.19): at GUI scales 1-4, in the first frame after a change from 2 to 3, and after a brightness drag,
 *       the wheel is a texture of its size times the scale, drawn 1:1 with linear sampling, its rim anti-aliased and its
 *       colours the colours picked; if it cannot be made, the 1.2.1 wheel is drawn (REQ-UI-47).</li>
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
			listOutlineIsAHairline(context);
			dropdownIcons(context);
			dropdownScrollbar(context);
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

	/**
	 * AC-UI-34 [C] (REQ-UI-33, T3.22): at GUI scales 2, 3 and 4 the open short dropdown list's outline measures
	 * ceil(guiScale / 2) physical px on its top edge; back at scale 2 afterwards.
	 */
	private static void listOutlineIsAHairline(ClientGameTestContext context) {
		try {
			for (int scale = 2; scale <= 4; scale++) {
				int s = scale;
				context.runOnClient(client -> client.options.guiScale().set(s));
				context.waitTicks(3);
				click(context, screen -> screen.centre(screen.shortDropdown));
				int[] list = context.computeOnClient(client -> {
					Widget rows = screen(client).openListWidget();
					return new int[] {rows.x, rows.y, rows.width};
				});
				context.getInput().setCursorPos(1, 1);
				context.waitTicks(2);
				java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(context.takeScreenshot("t3.22-list-outline-scale-" + scale).toFile());
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTick();
				// REQ-UI-33's rule written out, not read from the code under test.
				int thick = (int) Math.ceil(scale / 2.0);
				int listEdge = 0;
				while (near(image.getRGB((list[0] + list[2] / 2) * scale, list[1] * scale + listEdge) & 0xFFFFFF, Theme.SEPARATOR & 0xFFFFFF)) {
					listEdge++;
				}
				LOGGER.info("dropdown list outline at scale {}: {} px (rule {})", scale, listEdge, thick);
				check(listEdge == thick, "scale " + scale + ": the open list's outline is " + listEdge + " px, the hairline " + thick);
			}
		} catch (IOException e) {
			throw new AssertionError("cannot read a screenshot", e);
		} finally {
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
		}
	}

	/**
	 * AC-UI-37 [C] (REQ-UI-36, T3.27): the open long dropdown's scrollbar (12 entries, 8 in view) at GUI scales 1-4: idle,
	 * hovered, and dragged (held on the thumb, the pointer moved off it sideways), and the drag scrolls the list.
	 */
	private static void dropdownScrollbar(ClientGameTestContext context) {
		try {
			for (int scale = 1; scale <= 4; scale++) {
				int s = scale;
				context.runOnClient(client -> client.options.guiScale().set(s));
				context.waitTicks(3);
				int[] box = context.computeOnClient(client -> {
					Dropdown<String> dropdown = screen(client).longDropdown;
					return windowPoint(dropdown.x + dropdown.width / 2, dropdown.y + dropdown.height / 2);
				});
				context.getInput().setCursorPos(box[0], box[1]);
				context.waitTicks(2);
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				context.waitTicks(2);
				check(context.computeOnClient(client -> screen(client).listOpen()), "scale " + s + ": the long list opened");
				for (String state : java.util.List.of("idle", "hovered", "dragged")) {
					int[] g = context.computeOnClient(client -> {
						Widget rows = screen(client).openListWidget();
						int[] thumb = screen(client).longDropdown.openThumb();
						return new int[] {rows.x + rows.width, rows.y, rows.y + rows.height, thumb[0], thumb[1], rows.x};
					});
					int[] at = context.computeOnClient(client -> state.equals("idle") ? windowPoint(g[5] + 20, g[1] + 3 * Dropdown.ROW_HEIGHT)
							: windowPoint(g[0] - 3, g[3] + g[4] / 2));
					context.getInput().setCursorPos(at[0], at[1]);
					context.waitTicks(2);
					if (state.equals("dragged")) {
						context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
						context.waitTick();
						check(context.computeOnClient(client -> screen(client).listOpen()), "scale " + s + ": a press on the thumb grabs it and picks no entry");
						int[] off = context.computeOnClient(client -> windowPoint(g[5] + 20, g[3] + g[4] / 2));
						context.getInput().setCursorPos(off[0], off[1]);
						context.waitTicks(2);
					}
					java.awt.image.BufferedImage shot = com.k8bas.skyblockutility.ui.render.HairlineProbe.read(
							context.takeScreenshot("t3.27-dropdown-" + state + "-scale-" + s));
					String problem = ScrollbarProbe.check(shot, "the dropdown list's " + state + " scrollbar", s, g[0], g[1], g[2], g[3], g[4],
							!state.equals("idle"));
					if (state.equals("dragged")) {
						// Down by half the list's height: the list follows the thumb.
						int[] down = context.computeOnClient(client -> windowPoint(g[5] + 20, g[3] + g[4] / 2 + (g[2] - g[1]) / 2));
						context.getInput().setCursorPos(down[0], down[1]);
						context.waitTicks(2);
						int top = context.computeOnClient(client -> screen(client).longDropdown.openThumb()[0]);
						context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
						context.waitTicks(2);
						check(top > g[3], "scale " + s + ": dragging the thumb scrolls the list: its top " + g[3] + " -> " + top);
						check(context.computeOnClient(client -> screen(client).listOpen()), "scale " + s + ": and leaves it open");
					}
					check(problem.isEmpty(), problem);
				}
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(2);
			}
		} finally {
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
		}
	}

	private static int[] windowPoint(int guiX, int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return new int[] {(int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth()),
				(int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight())};
	}

	/**
	 * AC-UI-33 [C] (T3.21), at GUI scale 2: the closed short dropdown (GUI 8, 8, 120x16) shows the kit's down chevron 12 px
	 * from its right end in the text colour; open, its list shows the kit's check mark beside the selected entry, in the
	 * accent; both drawn 1:1 with anti-aliased edges.
	 */
	private static void dropdownIcons(ClientGameTestContext context) {
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		check(scale == 2, "the dropdown icons are checked at GUI scale 2: " + scale);
		Path closed = context.takeScreenshot("t3.21-dropdown-closed-scale-2");
		String chevron = IconProbe.check(closed, IconShapes.Icon.CHEVRON_DOWN, 8 + 120 - 12, 8 + (16 - 8) / 2, 8, scale, Theme.TEXT_PRIMARY);
		check(chevron.isEmpty(), "the dropdown's chevron: " + chevron);
		click(context, screen -> screen.centre(screen.shortDropdown));
		int[] rows = context.computeOnClient(client -> {
			Widget list = screen(client).openListWidget();
			return new int[] {list.x, list.y, list.width};
		});
		int selected = context.computeOnClient(client -> screen(client).shortDropdownSelectedRow());
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		Path open = context.takeScreenshot("t3.21-dropdown-open-scale-2");
		int rowY = rows[1] + selected * Dropdown.ROW_HEIGHT;
		String check = IconProbe.check(open, IconShapes.Icon.CHECK, rows[0] + rows[2] - 15, rowY + (Dropdown.ROW_HEIGHT - 8) / 2, 8, scale,
				Theme.current().accent());
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();
		check(check.isEmpty(), "the open list's check mark: " + check);
	}

	/** AC-UI-12 with real input: hex typed and saved, Cancel and Esc discard, an invalid hex is marked, alpha only where stored. */
	private static void colourPicker(ClientGameTestContext context) {
		click(context, screen -> screen.centre(screen.ruleSwatch));
		check(!context.computeOnClient(client -> screen(client).picker().hasAlphaControl()), "a rule colour shows no alpha control");
		// AC-UI-32: at GUI scales 1-4 the wheel is baked for the scale. The option resizes the GUI at once and the screenshot
		// renders its own frame, so from 2 to 3 the frame checked is the first one after the change.
		for (int scale = 1; scale <= 4; scale++) {
			int s = scale;
			context.runOnClient(client -> client.options.guiScale().set(s));
			if (scale != 3) {
				context.waitTicks(3);
			}
			Path shot = context.takeScreenshot("t2.3b-picker-rgb-scale-" + scale);
			wheelIsBakedForTheScale(context, shot, scale);
			if (scale >= 2) {
				// AC-UI-34 [C] (T3.23): the picker panel's outline is a hairline; its top edge lies 24 GUI px above the wheel.
				int[] wheel = context.computeOnClient(client -> new int[] {screen(client).picker().wheel().x, screen(client).picker().wheel().y});
				String line = HairlineProbe.check(HairlineProbe.read(shot), "the colour picker's outline", scale, (wheel[0] + 40) * scale,
						(wheel[1] - 24) * scale, 0, 1, Theme.SEPARATOR);
				// The brightness slider's outline lies just outside it: measured up from the row above its top edge.
				int[] slider = context.computeOnClient(client -> {
					Widget bar = screen(client).picker().brightnessSlider();
					return new int[] {bar.x + bar.width / 2, bar.y};
				});
				line += HairlineProbe.check(HairlineProbe.read(shot), "the brightness slider's outline", scale, slider[0] * scale, slider[1] * scale - 1, 0,
						-1, Theme.SEPARATOR);
				check(line.isEmpty(), line);
			}
			// The whole wheel shows: red at the left edge, cyan at the right, the panel in the box's corner.
			int[] left = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.06, 0.5));
			int[] right = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.94, 0.5));
			int[] corner = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.02, 0.02));
			int l = pixel(shot, left), r = pixel(shot, right), c = pixel(shot, corner);
			check((l >> 16 & 0xFF) > (l >> 8 & 0xFF) + 40 && (l >> 16 & 0xFF) > (l & 0xFF) + 40, "scale " + scale + ": red at the left: " + Integer.toHexString(l));
			check((r & 0xFF) > (r >> 16 & 0xFF) + 40 && (r >> 8 & 0xFF) > (r >> 16 & 0xFF) + 40, "scale " + scale + ": cyan at the right: " + Integer.toHexString(r));
			check(c == (Theme.PANEL & 0xFFFFFF), "scale " + scale + ": outside the circle is the panel: " + Integer.toHexString(c));
		}
		context.runOnClient(client -> client.options.guiScale().set(2));
		context.waitTicks(3);
		wheelFallsBack(context);
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
		// The outline is a hairline (T3.22): its first physical row, at the field's top edge.
		int[] fieldTop = context.computeOnClient(client -> {
			Widget field = screen(client).picker().hexField();
			int scale = client.getWindow().getGuiScale();
			return new int[] {(field.x + field.width / 2) * scale, field.y * scale};
		});
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
		// T3.19: the drag rebaked the wheel's texture in place at each brightness; it shows full brightness again.
		wheelIsBakedForTheScale(context, context.takeScreenshot("t3.19-wheel-after-brightness"), 2);
		wheelMarkerIsADisc(context);
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

	/**
	 * REQ-UI-30 (T3.18), at GUI scale 2 with the picker at hue 200, saturation 0.8 and full brightness: the wheel's marker is
	 * an 8 GUI px white disc with a 6 GUI px disc of the picked colour inside it, both of the disc mask. Where the white disc
	 * covers at most three quarters of a pixel (its box's corners and its rim), the wheel shows, not white; the colour disc's
	 * rim, over the opaque white, is the colour over white by its coverage, to within 3 per channel. A square marker is white
	 * in its box's corners and has no blended rim.
	 */
	private static void wheelMarkerIsADisc(ClientGameTestContext context) {
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		check(scale == 2, "the marker check runs at GUI scale 2: " + scale);
		int[] marker = context.computeOnClient(client -> screen(client).wheelMarker());
		int colour = context.computeOnClient(client -> screen(client).picker().state().hsv().toRgb());
		Path shot = context.takeScreenshot("t3.18-wheel-marker-scale-2");
		java.awt.image.BufferedImage image;
		try {
			image = javax.imageio.ImageIO.read(shot.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read " + shot, e);
		}
		int d = 8 * scale;
		int inner = 6 * scale;
		int x0 = (marker[0] - 4) * scale;
		int y0 = (marker[1] - 4) * scale;
		int wheel = 0;
		int blended = 0;
		int wrong = 0;
		String first = "";
		for (int y = 0; y < d; y++) {
			for (int x = 0; x < d; x++) {
				int white = CornerMask.discAlpha(d, x, y);
				int ix = x - scale;
				int iy = y - scale;
				int dot = ix >= 0 && iy >= 0 && ix < inner && iy < inner ? CornerMask.discAlpha(inner, ix, iy) : 0;
				int actual = image.getRGB(x0 + x, y0 + y) & 0xFFFFFF;
				boolean ok = true;
				if (dot == 0 && white <= 192) {
					ok = !near(actual, 0xFFFFFF);
					wheel++;
				} else if (dot == 0 && white == 255) {
					ok = near(actual, 0xFFFFFF);
				} else if (white == 255) {
					int expected = blend(colour, 0xFFFFFF, dot);
					ok = within(actual, expected, 3);
					if (!near(expected, colour) && !near(expected, 0xFFFFFF)) {
						blended++;
					}
				}
				if (!ok && wrong++ == 0) {
					first = " (first at " + x + "," + y + ": " + Integer.toHexString(actual) + ", white " + white + ", dot " + dot + ")";
				}
			}
		}
		LOGGER.info("wheel marker at scale 2: {} pixels show the wheel past the white disc, {} blend the colour disc over white, {} wrong", wheel,
				blended, wrong);
		check(wrong == 0, "the wheel's marker is two discs of the disc mask: " + wrong + " pixels differ" + first);
		check(wheel > 0 && blended > 0, "the marker's rims are anti-aliased: " + wheel + " wheel pixels, " + blended + " blended");
	}

	/**
	 * AC-UI-32 [C] (T3.19), at one GUI scale with the picker open: drawn into a fresh render state, the wheel is one textured
	 * element whose texture measures wheel size × GUI scale, shown whole and 1:1 with the sampler cache's linear, clamp-to-edge
	 * sampler; and in the screenshot each pixel of its box (but the marker's) is the colour a pick at its centre gives, at the
	 * picker's brightness, over the panel by the disc mask's coverage, within 3 per channel. So the rim is anti-aliased, with no
	 * stair step, and the colours drawn are the colours picked. The screenshot is checked first, so a wrong wheel shows how many
	 * pixels are off.
	 */
	private static void wheelIsBakedForTheScale(ClientGameTestContext context, Path shot, int scale) {
		check(context.computeOnClient(client -> client.getWindow().getGuiScale()) == scale, "the wheel check runs at GUI scale " + scale);
		int[] box = context.computeOnClient(client -> {
			Widget wheel = screen(client).picker().wheel();
			return new int[] {wheel.x, wheel.y, wheel.width};
		});
		int[] marker = context.computeOnClient(client -> screen(client).wheelMarker());
		float value = context.computeOnClient(client -> screen(client).picker().state().hsv().v());
		java.awt.image.BufferedImage image;
		try {
			image = javax.imageio.ImageIO.read(shot.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read " + shot, e);
		}
		int size = box[2] * scale;
		double radius = size / 2.0;
		int blended = 0;
		int wrong = 0;
		String first = "";
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (Math.abs(box[0] + x / scale - marker[0]) <= 5 && Math.abs(box[1] + y / scale - marker[1]) <= 5) {
					continue;
				}
				int alpha = CornerMask.discAlpha(size, x, y);
				float[] hs = WheelGeometry.hsAt(x + 0.5 - radius, y + 0.5 - radius, radius, 0F);
				int colour = new Hsv(hs[0], hs[1], value).toRgb();
				int actual = image.getRGB(box[0] * scale + x, box[1] * scale + y) & 0xFFFFFF;
				if (!within(actual, blend(colour, Theme.PANEL & 0xFFFFFF, alpha), 3) && wrong++ == 0) {
					first = " (first at " + x + "," + y + ": " + Integer.toHexString(actual) + ", expected "
							+ Integer.toHexString(blend(colour, Theme.PANEL & 0xFFFFFF, alpha)) + ")";
				}
				// A rim pixel as drawn: neither the panel nor the wheel's colour, but a blend of the two.
				if (alpha > 0 && alpha < 255 && !within(actual, Theme.PANEL & 0xFFFFFF, 3) && !within(actual, colour, 3)) {
					blended++;
				}
			}
		}
		LOGGER.info("colour wheel at scale {}: {} px, {} rim pixels blend the wheel over the panel, {} wrong", scale, size, blended, wrong);
		check(wrong == 0, "scale " + scale + ": " + wrong + " wheel pixels differ from the colour picked there over the panel" + first);
		check(blended > 0, "scale " + scale + ": the wheel's rim is anti-aliased");
		String element = context.computeOnClient(client -> wheelElement(screen(client).picker().wheel(), scale));
		check(element.isEmpty(), "scale " + scale + ": the wheel is not one texture of its size drawn 1:1: " + element);
	}

	/**
	 * REQ-UI-47 (T3.19), at GUI scale 2: while the wheel's texture counts as not made, the picker draws the 1.2.1 wheel, one
	 * blit of the whole 128 px texture over the wheel's bounds and no 1:1 wheel element: red at the wheel's left, cyan at its
	 * right and the panel outside the circle. Cleared, the hook frees the fallback's texture again.
	 */
	private static void wheelFallsBack(ClientGameTestContext context) {
		context.runOnClient(client -> WheelTexture.setFallbackForTests(true));
		try {
			context.waitTicks(2);
			Path shot = context.takeScreenshot("t3.19-wheel-fallback-scale-2");
			String element = context.computeOnClient(client -> wheelElement(screen(client).picker().wheel(), 2));
			check(!element.isEmpty(), "the fallback is not the wheel baked for the scale");
			String blit = context.computeOnClient(client -> legacyBlit(screen(client).picker().wheel()));
			check(blit.isEmpty(), "the fallback is not one blit of the 1.2.1 wheel over the wheel's bounds: " + blit);
			int[] left = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.06, 0.5));
			int[] right = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.94, 0.5));
			int[] corner = context.computeOnClient(client -> screen(client).wheelBoxPoint(0.02, 0.02));
			int l = pixel(shot, left), r = pixel(shot, right), c = pixel(shot, corner);
			LOGGER.info("colour wheel fallback: left {}, right {}, corner {}", Integer.toHexString(l), Integer.toHexString(r), Integer.toHexString(c));
			check((l >> 16 & 0xFF) > (l >> 8 & 0xFF) + 40 && (l >> 16 & 0xFF) > (l & 0xFF) + 40, "the fallback wheel: red at the left: " + Integer.toHexString(l));
			check((r & 0xFF) > (r >> 16 & 0xFF) + 40 && (r >> 8 & 0xFF) > (r >> 16 & 0xFF) + 40, "the fallback wheel: cyan at the right: " + Integer.toHexString(r));
			check(c == (Theme.PANEL & 0xFFFFFF), "the fallback wheel: outside the circle is the panel: " + Integer.toHexString(c));
		} finally {
			context.runOnClient(client -> WheelTexture.setFallbackForTests(false));
		}
	}

	/**
	 * On the client thread: the wheel drawn into a fresh render state, and "" if that is one blit of the whole
	 * {@link WheelTexture#LEGACY_SIZE} px texture over the wheel's bounds, else what differs.
	 */
	private static String legacyBlit(ColorWheel wheel) {
		GuiRenderState state = new GuiRenderState();
		wheel.draw(new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0), Minecraft.getInstance().font, -1, -1);
		List<BlitRenderState> blits = new ArrayList<>();
		state.forEachElement(element -> {
			if (element instanceof BlitRenderState blit) {
				blits.add(blit);
			}
		}, GuiRenderState.TraverseRange.ALL);
		if (blits.size() != 1) {
			return blits.size() + " blits";
		}
		BlitRenderState blit = blits.get(0);
		GpuTextureView view = blit.textureSetup().texure0();
		if (view.getWidth(0) != WheelTexture.LEGACY_SIZE || view.getHeight(0) != WheelTexture.LEGACY_SIZE) {
			return "a texture of " + view.getWidth(0) + "x" + view.getHeight(0);
		}
		if (blit.x0() != wheel.x || blit.y0() != wheel.y || blit.x1() != wheel.x + wheel.width || blit.y1() != wheel.y + wheel.height) {
			return "drawn over " + blit.x0() + "," + blit.y0() + ".." + blit.x1() + "," + blit.y1();
		}
		return blit.u0() == 0 && blit.v0() == 0 && blit.u1() == 1 && blit.v1() == 1 ? "" : "not the whole texture";
	}

	/**
	 * On the client thread: the wheel drawn into a fresh render state, and "" if its largest textured element shows its whole
	 * texture of wheel size × scale texels 1:1 with the linear, clamp-to-edge sampler, else what differs.
	 */
	private static String wheelElement(ColorWheel wheel, int scale) {
		GuiRenderState state = new GuiRenderState();
		wheel.draw(new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0), Minecraft.getInstance().font, -1, -1);
		ShapeRenderState[] largest = {null};
		state.forEachElement(element -> {
			if (element instanceof ShapeRenderState shape && shape.uvs() != null && (largest[0] == null || shape.quads()[2] > largest[0].quads()[2])) {
				largest[0] = shape;
			}
		}, GuiRenderState.TraverseRange.ALL);
		int size = wheel.width * scale;
		ShapeRenderState shape = largest[0];
		if (shape == null) {
			return "no textured shape element";
		}
		if (!Arrays.equals(shape.quads(), new int[] {0, 0, size, size}) || !Arrays.equals(shape.uvs(), new float[] {0, 0, 1, 1})) {
			return "quad " + Arrays.toString(shape.quads()) + " uv " + Arrays.toString(shape.uvs()) + ", expected the whole texture at " + size + " px";
		}
		GpuTextureView view = shape.textureSetup().texure0();
		if (view.getWidth(0) != size || view.getHeight(0) != size) {
			return "a texture of " + view.getWidth(0) + "x" + view.getHeight(0) + ", expected " + size;
		}
		Vector2f from = shape.pose().transformPosition(0, 0, new Vector2f());
		Vector2f to = shape.pose().transformPosition(size, size, new Vector2f());
		if (Math.abs(to.x - from.x - wheel.width) > 1e-3 || Math.abs(to.y - from.y - wheel.height) > 1e-3) {
			return "drawn " + (to.x - from.x) + " GUI px wide, not 1:1";
		}
		GpuSampler linear = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR,
				FilterMode.LINEAR, false);
		return shape.textureSetup().sampler0() == linear ? "" : "not the linear, clamp-to-edge sampler";
	}

	/** {@code top} over {@code bottom} (both 0xRRGGBB) at coverage {@code alpha} (0-255), as the GUI blend draws it. */
	private static int blend(int top, int bottom, int alpha) {
		int out = 0;
		for (int shift = 0; shift <= 16; shift += 8) {
			out |= Math.round(((top >> shift & 0xFF) * alpha + (bottom >> shift & 0xFF) * (255 - alpha)) / 255F) << shift;
		}
		return out;
	}

	/** Within 8 per colour channel. */
	private static boolean near(int a, int b) {
		return within(a, b, 8);
	}

	/** Whether each colour channel of a and b differs by at most {@code tolerance}. */
	private static boolean within(int a, int b, int tolerance) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > tolerance) {
				return false;
			}
		}
		return true;
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
