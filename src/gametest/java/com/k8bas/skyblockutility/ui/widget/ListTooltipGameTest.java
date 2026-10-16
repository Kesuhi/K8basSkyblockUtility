package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.HairlineProbe;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.TooltipLayout;
import com.k8bas.skyblockutility.ui.render.UiTime;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T2.3c in a real client:
 * <ul>
 *   <li>AC-UI-17 [C]: a 6-line tooltip on a button in the bottom-right corner lies entirely inside the
 *       window at GUI scales 1-4, and is drawn there (its background read from the screenshot); away
 *       from the button there is none;</li>
 *   <li>AC-UI-45 [C] (REQ-UI-44, T3.34), on a clock the test steps: the tooltip shows after 400 ms of rest, not at 399, beside
 *       its control (left of the corner button, right of a list's row and of a label), not while a press is held and only after
 *       the delay again after the release, at once on the next control within 300 ms, and fades in over 100 ms with motion on;</li>
 *   <li>REQ-UI-20 with real input on 5,000 rows: the wheel scrolls three rows a notch, a click picks
 *       the row under it, the thumb dragged to the bottom shows the last row, a press on the track
 *       jumps there; never more than 13 rows are laid out. Screenshots from start to end.</li>
 * </ul>
 */
public class ListTooltipGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	/** The screen's clock (T3.34): motion off, as in every gametest, so glides are instant; the tooltip delay still counts on it. */
	private static final AtomicLong CLOCK = new AtomicLong(10_000);

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		try {
			context.setScreen(() -> new ListTooltipTestScreen(UiTime.of(CLOCK::get, () -> false)));
			context.waitForScreen(ListTooltipTestScreen.class);
			tooltipAtTheCorner(context);
			scrollbarStates(context);
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
			longList(context);
			placedBox(context);
			fadesIn(context);
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
			arrive(context, screen -> screen.centre(screen.corner));
			check(context.computeOnClient(client -> screen(client).tooltip()) == null, "scale " + s + ": no tooltip as the pointer arrives");
			rest(context, 399);
			check(context.computeOnClient(client -> screen(client).tooltip()) == null, "scale " + s + ": none after 399 ms of rest");
			rest(context, 1);
			Path shot = context.takeScreenshot("t2.3c-tooltip-corner-scale-" + s);
			TooltipLayout.Box box = context.computeOnClient(client -> screen(client).tooltip());
			int[] size = context.computeOnClient(client -> new int[] {screen(client).width, screen(client).height});
			check(box != null, "scale " + s + ": hovering the button shows its tooltip after 400 ms");
			// Anchored beside the button (REQ-UI-44): at the window's right edge it flips to the button's left, 6 GUI px away.
			int[] button = context.computeOnClient(client -> {
				Widget corner = screen(client).corner;
				return new int[] {corner.x(), corner.y(), corner.width(), corner.height()};
			});
			check(box.x() + box.width() + TooltipLayout.GAP == button[0], "scale " + s + ": left of the button, 6 GUI px away: " + box);
			check(box.y() + box.height() <= size[1] - TooltipLayout.MARGIN && box.y() <= button[1], "scale " + s + ": beside it, inside the window");
			check(box.lines().size() == 6, "scale " + s + ": six lines: " + box.lines());
			// AC-UI-42 [C] (T3.31): its shadow darkens beside it; past the blur the screen is untouched, and its own fill is too.
			var image = HairlineProbe.read(shot);
			// Its left padding at mid-height, away from its rounded corners.
			int ownFill = image.getRGB((box.x() + 2) * s, (box.y() + box.height() / 2) * s) & 0xFFFFFF;
			// The tooltip sits at the window's bottom right corner here, so its shadow is read on its left side.
			int shadowY = (box.y() + box.height() / 2) * s;
			int justLeft = image.getRGB((box.x() - 1) * s, shadowY) & 0xFFFFFF;
			int pastBlur = image.getRGB((box.x() - com.k8bas.skyblockutility.ui.render.Shapes.SHADOW_BLUR - 1) * s, shadowY) & 0xFFFFFF;
			check(luminance(justLeft) < luminance(pastBlur), "scale " + s + ": a shadow beside the tooltip: " + Integer.toHexString(justLeft)
					+ " beside " + Integer.toHexString(pastBlur));
			check(ownFill == (Theme.SIDEBAR & 0xFFFFFF), "scale " + s + ": the shadow is not over the tooltip: " + Integer.toHexString(ownFill));
			if (s <= 2) {
				// AC-UI-41 [C] (T3.30): the tooltip takes the control radius, 4 GUI px.
				String corner = com.k8bas.skyblockutility.ui.render.RadiusProbe.check(HairlineProbe.read(shot), "the tooltip's corner at scale " + s,
						box.x() * s, box.y() * s, box.height() * s, com.k8bas.skyblockutility.ui.render.Shapes.RADIUS_CONTROL * s);
				check(corner.isEmpty(), corner);
			}
			if (s >= 2) {
				// AC-UI-34 [C] (T3.23): the tooltip's outline is a hairline, measured down from its top edge's middle.
				String line = HairlineProbe.check(HairlineProbe.read(shot), "the tooltip's outline", s, (box.x() + box.width() / 2) * s, box.y() * s, 0, 1,
						Theme.SEPARATOR);
				check(line.isEmpty(), line);
			}
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
		// None while a press is held (it would cover a drag), however long it rests; after the release the delay starts again.
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		try {
			context.waitTicks(2);
			check(context.computeOnClient(client -> screen(client).tooltip()) == null, "no tooltip while the button is held");
			rest(context, 1_000);
			check(context.computeOnClient(client -> screen(client).tooltip()) == null, "none after a second held");
		} finally {
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
		}
		rest(context, 399);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "399 ms after the release, none yet");
		rest(context, 1);
		check(context.computeOnClient(client -> screen(client).tooltip()) != null, "400 ms after the release it shows");
		// A short press, released after a move inside the button 100 ms after the tooltip showed: the delay starts again, not at once.
		int[] nudged = context.computeOnClient(client -> {
			int[] centre = screen(client).centre(screen(client).corner);
			return new int[] {centre[0] + 4, centre[1]};
		});
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		try {
			rest(context, 100);
		} finally {
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		}
		// Moved before the next frame, so that frame sees the pointer arrive: only the release's restart holds the tooltip back.
		context.getInput().setCursorPos(nudged[0], nudged[1]);
		context.waitTicks(2);
		rest(context, 399);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "399 ms after a short press's release, none yet");
		rest(context, 1);
		check(context.computeOnClient(client -> screen(client).tooltip()) != null, "400 ms after it, it shows");
		// On to a label drawn by the screen, which asks for its own: at once, as one showed just now (within 300 ms).
		move(context, ListTooltipTestScreen::label);
		TooltipLayout.Box label = context.computeOnClient(client -> screen(client).tooltip());
		check(label != null && label.lines().equals(java.util.List.of("The whole label")), "the label's tooltip at once: " + label);
		int labelRight = context.computeOnClient(client -> ListTooltipTestScreen.LABEL_X + client.font.width("Label"));
		// Right of it, or flipped to its left where the window (GUI scale 4 here) has no room on the right.
		boolean beside = label.x() == labelRight + TooltipLayout.GAP || label.x() + label.width() + TooltipLayout.GAP == ListTooltipTestScreen.LABEL_X;
		check(beside && label.y() == ListTooltipTestScreen.LABEL_Y, "beside the label: " + label);
		// A press no widget takes (on the label): none either while it is held.
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		try {
			rest(context, 1_000);
			check(context.computeOnClient(client -> screen(client).tooltip()) == null, "none while a press on the label is held");
		} finally {
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
		}
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "control: away from the button there is no tooltip");
		LOGGER.info("tooltip at the corner:{}", log);
	}

	/** AC-UI-37 [C] (REQ-UI-36, T3.27): the list's scrollbar idle, hovered and dragged at GUI scales 1-4. */
	private static void scrollbarStates(ClientGameTestContext context) {
		for (int scale = 1; scale <= 4; scale++) {
			int s = scale;
			context.runOnClient(client -> client.options.guiScale().set(s));
			context.waitTicks(3);
			for (String state : java.util.List.of("idle", "hovered", "dragged")) {
				if (state.equals("idle")) {
					context.getInput().setCursorPos(1100, 900);
					context.waitTicks(2);
				} else {
					move(context, ListTooltipTestScreen::thumbCentre);
				}
				if (state.equals("dragged")) {
					// Held on the thumb, the pointer moved off it sideways: still dragged, the scroll where it was.
					context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
					context.waitTick();
					move(context, screen -> new int[] {screen.visibleRow(0)[0], screen.thumbCentre()[1]});
				}
				java.awt.image.BufferedImage shot = com.k8bas.skyblockutility.ui.render.HairlineProbe.read(
						context.takeScreenshot("t3.27-list-" + state + "-scale-" + s));
				int[] g = context.computeOnClient(client -> {
					VirtualList list = screen(client).list;
					VirtualRows.Thumb thumb = list.rows().thumb(list.height);
					return new int[] {list.x + list.width, list.y, list.y + list.height, list.y + thumb.offset(), thumb.length()};
				});
				if (state.equals("dragged")) {
					context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
					context.waitTick();
				}
				String problem = ScrollbarProbe.check(shot, "the list's " + state + " scrollbar", s, g[0], g[1], g[2], g[3], g[4], !state.equals("idle"));
				check(problem.isEmpty(), problem);
			}
		}
		context.runOnClient(client -> screen(client).list.setScroll(0));
		context.getInput().setCursorPos(1100, 900);
		context.waitTicks(2);
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
		rest(context, 400);
		TooltipLayout.Box row = context.computeOnClient(client -> screen(client).tooltip());
		check(row != null && row.lines().equals(java.util.List.of("Row 11 in full")), "the hovered row's tooltip follows the scroll: " + row);
		// Beside its row, right of the list's rows (REQ-UI-44).
		int[] rowPlace = context.computeOnClient(client -> {
			VirtualList list = screen(client).list;
			return new int[] {list.x + list.width - VirtualList.SCROLLBAR, list.y + list.rows().rowTop(11)};
		});
		check(row.x() == rowPlace[0] + TooltipLayout.GAP && row.y() == rowPlace[1], "beside row 11 at " + rowPlace[0] + "," + rowPlace[1] + ": " + row);
		// EC-UI-26: the wheel turns while it shows (the pointer moving a little too): it hides, and the delay starts again.
		int[] at = context.computeOnClient(client -> screen(client).visibleRow(2));
		context.getInput().scroll(-1.0);
		context.getInput().setCursorPos(at[0] + 2, at[1]);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "the wheel hid it");
		rest(context, 399);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "399 ms after the wheel, none yet");
		rest(context, 1);
		TooltipLayout.Box next = context.computeOnClient(client -> screen(client).tooltip());
		check(next != null && next.lines().equals(java.util.List.of("Row 14 in full")), "after the delay, the row now under the pointer: " + next);
		context.getInput().scroll(1.0);
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		rest(context, 400);
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

	/** AC-UI-45 [C] (T3.34): with motion on, the tooltip fades in over 100 ms from the end of the delay. */
	private static void fadesIn(ClientGameTestContext context) {
		context.setScreen(() -> new ListTooltipTestScreen(UiTime.of(CLOCK::get, () -> true)));
		context.waitForScreen(ListTooltipTestScreen.class);
		arrive(context, screen -> screen.centre(screen.corner));
		rest(context, 400);
		float start = context.computeOnClient(client -> screen(client).tooltipAlpha());
		check(context.computeOnClient(client -> screen(client).tooltip()) != null && start == 0F, "at 400 ms it shows, fully transparent: " + start);
		rest(context, 50);
		float half = context.computeOnClient(client -> screen(client).tooltipAlpha());
		check(Math.abs(half - 0.5F) < 1e-4, "half way at 450 ms: " + half);
		rest(context, 50);
		float full = context.computeOnClient(client -> screen(client).tooltipAlpha());
		check(full == 1F, "opaque at 500 ms: " + full);
		LOGGER.info("tooltip fade: {} at 400 ms, {} at 450, {} at 500", start, half, full);
		// AC-UI-46 (REQ-UI-45, T3.35): a list row's hover highlight eases in, as a card's: its colour 75 ms in lies between its own
		// and the hover colour, and is the hover colour at 150 ms.
		int[] plain = context.computeOnClient(client -> {
			VirtualList list = screen(client).list;
			return new int[] {ListTooltipTestScreen.windowX(list.x + 120), ListTooltipTestScreen.windowY(list.y + 2)};
		});
		move(context, screen -> screen.visibleRow(0));
		int atOnce = pixel(context.takeScreenshot("t3.35-row-hover-at-0ms"), plain);
		rest(context, 75);
		int midway = pixel(context.takeScreenshot("t3.35-row-hover-at-75ms"), plain);
		rest(context, 75);
		int hovered = pixel(context.takeScreenshot("t3.35-row-hover-at-150ms"), plain);
		LOGGER.info("row hover: {} at 0 ms, {} at 75, {} at 150", Integer.toHexString(atOnce), Integer.toHexString(midway), Integer.toHexString(hovered));
		check(atOnce == (Theme.CARD & 0xFFFFFF), "the row's own colour as the pointer arrives: " + Integer.toHexString(atOnce));
		check(midway != atOnce && midway != (Theme.CARD_HOVER & 0xFFFFFF) && luminance(midway) > luminance(atOnce)
				&& luminance(midway) < luminance(Theme.CARD_HOVER & 0xFFFFFF), "75 ms in, between: " + Integer.toHexString(midway));
		check(hovered == (Theme.CARD_HOVER & 0xFFFFFF), "the hover colour at 150 ms: " + Integer.toHexString(hovered));
	}

	/**
	 * The gametest hook (T3.34) for AC-UI-29 and AC-UI-49: a tooltip box placed at a given rectangle is drawn there, whatever is
	 * hovered, and taken away again.
	 */
	private static void placedBox(ClientGameTestContext context) {
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		context.runOnClient(client -> screen(client).placeTooltipForTests("Placed", 220, 100, 60, 17));
		context.waitTicks(2);
		Path shot = context.takeScreenshot("t3.34-placed-tooltip");
		TooltipLayout.Box box = context.computeOnClient(client -> screen(client).tooltip());
		check(box != null && box.x() == 220 && box.y() == 100 && box.width() == 60 && box.height() == 17 && box.lines().equals(java.util.List.of("Placed")),
				"the placed box: " + box);
		int[] inBox = context.computeOnClient(client -> new int[] {ListTooltipTestScreen.windowX(250), ListTooltipTestScreen.windowY(101)});
		int background = pixel(shot, inBox);
		check(background == (Theme.SIDEBAR & 0xFFFFFF), "it is drawn there: " + Integer.toHexString(background));
		context.runOnClient(client -> screen(client).placeTooltipForTests(null, 0, 0, 0, 0));
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).tooltip()) == null, "taken away, nothing hovered shows none");
	}

	/** Puts the pointer on a place with no tooltip shown in the last 300 ms, so the next one waits the whole delay. */
	private static void arrive(ClientGameTestContext context, Locate where) {
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		rest(context, 1_000);
		move(context, where);
	}

	/** Steps the screen's clock and lets two frames run. */
	private static void rest(ClientGameTestContext context, long millis) {
		CLOCK.addAndGet(millis);
		context.waitTicks(2);
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

	private static int luminance(int rgb) {
		return (rgb >> 16 & 0xFF) * 3 + (rgb >> 8 & 0xFF) * 6 + (rgb & 0xFF);
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
