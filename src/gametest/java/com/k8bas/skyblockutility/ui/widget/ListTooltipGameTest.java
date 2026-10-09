package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.TooltipLayout;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * T2.3c in a real client:
 * <ul>
 *   <li>AC-UI-17 [C]: a 6-line tooltip on a button in the bottom-right corner lies entirely inside the
 *       window at GUI scales 1-4, and is drawn there (its background read from the screenshot); away
 *       from the button there is none;</li>
 *   <li>REQ-UI-20 with real input on 5,000 rows: the wheel scrolls three rows a notch, a click picks
 *       the row under it, the thumb dragged to the bottom shows the last row, a press on the track
 *       jumps there; never more than 13 rows are laid out. Screenshots from start to end.</li>
 * </ul>
 */
public class ListTooltipGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		try {
			context.setScreen(ListTooltipTestScreen::new);
			context.waitForScreen(ListTooltipTestScreen.class);
			tooltipAtTheCorner(context);
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
			longList(context);
		} finally {
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static void tooltipAtTheCorner(ClientGameTestContext context) {
		StringBuilder log = new StringBuilder();
		for (int scale = 1; scale <= 4; scale++) {
			int s = scale;
			context.runOnClient(client -> client.options.guiScale().set(s));
			context.waitTicks(3);
			move(context, screen -> screen.centre(screen.corner));
			Path shot = context.takeScreenshot("t2.3c-tooltip-corner-scale-" + s);
			TooltipLayout.Box box = context.computeOnClient(client -> screen(client).tooltip());
			int[] size = context.computeOnClient(client -> new int[] {screen(client).width, screen(client).height});
			check(box != null, "scale " + s + ": hovering the button shows its tooltip");
			check(box.lines().size() == 6, "scale " + s + ": six lines: " + box.lines());
			check(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= size[0] && box.y() + box.height() <= size[1],
					"scale " + s + ": inside the " + size[0] + "x" + size[1] + " window: " + box);
			// The top padding's middle shows the tooltip's background, so it is drawn where it was laid out.
			int[] inBox = context.computeOnClient(client -> new int[] {ListTooltipTestScreen.windowX(box.x() + box.width() / 2),
					ListTooltipTestScreen.windowY(box.y() + 1)});
			int background = pixel(shot, inBox);
			check(background == (Theme.SIDEBAR & 0xFFFFFF), "scale " + s + ": the box is drawn: " + Integer.toHexString(background));
			log.append(" scale ").append(s).append(' ').append(box.x()).append(',').append(box.y()).append(' ').append(box.width()).append('x')
					.append(box.height()).append(" in ").append(size[0]).append('x').append(size[1]);
		}
		// None while a press is held (it would cover a drag).
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "no tooltip while the button is held");
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "control: away from the button there is no tooltip");
		// A label drawn by the screen asks for its own.
		move(context, ListTooltipTestScreen::label);
		TooltipLayout.Box label = context.computeOnClient(client -> screen(client).tooltip());
		check(label != null && label.lines().equals(java.util.List.of("The whole label")), "a hovered label shows its tooltip: " + label);
		LOGGER.info("tooltip at the corner:{}", log);
	}

	private static void longList(ClientGameTestContext context) {
		context.takeScreenshot("t2.3c-list-start");
		move(context, screen -> screen.visibleRow(0));
		for (int i = 0; i < 3; i++) {
			context.getInput().scroll(-1.0);
			context.waitTick();
		}
		int first = context.computeOnClient(client -> screen(client).list.rows().visible().first());
		check(first == 9, "three notches scroll nine rows: first row " + first);
		checkLaidOut(context);
		click(context, screen -> screen.visibleRow(2));
		int clicked = context.computeOnClient(client -> screen(client).clicked);
		check(clicked == 11, "a click picks the row under it: " + clicked);
		context.waitTicks(2);
		TooltipLayout.Box row = context.computeOnClient(client -> screen(client).tooltip());
		check(row != null && row.lines().equals(java.util.List.of("Row 11 in full")), "the hovered row's tooltip follows the scroll: " + row);
		context.takeScreenshot("t2.3c-list-scrolled");
		move(context, ListTooltipTestScreen::thumbCentre);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "the thumb shows no row's tooltip");

		move(context, ListTooltipTestScreen::thumbCentre);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		move(context, screen -> screen.track(1.5));
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		int last = context.computeOnClient(client -> screen(client).list.rows().visible().end() - 1);
		check(last == ListTooltipTestScreen.ROWS - 1, "the thumb dragged to the bottom shows the last row: " + last);
		checkLaidOut(context);
		context.takeScreenshot("t2.3c-list-end");

		click(context, screen -> screen.track(0.25));
		int quarter = context.computeOnClient(client -> screen(client).list.rows().visible().first());
		check(quarter > ListTooltipTestScreen.ROWS / 5 && quarter < ListTooltipTestScreen.ROWS * 3 / 10,
				"a press a quarter down the track jumps about a quarter in: " + quarter);
		check(context.computeOnClient(client -> screen(client).clicked) == 11, "a press on the track picks no row");
		context.takeScreenshot("t2.3c-list-quarter");
		LOGGER.info("long list: 3 notches to row {}, clicked {}, thumb to the end shows {}, track press to {}", first, clicked, last, quarter);
	}

	/** AC-UI-18: never more than ceil(viewport / 18) + 1 rows laid out. */
	private static void checkLaidOut(ClientGameTestContext context) {
		context.waitTick();
		int laidOut = context.computeOnClient(client -> screen(client).list.laidOut().count());
		int painted = context.computeOnClient(client -> screen(client).paintedRows);
		int viewport = context.computeOnClient(client -> screen(client).list.height);
		int bound = (viewport + ListTooltipTestScreen.ROW_HEIGHT - 1) / ListTooltipTestScreen.ROW_HEIGHT + 1;
		check(laidOut <= bound && painted > 0 && painted <= bound, laidOut + " rows laid out and " + painted + " painted in a " + viewport + " px viewport");
	}

	private interface Locate {
		int[] at(ListTooltipTestScreen screen);
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

	private static ListTooltipTestScreen screen(Minecraft client) {
		if (!(client.gui.screen() instanceof ListTooltipTestScreen screen)) {
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

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
