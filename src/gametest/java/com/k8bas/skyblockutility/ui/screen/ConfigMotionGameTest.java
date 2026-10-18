package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.gametest.ModuleStates;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.module.mobhighlighter.MobRulesForTests;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.render.CornerTextures;
import com.k8bas.skyblockutility.ui.render.HairlineProbe;
import com.k8bas.skyblockutility.ui.render.ScaleAbout;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiTime;
import com.k8bas.skyblockutility.ui.widget.ScrollArea;
import com.k8bas.skyblockutility.ui.widget.Slider;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
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
 *   <li>AC-UI-40 (REQ-UI-39, T3.24): the screen runs on the source the test gives it (a test clock, motion on) with
 *       -Dk8bas.ui.motion=off set, which turns the game's source off; a toggle's knob follows that test clock. With
 *       Animations OFF (a source whose motion reads the switch) the panel is at full size and a category shows unslid
 *       from the first frame, and the first frame after a toggle click shows the knob at its end; switched back ON in
 *       the open screen, the next click slides again.</li>
 *   <li>AC-UI-35 [C] (REQ-UI-34, T3.25): 80 ms into the switch the page is laid out 3 GUI px down but drawn 2.5 down, on the
 *       physical-pixel grid, which the screenshot shows at the card's top edge, and the section line above it keeps its
 *       settled distance to the card; during the slide, clicks at the card's inner top and bottom edges reach it and one
 *       1 px above reaches nothing (AC-UI-03). The toggle knob mid-slide is drawn on the grid, a click on the switch then
 *       reaches it, and its knob turns back from where it was.</li>
 *   <li>AC-UI-36 [C] and EC-UI-25 (REQ-UI-35, T3.26): a wheel notch over the page glides on the test clock (still at 0 ms,
 *       strictly between at 50 ms, at 24 GUI px from 400 ms on), and a press during a glide stops it where the page was
 *       drawn.</li>
 *   <li>AC-UI-39 [C] (REQ-UI-38, T3.29): the dropdown list, the colour picker and the database picker, screenshotted at 0,
 *       60 and 120 ms after opening: a point inside each, and the modal backdrop, are unchanged at 0 ms, between at 60 ms and
 *       at their end from 120 ms on; closed, each fades out and is gone after 90 ms (changed by R47, AC-UI-59); a click on a
 *       dropdown entry 10 ms after opening picks that entry.
 *       With Animations OFF a dropdown list is fully drawn in its first frame (AC-UI-40).</li>
 *   <li>REQ-UI-47 (Checkpoint G3u): at GUI scale 4, frame by frame, a slider's knob growing on hover and the content area's
 *       scrollbar widening each add at most one mask to the cache, their grown size's, not one per size they pass.</li>
 * </ul>
 */
public class ConfigMotionGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleStates modules = context.computeOnClient(client -> ModuleStates.capture());
		String start = "k8bas config motion start " + System.nanoTime();
		String end = "k8bas config motion end " + System.nanoTime();
		AtomicLong clock = new AtomicLong(10_000);
		UiTime time = UiTime.of(clock::get, () -> true);
		context.getInput().resizeWindow(1280, 960);
		try {
			context.runOnClient(client -> client.options.guiScale().set(2));
			LOGGER.info(start);
			context.setScreen(() -> new ConfigScreen(Minecraft.getInstance().gui.screen(), OptionCatalog.live(), time));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(3);
			duringTheOpen(context, clock);
			clock.addAndGet(1_000);
			context.waitTicks(2);
			check(!context.computeOnClient(client -> screen(client).motion().animating()), "the open has ended");
			duringASwitch(context, clock, time);
			anOverlaySettlesTheMotion(context);
			clock.addAndGet(1_000);
			context.waitTicks(2);
			check(!context.computeOnClient(client -> screen(client).motion().animating()), "settled");
			tabIndicatorSlides(context, clock);
			listHoversEase(context, clock);
			theWheelGlides(context, clock, time);
			overlaysFadeIn(context, clock);
			overlaysFadeOut(context, clock);
			growingShapesBorrowMasks(context, clock);
			ruleCardsOpenAndClose(context, clock, time);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			check(!(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen), "Esc closes the screen");
			withAnimationsOff(context, clock);
			context.waitTicks(5);
			LOGGER.info(end);
			List<String> errors = errorsBetween(context, start, end);
			check(errors.isEmpty(), "no exception or render error logged during the motion: " + errors);
		} finally {
			context.runOnClient(client -> {
				modules.restore();
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
			// Brought into view at once: on the still test clock a glide would not move (T3.26).
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
			screen(client).scrollArea().setScroll(screen(client).scrollArea().target());
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
	private static void duringASwitch(ClientGameTestContext context, AtomicLong clock, UiTime time) {
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

		// AC-UI-35: at 80 ms 2.592 GUI px of slide are left, laid out as 3 and drawn as 2.5 at GUI scale 2.
		clock.addAndGet(40);
		context.waitTicks(2);
		float drawnSlide = context.computeOnClient(client -> screen(client).motion().slideDrawn(Shapes.guiScale()));
		int laidSlide = context.computeOnClient(client -> screen(client).motion().slideOffset());
		float openScale = context.computeOnClient(client -> screen(client).motion().scale());
		check(laidSlide == 3 && drawnSlide == 2.5F && openScale == 1F, "80 ms in: laid out " + laidSlide + ", drawn " + drawnSlide + ", scale "
				+ openScale);
		int[] sliding = cardAndLine(context, context.takeScreenshot("t3.25-switch-at-80ms"));
		check(sliding[0] == sliding[2] - 1, "the card's top edge is drawn half a GUI px above its whole-px place: physical row " + sliding[0]
				+ ", laid out at " + sliding[2]);
		// AC-UI-03 during the slide, at the whole-px layout: the card's inner top and bottom rows reach it, 1 px above nothing.
		String unclicked = context.computeOnClient(client -> settings());
		int[][] edges = context.computeOnClient(client -> {
			Widget card = screen(client).page().card(toggleRow(screen(client)).option());
			return new int[][] {{card.x() + 12, card.y()}, {card.x() + 12, card.y() + card.height() - 1}, {card.x() + 12, card.y() - 1}};
		});
		for (int i = 0; i < 2; i++) {
			click(context, edges[i]);
			check(!context.computeOnClient(client -> settings()).equals(unclicked), "a click on the sliding card's inner edge " + i + " reaches it");
			click(context, edges[i]);
			check(context.computeOnClient(client -> settings()).equals(unclicked), "and back");
		}
		click(context, edges[2]);
		check(context.computeOnClient(client -> settings()).equals(unclicked), "1 px above the sliding card reaches nothing");
		check(context.computeOnClient(client -> screen(client).motion().slideOffset()) == 3, "still sliding");

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
		// AC-UI-40: from a settled knob, one click slides it on the screen's test clock, which stands still until the test
		// steps it, while -Dk8bas.ui.motion=off holds the game's source still.
		check(!context.computeOnClient(client -> UiClock.GAME.motion()), "-Dk8bas.ui.motion=off turns the game's source off");
		clock.addAndGet(1_000);
		context.waitTicks(2);
		boolean on = context.computeOnClient(client -> toggleValue(screen(client)));
		float from = on ? 1F : 0F;
		check(context.computeOnClient(client -> knob(screen(client), time)) == from, "the knob has settled");
		click(context, title);
		float clicked = context.computeOnClient(client -> knob(screen(client), time));
		clock.addAndGet(75);
		context.waitTicks(2);
		float halfway = context.computeOnClient(client -> knob(screen(client), time));
		check(clicked == from && halfway > 0F && halfway < 1F, "the knob follows the test clock: " + clicked + ", " + halfway);
		// AC-UI-35: mid-slide the knob is drawn on the physical-pixel grid, at the eased position written out here.
		BufferedImage knobShot = HairlineProbe.read(context.takeScreenshot("t3.25-knob-sliding"));
		int[] knobBox = context.computeOnClient(client -> {
			Widget toggle = screen(client).page().control(toggleRow(screen(client)).option());
			return new int[] {toggle.x(), toggle.y(), toggle.width(), toggle.height(), Shapes.guiScale()};
		});
		int knobScale = knobBox[4];
		int knobPx = (knobBox[3] - 4) * knobScale;
		int knobLeft = Math.round((knobBox[0] + 2 + (knobBox[2] - 4 - (knobBox[3] - 4)) * halfway) * knobScale);
		int knobRow = (knobBox[1] + 2) * knobScale + knobPx / 2;
		int knobColour = knobShot.getRGB(knobLeft + knobPx / 2, knobRow);
		int runLeft = knobLeft + knobPx / 2;
		while (runLeft > knobLeft - 4 && near(knobShot.getRGB(runLeft - 1, knobRow), knobColour)) {
			runLeft--;
		}
		int runRight = knobLeft + knobPx / 2;
		while (runRight < knobLeft + knobPx + 4 && near(knobShot.getRGB(runRight + 1, knobRow), knobColour)) {
			runRight++;
		}
		check(runLeft + runRight + 1 == 2 * knobLeft + knobPx, "the sliding knob is centred on " + (knobLeft + knobPx / 2.0) + ": its row runs "
				+ runLeft + " to " + runRight);
		// AC-UI-03 during the knob's slide: a click on the switch itself reaches it, and the knob turns back from where it is.
		int[] control = context.computeOnClient(client -> {
			Widget toggle = screen(client).page().control(toggleRow(screen(client)).option());
			return new int[] {toggle.x() + toggle.width() / 2, toggle.y() + toggle.height() / 2};
		});
		click(context, control);
		check(context.computeOnClient(client -> settings()).equals(before), "a click on the sliding switch flips it back");
		float turned = context.computeOnClient(client -> knob(screen(client), time));
		clock.addAndGet(200);
		context.waitTicks(2);
		float ended = context.computeOnClient(client -> knob(screen(client), time));
		check(turned == halfway && ended == from, "the knob turns back from " + halfway + ": " + turned + ", then " + ended);
		// Settled, the section line lies as far above the card as it was drawn during the slide: page text and widgets moved together.
		int[] settled = cardAndLine(context, context.takeScreenshot("t3.25-switch-settled"));
		check(settled[0] == settled[2] && settled[0] - settled[1] == sliding[0] - sliding[1], "the section line keeps its distance to the card: "
				+ (sliding[0] - sliding[1]) + " px sliding, " + (settled[0] - settled[1]) + " settled");
	}

	/**
	 * AC-UI-40 [C]: with Animations OFF everything is at its end at once, on a source whose motion reads the switch as the
	 * game's does without the gametests' property; the clock stands still throughout, so anything still moving would show.
	 */
	private static void withAnimationsOff(ClientGameTestContext context, AtomicLong clock) {
		Boolean before = context.computeOnClient(client -> ConfigManager.general().animations);
		UiTime time = UiTime.of(clock::get, () -> ConfigManager.general().animations());
		try {
			context.runOnClient(client -> ConfigManager.general().animations = false);
			check(!context.computeOnClient(client -> ConfigManager.general().animations()), "Animations reads OFF");
			context.setScreen(() -> new ConfigScreen(Minecraft.getInstance().gui.screen(), OptionCatalog.live(), time));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(2);
			float scale = context.computeOnClient(client -> screen(client).motion().scale());
			check(scale == 1F, "the panel is at full size from its first frame: " + scale);
			context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
			context.waitTicks(2);
			int slide = context.computeOnClient(client -> screen(client).motion().slideOffset());
			int veil = context.computeOnClient(client -> screen(client).motion().veilAlpha());
			check(slide == 0 && veil == 0, "a category shows unslid at once: slide " + slide + ", veil " + veil);
			int[] title = context.computeOnClient(client -> {
				Widget card = screen(client).page().card(toggleRow(screen(client)).option());
				return new int[] {card.x() + 12, card.y() + card.height() / 2};
			});
			boolean was = context.computeOnClient(client -> toggleValue(screen(client)));
			context.getInput().setCursorPos(context.computeOnClient(client -> window(title[0], title[1]))[0],
					context.computeOnClient(client -> window(title[0], title[1]))[1]);
			context.waitTicks(2);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			// The first frame after the click.
			context.waitTick();
			Path shot = context.takeScreenshot("t3.24-animations-off-toggle");
			boolean now = context.computeOnClient(client -> toggleValue(screen(client)));
			check(now != was, "the click flipped the toggle");
			float knob = context.computeOnClient(client -> knob(screen(client), time));
			check(knob == (now ? 1F : 0F), "the knob is at its end at once: " + knob);
			int[] ends = context.computeOnClient(client -> {
				Widget control = screen(client).page().control(toggleRow(screen(client)).option());
				int knobSize = control.height() - 4;
				int off = control.x() + 2 + knobSize / 2;
				int on = control.x() + control.width() - 2 - knobSize / 2 - 1;
				return new int[] {now ? on : off, now ? off : on, control.y() + control.height() / 2};
			});
			int atEnd = pixel(context, shot, ends[0], ends[2]);
			int atStart = pixel(context, shot, ends[1], ends[2]);
			int knobColour = (now ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY) & 0xFFFFFF;
			check(atEnd == knobColour && atStart != knobColour, "the screenshot shows the knob at its end: " + Integer.toHexString(atEnd) + " at the end, "
					+ Integer.toHexString(atStart) + " at the start");

			// AC-UI-40 [C]: with Animations OFF a dropdown list is fully drawn in its first frame.
			context.runOnClient(client -> screen(client).select(Category.GENERAL));
			context.waitTicks(2);
			int[] field = controlOf(context, "interface.notice_position");
			Path closedList = context.takeScreenshot("t3.24-list-closed");
			click(context, field);
			int[] listBox = context.computeOnClient(client -> screen(client).overlay().bounds());
			Path firstFrame = context.takeScreenshot("t3.24-list-first-frame");
			clock.addAndGet(1_000);
			context.waitTicks(2);
			Path laterFrame = context.takeScreenshot("t3.24-list-later");
			int[] inList = evenChange(context, closedList, laterFrame, listBox);
			check(inList != null, "the list changes an even patch of the screen");
			int first = pixel(context, firstFrame, inList[0], inList[1]);
			int later = pixel(context, laterFrame, inList[0], inList[1]);
			check(first == later, "with Animations OFF the list is fully drawn at once: " + Integer.toHexString(first) + ", later "
					+ Integer.toHexString(later));
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
			context.waitTicks(2);

			// Switched ON in the open screen, the next click slides on the still clock: the knob stays at its start.
			context.runOnClient(client -> ConfigManager.general().animations = true);
			click(context, title);
			float start = context.computeOnClient(client -> knob(screen(client), time));
			check(start == (now ? 1F : 0F), "ON again applies at once: the knob starts its slide " + start);
			click(context, title);
			context.setScreen(() -> null);
		} finally {
			context.runOnClient(client -> ConfigManager.general().animations = before);
		}
	}

	/** The first toggle's knob as the screen's frames see it: read on the screen's source, as they are drawn. */
	/**
	 * In the screenshot, on a column near the toggle card's right end: the physical row of the card's top edge, of the section
	 * line above it, and of the card's whole-px top. Each is the first row that differs from the panel between them, going
	 * away from it.
	 */
	private static int[] cardAndLine(ClientGameTestContext context, Path shot) {
		int[] card = context.computeOnClient(client -> {
			Widget box = screen(client).page().card(toggleRow(screen(client)).option());
			int scale = Shapes.guiScale();
			return new int[] {(box.x() + box.width() - 10) * scale, box.y() * scale, scale};
		});
		BufferedImage image = HairlineProbe.read(shot);
		int column = card[0];
		int gap = card[1] - 2 * card[2];
		int panel = image.getRGB(column, gap);
		int top = gap;
		while (top < card[1] + 2 * card[2] && image.getRGB(column, top) == panel) {
			top++;
		}
		int line = gap;
		while (line > gap - 30 * card[2] && image.getRGB(column, line) == panel) {
			line--;
		}
		return new int[] {top, line, card[1]};
	}

	private static boolean near(int a, int b) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > 6) {
				return false;
			}
		}
		return true;
	}

	private static float knob(ConfigScreen screen, UiTime time) {
		UiTime previous = UiClock.enter(time);
		try {
			return ((ToggleSwitch) screen.page().control(toggleRow(screen).option())).knob();
		} finally {
			UiClock.leave(previous);
		}
	}

	/**
	 * AC-UI-36 [C] (REQ-UI-35, T3.26) at GUI scale 2 on the test clock: 3 notches screenshotted at 0, 16, 50 and 400 ms show a
	 * card drawn at a growing offset, on the physical-pixel grid, that ends at 72 GUI px; a click 30 ms into a glide reaches
	 * the toggle drawn under the pointer and stops the glide there (EC-UI-25); Tab to a control below the view glides to it.
	 */
	private static void theWheelGlides(ClientGameTestContext context, AtomicLong clock, UiTime time) {
		// The switch starts on the next frame; then the clock steps past it.
		context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
		context.waitTicks(2);
		clock.addAndGet(1_000);
		context.waitTicks(2);
		check(!context.computeOnClient(client -> screen(client).motion().animating()), "the switch to Waypoints has ended");
		context.runOnClient(client -> onScreen(time, () -> {
			screen(client).scrollArea().setScroll(0);
			return null;
		}));
		context.waitTicks(2);
		// The content's left padding: the scroll area's own, under no card or control.
		int[] point = context.computeOnClient(client -> {
			ConfigLayout.Rect content = screen(client).frame().content();
			return new int[] {content.x() + 2, content.y() + content.h() / 2};
		});
		check(context.computeOnClient(client -> screen(client).page().widgets().stream().noneMatch(w -> w.contains(point[0] + 0.5,
				point[1] + 0.5))), "nothing but the scroll area lies under the pointer");
		check(context.computeOnClient(client -> screen(client).scrollArea().maxScroll()) >= 200, "Waypoints scrolls far enough");
		// A card well inside the view, whose top edge the screenshots follow: its laid-out top at scroll 0, and a column on it.
		int[] probe = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.Rect content = screen.frame().content();
			for (ConfigLayout.Row row : screen.page().page().rows()) {
				if (row instanceof ConfigLayout.OptionRow option) {
					Widget card = screen.page().card(option.option());
					if (card.y() >= content.y() + 100 && card.y() <= content.bottom() - 10) {
						return new int[] {card.x() + card.width() - 10, card.y()};
					}
				}
			}
			throw new AssertionError("FAILED: no card to follow");
		});
		int[] window = context.computeOnClient(client -> window(point[0], point[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTicks(2);
		for (int i = 0; i < 3; i++) {
			context.getInput().scroll(-1.0);
			context.waitTick();
		}
		int target = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().target()));
		check(target == 72, "3 notches: target " + target);
		long base = clock.get();
		float last = -1;
		for (long t : new long[] {0, 16, 50, 400}) {
			clock.set(base + t);
			context.waitTicks(2);
			BufferedImage shot = HairlineProbe.read(context.takeScreenshot("t3.26-wheel-at-" + t + "ms"));
			float drawn = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().drawnScroll(Shapes.guiScale())));
			int scale = context.computeOnClient(client -> Shapes.guiScale());
			int column = probe[0] * scale;
			int row = Math.round((probe[1] - drawn) * scale);
			boolean edge = shot.getRGB(column, row) != shot.getRGB(column, row - 1) && shot.getRGB(column, row - 1) == shot.getRGB(column, row - 2);
			check(drawn > last && edge, t + " ms: the card's top edge is drawn at physical row " + row + " for a scroll of " + drawn
					+ " (before: " + last + ")");
			last = drawn;
		}
		check(last == 72F, "it ends at 72: " + last);

		// Back up: 30 ms into the glide, a click reaches the toggle drawn under the pointer, and the glide stops there.
		for (int i = 0; i < 3; i++) {
			context.getInput().scroll(1.0);
			context.waitTick();
		}
		clock.addAndGet(30);
		context.waitTicks(2);
		int at30 = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().scroll()));
		check(at30 > 0 && at30 < 72, "30 ms into the glide up: " + at30);
		int[] toggle = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.Rect content = screen.frame().content();
			for (ConfigLayout.Row row : screen.page().page().rows()) {
				if (row instanceof ConfigLayout.OptionRow option && option.option() instanceof Toggle) {
					Widget card = screen.page().card(option.option());
					if (card.y() >= content.y() + 4 && card.y() + card.height() <= content.bottom() - 4) {
						return new int[] {card.x() + 12, card.y() + card.height() / 2};
					}
				}
			}
			throw new AssertionError("FAILED: no toggle card in view");
		});
		String before = context.computeOnClient(client -> settings());
		click(context, toggle);
		check(!context.computeOnClient(client -> settings()).equals(before), "the click at 30 ms flipped the toggle drawn under the pointer");
		clock.addAndGet(400);
		context.waitTicks(2);
		int after = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().scroll()));
		int stopped = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().target()));
		check(after == at30 && stopped == at30, "the click stopped the glide where the page was drawn: " + at30 + ", then " + after
				+ " (target " + stopped + ")");
		click(context, toggle);
		check(context.computeOnClient(client -> settings()).equals(before), "and back");

		// AC-UI-36: Tab to a control below the view sets the target to show it, and the page glides there.
		context.runOnClient(client -> onScreen(time, () -> {
			screen(client).scrollArea().setScroll(0);
			return null;
		}));
		context.waitTicks(2);
		int from = -1;
		int goal = -1;
		for (int i = 0; i < 80 && goal <= from; i++) {
			context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
			context.waitTicks(2);
			from = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().scroll()));
			goal = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().target()));
		}
		check(goal > from, "a Tab reached a control below the view: target " + goal + ", at " + from);
		clock.addAndGet(50);
		context.waitTicks(2);
		int tab50 = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().scroll()));
		clock.addAndGet(350);
		context.waitTicks(2);
		int tab400 = context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().scroll()));
		check(tab50 > from && tab50 < goal && tab400 == goal, "Tab glides from " + from + " to " + goal + ": " + tab50 + " at 50 ms, "
				+ tab400 + " at 400 ms");
		// The keyboard focus goes, so the Esc that follows closes the screen.
		click(context, point);
		aKeyOpenedListStaysOpen(context, clock, time);
	}

	/**
	 * A dropdown half out of view, reached with Tab and opened with Enter while its reveal would glide (the clock stands
	 * still): the page shows its box at once, so the list stays anchored and open.
	 */
	private static void aKeyOpenedListStaysOpen(ClientGameTestContext context, AtomicLong clock, UiTime time) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		clock.addAndGet(1_000);
		context.waitTicks(2);
		check(!context.computeOnClient(client -> screen(client).motion().animating()), "the switch to General has ended");
		context.runOnClient(client -> onScreen(time, () -> {
			ConfigScreen screen = screen(client);
			screen.scrollArea().setScroll(0);
			return null;
		}));
		context.waitTicks(2);
		context.runOnClient(client -> onScreen(time, () -> {
			ConfigScreen screen = screen(client);
			Widget control = screen.page().control(choiceRow(screen).option());
			// Half above the content's top edge.
			screen.scrollArea().setScroll(control.y() - screen.frame().content().y() + control.height() / 2);
			return null;
		}));
		context.waitTicks(2);
		boolean reached = false;
		for (int i = 0; i < 40 && !reached; i++) {
			context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
			context.waitTicks(2);
			reached = context.computeOnClient(client -> screen(client).navFocus() == screen(client).page().control(choiceRow(screen(client)).option()));
		}
		check(reached, "Tab reached the dropdown");
		check(context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().scroll()
				!= screen(client).scrollArea().target())), "its reveal would glide on the still clock");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(3);
		check(context.computeOnClient(client -> screen(client).overlay()) != null, "the list Enter opened stays open");
		check(context.computeOnClient(client -> onScreen(time, () -> screen(client).scrollArea().scroll()
				== screen(client).scrollArea().target())), "the page shows the box at once");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).overlay()) == null, "Esc closed the list");
		context.runOnClient(client -> screen(client).focus(null));
		context.waitTicks(1);
	}

	/** AC-UI-39 [C]: each overlay fades and scales in on the test clock, and (R47) fades out after it closes. */
	private static void overlaysFadeIn(ClientGameTestContext context, AtomicLong clock) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		clock.addAndGet(1_000);
		context.waitTicks(2);
		fadeIn(context, clock, "dropdown", "interface.notice_position", false);
		fadeIn(context, clock, "colour-picker", "interface.accent", true);
		context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
		context.waitTicks(2);
		clock.addAndGet(1_000);
		context.waitTicks(2);
		fadeIn(context, clock, "database-picker", "npc_search.add_from_database", true);

		// A click on a dropdown entry 10 ms after opening picks that entry; the list takes no more input at once.
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		clock.addAndGet(1_000);
		context.waitTicks(2);
		var before = context.computeOnClient(client -> ConfigManager.general().noticePosition);
		check(context.computeOnClient(client -> ConfigManager.general().noticePosition()) != com.k8bas.skyblockutility.ui.notice.NoticePosition.values()[3],
				"the fourth entry is not the value yet");
		try {
			click(context, controlOf(context, "interface.notice_position"));
			clock.addAndGet(10);
			context.waitTicks(2);
			// The fourth entry's last row as laid out: scaled input (0.97 about the list's top) would meet the fifth.
			int[] entry = context.computeOnClient(client -> {
				int[] box = screen(client).overlay().bounds();
				return new int[] {(box[0] + box[2]) / 2, box[1] + com.k8bas.skyblockutility.ui.widget.Dropdown.LIST_PADDING + 4 * com.k8bas.skyblockutility.ui.widget.Dropdown.ROW_HEIGHT - 1};
			});
			int[] window = context.computeOnClient(client -> window(entry[0], entry[1]));
			context.getInput().setCursorPos(window[0], window[1]);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
			check(context.computeOnClient(client -> screen(client).overlay()) == null, "the pick closes the list at once");
			check(context.computeOnClient(client -> ConfigManager.general().noticePosition()) == com.k8bas.skyblockutility.ui.notice.NoticePosition.values()[3],
					"10 ms after opening, a click on the fourth entry picks it: " + context.computeOnClient(client -> ConfigManager.general().noticePosition()));
		} finally {
			context.runOnClient(client -> ConfigManager.general().noticePosition = before);
		}
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
	}

	/**
	 * Opens an overlay with the control {@code optionId} on the still test clock and checks a point inside it (and, for a modal,
	 * the backdrop at the screen's corner) at 0, 60 and 120 ms against the frames before it opened and long after; then closes
	 * it with Esc and checks it fades out, gone once its 90 ms have run (R47, AC-UI-59).
	 */
	private static void fadeIn(ClientGameTestContext context, AtomicLong clock, String name, String optionId, boolean modal) {
		int[] control = controlOf(context, optionId);
		Path closedShot = context.takeScreenshot("t3.29-" + name + "-before");
		click(context, control);
		check(context.computeOnClient(client -> screen(client).overlay()) != null, name + ": it opened");
		int[] box = context.computeOnClient(client -> screen(client).overlay().bounds());
		// For the backdrop: a point on the sidebar outside the overlay, which stays still (the panorama behind turns).
		int[] corner = context.computeOnClient(client -> {
			ConfigLayout.Rect sidebar = screen(client).frame().sidebar();
			return new int[] {sidebar.x() + 4, sidebar.bottom() - 4};
		});
		check(corner[0] < box[0] || corner[0] >= box[2] || corner[1] < box[1] || corner[1] >= box[3], name + ": the backdrop point is outside it");
		int cornerBefore = pixel(context, closedShot, corner[0], corner[1]);
		Path at0 = context.takeScreenshot("t3.29-" + name + "-at-0ms");
		clock.addAndGet(10);
		context.waitTicks(2);
		Path at10 = context.takeScreenshot("t3.29-" + name + "-at-10ms");
		clock.addAndGet(50);
		context.waitTicks(2);
		Path at60 = context.takeScreenshot("t3.29-" + name + "-at-60ms");
		clock.addAndGet(60);
		context.waitTicks(2);
		Path at120 = context.takeScreenshot("t3.29-" + name + "-at-120ms");
		clock.addAndGet(1_000);
		context.waitTicks(2);
		Path settled = context.takeScreenshot("t3.29-" + name + "-settled");
		// A point inside it that it changes, in a patch that is even both before and once open, so the 0.96 scale moving it by
		// a GUI px at 60 ms still samples the same.
		int[] inside = evenChange(context, closedShot, settled, box);
		check(inside != null, name + ": somewhere inside, it changes an even patch of the screen");
		int insideBefore = pixel(context, closedShot, inside[0], inside[1]);
		int insideEnd = pixel(context, settled, inside[0], inside[1]);
		check(pixel(context, at0, inside[0], inside[1]) == insideBefore, name + ": at 0 ms it does not show yet");
		// Partly shown: between the screen as it is under it at 60 ms and the open overlay. Under a modal that screen is dimmed by
		// the backdrop easing in, at the alpha the ease-out curve, written out here, gives at 60 ms: 1 - (1 - 0.5)^3 = 0.875.
		int inside60 = pixel(context, at60, inside[0], inside[1]);
		int under60 = modal ? over(insideBefore, Theme.BACKDROP & 0xFFFFFF, Math.round((Theme.BACKDROP >>> 24) * 0.875F)) : insideBefore;
		check(between(inside60, under60, insideEnd), name + ": at 60 ms it is partly shown: "
				+ Integer.toHexString(pixel(context, at60, inside[0], inside[1])) + " between " + Integer.toHexString(under60) + " and "
				+ Integer.toHexString(insideEnd));
		check(pixel(context, at120, inside[0], inside[1]) == insideEnd, name + ": at 120 ms it is fully shown");
		// AC-UI-42 [C] (T3.31): open, its shadow darkens the screen just below it, and past the blur the screen is as it was (under
		// a modal, as the backdrop leaves it).
		// The row right below it: inside the shadow's box, its offset lower, so in its full band.
		int below = box[3];
		int past = box[3] + com.k8bas.skyblockutility.ui.render.Shapes.SHADOW_BLUR + com.k8bas.skyblockutility.ui.render.Shapes.SHADOW_OFFSET;
		int shadowColumn = (box[0] + box[2]) / 2;
		int alphaBackdrop = modal ? Theme.BACKDROP >>> 24 : 0;
		int belowWas = over(pixel(context, closedShot, shadowColumn, below), Theme.BACKDROP & 0xFFFFFF, alphaBackdrop);
		int pastWas = over(pixel(context, closedShot, shadowColumn, past), Theme.BACKDROP & 0xFFFFFF, alphaBackdrop);
		int belowNow = pixel(context, settled, shadowColumn, below);
		int pastNow = pixel(context, settled, shadowColumn, past);
		// Just below it the shadow's full band: black at its alpha over the screen.
		int belowShadow = over(belowWas, 0, com.k8bas.skyblockutility.ui.render.Shapes.SHADOW_ALPHA);
		check(lighterBy(belowNow, belowShadow) <= 4 && lighterBy(belowShadow, belowNow) <= 4, name + ": a shadow under it: "
				+ Integer.toHexString(belowNow) + " for " + Integer.toHexString(belowShadow));
		check(lighterBy(pastWas, pastNow) <= 3 && lighterBy(pastNow, pastWas) <= 3, name + ": past the blur the screen is as it was: "
				+ Integer.toHexString(pastNow) + " for " + Integer.toHexString(pastWas));
		if (modal) {
			int cornerEnd = pixel(context, settled, corner[0], corner[1]);
			check(cornerEnd != cornerBefore, name + ": its backdrop dims the screen");
			check(pixel(context, at0, corner[0], corner[1]) == cornerBefore, name + ": at 0 ms the backdrop is clear");
			check(between(pixel(context, at60, corner[0], corner[1]), cornerBefore, cornerEnd), name + ": at 60 ms the backdrop is easing in");
			check(pixel(context, at120, corner[0], corner[1]) == cornerEnd, name + ": at 120 ms the backdrop is at its alpha");
		}
		for (int i = 0; i < 3 && context.computeOnClient(client -> screen(client).overlay()) != null; i++) {
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();
		}
		check(context.computeOnClient(client -> screen(client).overlay()) == null, name + ": Esc closes it");
		// Changed by R47 (AC-UI-59): closed, it fades out over 90 ms, drawn only; then the page shows where it was.
		check(context.computeOnClient(client -> screen(client).closingOverlay()) != null, name + ": closed, it fades out");
		clock.addAndGet(com.k8bas.skyblockutility.ui.widget.OverlayTransition.CLOSE_MS);
		context.waitTick();
		Path closed = context.takeScreenshot("t3.29-" + name + "-closed");
		check(pixel(context, closed, inside[0], inside[1]) == insideBefore, name + ": gone once its 90 ms fade has run");
		// Closing during the transition fades out too.
		click(context, control);
		clock.addAndGet(60);
		context.waitTicks(2);
		for (int i = 0; i < 3 && context.computeOnClient(client -> screen(client).overlay()) != null; i++) {
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();
		}
		// Changed by R47 (AC-UI-59): it fades out from where it was, and is gone 90 ms later.
		clock.addAndGet(com.k8bas.skyblockutility.ui.widget.OverlayTransition.CLOSE_MS);
		context.waitTick();
		Path closedEarly = context.takeScreenshot("t3.29-" + name + "-closed-at-60ms");
		check(pixel(context, closedEarly, inside[0], inside[1]) == insideBefore, name + ": closed at 60 ms, it is gone 90 ms later");
		if (!modal) {
			// The drawn list at 10 ms, faint but there: its top edge, at the field, stays; its bottom edge is where the scaled box puts
			// it, about 2 GUI px short of its end (at 60 ms the scale is too near 1 to tell).
			int scale = context.computeOnClient(client -> com.k8bas.skyblockutility.ui.render.Shapes.guiScale());
			BufferedImage was = HairlineProbe.read(closedShot);
			BufferedImage mid = HairlineProbe.read(at10);
			int column = (box[0] + box[2]) / 2 * scale;
			int first = -1;
			int last = -1;
			// From the list's own top row down (the field above it changes as it opens).
			for (int row = box[1] * scale; row < (box[3] + 6) * scale; row++) {
				// The list lightens what it covers (its outline and rows); its shadow below only darkens, so it does not count.
				if (lum(mid.getRGB(column, row)) > lum(was.getRGB(column, row))) {
					first = first < 0 ? row : first;
					last = row;
				}
			}
			// The ease-out curve written out: 1 - (1 - 10/120)^3 of the way from 0.96 to 1.
			float s10 = 0.96F + 0.04F * (1F - (float) Math.pow(1 - 10 / 120.0, 3));
			float drawnBottom = com.k8bas.skyblockutility.ui.render.Shapes.snap(box[1] + (box[3] - box[1]) * s10, scale);
			check(first == box[1] * scale && Math.abs(last + 1 - drawnBottom * scale) <= 1, name + ": at 10 ms the list is drawn from row " + first
					+ " to " + last + ": its top at " + box[1] * scale + " stays and its bottom is at " + drawnBottom * scale);
		}
	}

	private static int lum(int rgb) {
		return (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
	}

	/** How much lighter {@code a} is than {@code b}, summed over the channels (0 if it is not). */
	private static int lighterBy(int a, int b) {
		int d = 0;
		for (int shift = 0; shift <= 16; shift += 8) {
			d += Math.max(0, (a >> shift & 0xFF) - (b >> shift & 0xFF));
		}
		return d;
	}

	/** {@code rgb} under {@code top} at {@code alpha} (0-255): the colour a translucent fill over it shows. */
	private static int over(int rgb, int top, int alpha) {
		int out = 0;
		for (int shift = 0; shift <= 16; shift += 8) {
			int channel = Math.round(((top >> shift & 0xFF) * alpha + (rgb >> shift & 0xFF) * (255 - alpha)) / 255F);
			out |= channel << shift;
		}
		return out;
	}

	/** The first point of the box, on a 2 GUI px grid, whose 5x5 GUI px patch is even in both shots and differs between them. */
	private static int[] evenChange(ClientGameTestContext context, Path before, Path after, int[] box) {
		int[] gui = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
		BufferedImage was = HairlineProbe.read(before);
		BufferedImage now = HairlineProbe.read(after);
		for (int y = box[1] + 4; y < box[3] - 4; y += 2) {
			for (int x = box[0] + 4; x < box[2] - 4; x += 2) {
				int a = at(was, gui, x, y);
				int b = at(now, gui, x, y);
				if (a != b && even(was, gui, x, y, a) && even(now, gui, x, y, b)) {
					return new int[] {x, y};
				}
			}
		}
		return null;
	}

	private static boolean even(BufferedImage image, int[] gui, int x, int y, int rgb) {
		for (int dy = -2; dy <= 2; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				if (at(image, gui, x + dx, y + dy) != rgb) {
					return false;
				}
			}
		}
		return true;
	}

	/** The image's pixel at the middle of a GUI pixel, as {@link #pixel} reads it. */
	private static int at(BufferedImage image, int[] gui, int guiX, int guiY) {
		return image.getRGB((int) Math.floor((guiX + 0.5) * image.getWidth() / gui[0]), (int) Math.floor((guiY + 0.5) * image.getHeight() / gui[1])) & 0xFFFFFF;
	}

	/**
	 * AC-UI-59 [A]/[C] (REQ-UI-55, T3.48) at GUI scale 2 on the test clock: a dropdown entry picked takes effect at once, and
	 * the list then fades and shrinks away, drawn only: opacity 1 at 0 ms, about 0.125 at 45 ms, gone from 90 ms on; a click
	 * 10 ms after a close reaches the field under it, and the list it opens drops the closing one (EC-UI-31); the colour picker
	 * fades the same way and frees its wheel's texture only once gone; a GUI scale change drops a closing overlay at once.
	 */
	private static void overlaysFadeOut(ClientGameTestContext context, AtomicLong clock) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		clock.addAndGet(1_000);
		context.waitTicks(2);
		var before = context.computeOnClient(client -> ConfigManager.general().noticePosition);
		Integer secondsBefore = context.computeOnClient(client -> ConfigManager.general().noticeSeconds);
		int seconds = context.computeOnClient(client -> ConfigManager.general().noticeSeconds());
		var first = com.k8bas.skyblockutility.ui.notice.NoticePosition.values()[0];
		var third = com.k8bas.skyblockutility.ui.notice.NoticePosition.values()[2];
		try {
			// A pick of an entry that is not the value yet: applied at once; then the list fades out, drawn as it was at 0 ms and
			// between itself and the page at 45 ms.
			context.runOnClient(client -> ConfigManager.general().noticePosition = first);
			context.waitTick();
			check(context.computeOnClient(client -> ConfigManager.general().noticePosition()) != third, "the third entry is not the value yet");
			Path page = context.takeScreenshot("t3.48-page-before-list");
			click(context, controlOf(context, "interface.notice_position"));
			clock.addAndGet(500);
			context.waitTick();
			int[] box = context.computeOnClient(client -> screen(client).overlay().bounds());
			Path open = context.takeScreenshot("t3.48-list-open");
			int[] entry = {(box[0] + box[2]) / 2, box[1] + com.k8bas.skyblockutility.ui.widget.Dropdown.LIST_PADDING + 2 * com.k8bas.skyblockutility.ui.widget.Dropdown.ROW_HEIGHT + 4};
			int[] window = context.computeOnClient(client -> window(entry[0], entry[1]));
			context.getInput().setCursorPos(window[0], window[1]);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
			check(context.computeOnClient(client -> ConfigManager.general().noticePosition()) == third,
					"the picked entry is applied at once: " + context.computeOnClient(client -> ConfigManager.general().noticePosition()));
			check(context.computeOnClient(client -> screen(client).overlay()) == null, "the list takes no more input");
			// The pointer off the page before the clock moves, so no card under the list eases into its hover look meanwhile.
			context.getInput().setCursorPos(2, 2);
			List<Path> shots = new ArrayList<>();
			float[] list = closingSteps(context, clock, "dropdown", shots);
			check(list[0] == 1F && Math.abs(list[1] - 0.125F) < 0.01F && list[2] < 0F, "the list: 1 at 0 ms, 0.125 at 45 ms, gone at 90 ms: "
					+ java.util.Arrays.toString(list));
			// Over the list's box, how far each frame differs from the page: as open at 0 ms, partly at 45 ms, not at all at 90 ms.
			long openDiff = difference(open, page, box);
			long[] fading = {difference(shots.get(0), page, box), difference(shots.get(1), page, box), difference(shots.get(2), page, box)};
			// At 0 ms as open, but for its new value's check mark and no hover; at 45 ms at an eighth of its opacity.
			check(openDiff > 0 && fading[0] >= openDiff * 9 / 10, "0 ms after the close the list is drawn as it was open: " + fading[0] + " of " + openDiff);
			check(fading[1] > 0 && fading[1] < fading[0] / 2, "45 ms after, between it and the page: " + fading[1] + " of " + fading[0]);
			check(fading[2] == 0, "90 ms after, the page: " + fading[2]);

			// Closed with Esc, then 10 ms later a click inside where the list was reaches the page's control there (the notice
			// duration slider): the closing list takes no input.
			click(context, controlOf(context, "interface.notice_position"));
			clock.addAndGet(500);
			context.waitTick();
			int[] listBox = context.computeOnClient(client -> screen(client).overlay().bounds());
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();
			check(context.computeOnClient(client -> screen(client).closingOverlay()) != null, "Esc: the list fades out");
			int[] under = context.computeOnClient(client -> {
				Widget slider = screen(client).page().control(option(screen(client), "interface.notice_seconds"));
				int left = Math.max(listBox[0] + 2, slider.x() + 2);
				int right = Math.min(listBox[2] - 2, slider.x() + slider.width() - 2);
				int top = Math.max(listBox[1] + 2, slider.y() + 2);
				int bottom = Math.min(listBox[3] - 2, slider.y() + slider.height() - 2);
				// The end of the overlap farther from the knob, so the click moves the value.
				int knob = ((Slider) slider).knobX() + Slider.KNOB_SIZE / 2;
				int x = Math.abs(left - knob) > Math.abs(right - knob) ? left : right;
				return right > left && bottom > top ? new int[] {x, (top + bottom) / 2} : null;
			});
			check(under != null, "the notice duration slider lies under the list");
			clock.addAndGet(10);
			click(context, under);
			int after = context.computeOnClient(client -> ConfigManager.general().noticeSeconds());
			check(after != seconds, "a click 10 ms after the close reaches the slider under the list: " + seconds + " to " + after);
			check(context.computeOnClient(client -> screen(client).closingOverlay()) != null, "while the list still fades out");
			// Its field clicked 10 ms after the close: a new list, the closing one dropped (EC-UI-31).
			clock.addAndGet(500);
			context.waitTick();
			click(context, controlOf(context, "interface.notice_position"));
			clock.addAndGet(500);
			context.waitTick();
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();
			clock.addAndGet(10);
			click(context, controlOf(context, "interface.notice_position"));
			check(context.computeOnClient(client -> screen(client).overlay()) != null, "its field clicked 10 ms after the close opens a new list");
			check(context.computeOnClient(client -> screen(client).closingOverlay()) == null, "which drops the closing one");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();

			// A GUI scale change drops a closing overlay at once.
			clock.addAndGet(500);
			context.waitTick();
			click(context, controlOf(context, "interface.notice_position"));
			clock.addAndGet(500);
			context.waitTick();
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();
			context.runOnClient(client -> client.options.guiScale().set(3));
			context.waitTicks(2);
			check(context.computeOnClient(client -> screen(client).closingOverlay()) == null, "a GUI scale change drops the closing list");
			context.runOnClient(client -> client.options.guiScale().set(2));
			clock.addAndGet(1_000);
			context.waitTicks(3);

			// The colour picker, closed with Esc: it fades the same way, its wheel's texture kept until it is gone.
			click(context, controlOf(context, "interface.accent"));
			clock.addAndGet(500);
			context.waitTicks(2);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();
			int wheelWhileFading = context.computeOnClient(client -> com.k8bas.skyblockutility.ui.widget.WheelTexture.cachedForTests()[0]);
			float[] picker = closingSteps(context, clock, "colour-picker", new ArrayList<>());
			int wheelAfter = context.computeOnClient(client -> com.k8bas.skyblockutility.ui.widget.WheelTexture.cachedForTests()[0]);
			check(picker[0] == 1F && Math.abs(picker[1] - 0.125F) < 0.01F && picker[2] < 0F, "the picker: 1, 0.125, gone: " + java.util.Arrays.toString(picker));
			check(wheelWhileFading == 1 && wheelAfter == 0, "the wheel's texture is kept while the picker fades and freed once it is gone: "
					+ wheelWhileFading + ", " + wheelAfter);
			LOGGER.info("overlays closing: list {}, picker {}", java.util.Arrays.toString(list), java.util.Arrays.toString(picker));
		} finally {
			context.runOnClient(client -> {
				ConfigManager.general().noticePosition = before;
				ConfigManager.general().noticeSeconds = secondsBefore;
			});
		}
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
	}

	/**
	 * Screenshots a closing overlay at 0, 45 and 90 ms after it closed (the frame of the close is 0 ms) and returns its opacity
	 * each time, -1 once it is gone.
	 */
	private static float[] closingSteps(ClientGameTestContext context, AtomicLong clock, String name, List<Path> shots) {
		float[] opacity = new float[3];
		for (int step = 0; step < 3; step++) {
			opacity[step] = context.computeOnClient(client -> screen(client).closingOpacityForTests());
			shots.add(context.takeScreenshot("t3.48-" + name + "-closing-" + step * 45 + "ms"));
			clock.addAndGet(45);
			context.waitTick();
		}
		return opacity;
	}

	/**
	 * REQ-UI-47 (Checkpoint G3u) at GUI scale 4 on the test clock, motion on: a slider's knob grows 40 to 48 px and the content
	 * area's scrollbar widens 12 to 20 px over 150 ms; drawn every 4 ms, each adds at most one disc mask (its grown size's),
	 * where drawing every size it passes from its own mask would add up to 8.
	 */
	private static void growingShapesBorrowMasks(ClientGameTestContext context, AtomicLong clock) {
		context.getInput().setCursorPos(2, 2);
		context.runOnClient(client -> {
			client.options.guiScale().set(4);
			screen(client).select(Category.FISHING);
		});
		clock.addAndGet(1_000);
		context.waitTicks(3);
		check(context.computeOnClient(client -> Shapes.guiScale()) == 4, "GUI scale 4");
		context.runOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = screen.page().page().rows().stream()
					.filter(r -> r instanceof ConfigLayout.OptionRow o && screen.page().control(o.option()) instanceof Slider slider && slider.isEnabled())
					.map(r -> (ConfigLayout.OptionRow) r).findFirst().orElseThrow();
			screen.scrollArea().ensureVisible(row.y(), row.y() + row.height());
			screen.scrollArea().setScroll(screen.scrollArea().target());
		});
		clock.addAndGet(1_000);
		context.waitTicks(2);
		int[] knob = context.computeOnClient(client -> {
			Slider slider = (Slider) screen(client).page().widgets().stream()
					.filter(widget -> widget instanceof Slider shown && shown.fullyShowing() && shown.isEnabled()).findFirst().orElseThrow();
			return new int[] {slider.x() + slider.width() / 2, slider.y() + slider.height() / 2};
		});
		grows(context, clock, knob, "a slider's knob growing on hover");
		int[] bar = context.computeOnClient(client -> {
			ScrollArea area = screen(client).scrollArea();
			check(area.maxScroll() > 0, "the Fishing page scrolls at GUI scale 4");
			return new int[] {area.x() + area.width() - 2, area.y() + area.height() / 2};
		});
		grows(context, clock, bar, "the content area's scrollbar widening");
		context.getInput().setCursorPos(2, 2);
		context.runOnClient(client -> client.options.guiScale().set(2));
		clock.addAndGet(1_000);
		context.waitTicks(3);
	}

	/** Moves the pointer onto GUI point {@code at} and draws 150 ms of frames 4 ms apart; checks the masks they added. */
	private static void grows(ClientGameTestContext context, AtomicLong clock, int[] at, String what) {
		context.getInput().setCursorPos(2, 2);
		clock.addAndGet(1_000);
		context.waitTicks(2);
		int before = context.computeOnClient(client -> CornerTextures.cachedForTests()[1]);
		int[] window = context.computeOnClient(client -> window(at[0], at[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		for (int t = 0; t <= 160; t += 4) {
			context.waitTick();
			clock.addAndGet(4);
		}
		context.waitTick();
		int after = context.computeOnClient(client -> CornerTextures.cachedForTests()[1]);
		check(after - before <= 1, what + " adds " + (after - before) + " masks (" + before + " to " + after + "), at most its grown size's 1");
	}

	/**
	 * AC-UI-56 [C] (REQ-UI-52, T3.45) at GUI scale 2 on the test clock: a rule card clicked open eases open over 150 ms, the
	 * next rule's header moving down with it (strictly between at 75 ms, at the open place from 150 ms on), and closes the
	 * same way; screenshots at 0, 75 and 150 ms. On a screen of its own with two mob rules, the rules restored after.
	 */
	private static void ruleCardsOpenAndClose(ClientGameTestContext context, AtomicLong clock, UiTime time) {
		List<com.k8bas.skyblockutility.highlight.HighlightRule> before = context.computeOnClient(client -> MobRulesForTests.rules());
		try {
			context.runOnClient(client -> MobRulesForTests.use(List.of(RuleCardsGameTest.mob("motion-1", "Zealot", "Zealot", "The End", 0xAA00FF),
					RuleCardsGameTest.mob("motion-2", "Voidgloom", "Voidgloom", null, 0x00FF00))));
			context.setScreen(() -> new ConfigScreen(null, OptionCatalog.live(), time));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
			// The open starts with the first frame on the test clock: drawn first, then past its end.
			context.waitTicks(3);
			clock.addAndGet(1_000);
			context.waitTicks(3);
			check(!context.computeOnClient(client -> screen(client).motion().animating()), "the open and the switch have ended");
			int closed = nextHeaderY(context);
			// A click on Zealot's header, as a player opens it.
			int[] header = context.computeOnClient(client -> {
				Widget widget = screen(client).page().header(rule(screen(client), "Zealot"));
				return window(widget.x() + widget.width() / 3, widget.y() + widget.height() / 2);
			});
			context.getInput().setCursorPos(header[0], header[1]);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
			int[] opening = new int[3];
			float[] chevron = new float[3];
			for (int step = 0; step < 3; step++) {
				opening[step] = nextHeaderY(context);
				chevron[step] = context.computeOnClient(client -> screen(client).page().header(rule(screen(client), "Zealot")).reveal());
				context.takeScreenshot("t3.45-rule-opening-" + step * 75 + "ms");
				clock.addAndGet(75);
				context.waitTick();
			}
			check(chevron[0] == 0F && Math.abs(chevron[1] - 0.875F) < 0.001F && chevron[2] == 1F,
					"the chevron turns with the card: " + java.util.Arrays.toString(chevron));
			check(opening[0] == closed, "0 ms: the next header has not moved: " + opening[0] + ", closed " + closed);
			check(opening[1] > closed && opening[1] < opening[2], "75 ms: between the closed and the open place: " + closed + " < " + opening[1] + " < "
					+ opening[2]);
			clock.addAndGet(500);
			context.waitTick();
			check(nextHeaderY(context) == opening[2], "150 ms: open, and it stays: " + nextHeaderY(context) + ", " + opening[2]);
			context.runOnClient(client -> screen(client).toggleRule(rule(screen(client), "Zealot")));
			context.waitTick();
			clock.addAndGet(75);
			context.waitTick();
			int halfShut = nextHeaderY(context);
			clock.addAndGet(75);
			context.waitTick();
			int shut = nextHeaderY(context);
			check(halfShut > closed && halfShut < opening[2] && shut == closed, "closing: " + opening[2] + " > " + halfShut + " > " + shut + " = " + closed);
			// EC-UI-32: a search opens the rule it finds at once, motion on, and its end closes it at once.
			context.runOnClient(client -> {
				screen(client).search().model().setText("zealot");
				screen(client).applyQueryForTests();
			});
			context.waitTick();
			float found = context.computeOnClient(client -> screen(client).page().header(rule(screen(client), "Zealot")).reveal());
			context.runOnClient(client -> {
				screen(client).search().model().setText("");
				screen(client).applyQueryForTests();
			});
			context.waitTick();
			float ended = context.computeOnClient(client -> screen(client).page().header(rule(screen(client), "Zealot")).reveal());
			check(found == 1F && ended == 0F, "a search opens the rule at once and its end closes it at once: " + found + ", " + ended);
			pressedLooks(context, clock);
			LOGGER.info("rule card motion: the next header at {} closed, {} at 75 ms, {} open; closing {} at 75 ms, {} at 150 ms", closed, opening[1],
					opening[2], halfShut, shut);
		} finally {
			context.runOnClient(client -> MobRulesForTests.use(before));
		}
	}

	/**
	 * AC-UI-57 [C] (REQ-UI-53, T3.46) at GUI scale 2 on the test clock, Mob Highlighter on: held down, the feature's whole-card
	 * toggle and a rule header are drawn in the card colour mixed 12 % toward black, and the switch's track as drawn then,
	 * mixed the same; 150 ms after the release the card is back in its hover colour. Screenshots of each held. Each press
	 * flips what it is pressed on, so the card and the switch are pressed twice, leaving the feature as it was.
	 */
	private static void pressedLooks(ClientGameTestContext context, AtomicLong clock) {
		context.runOnClient(client -> ((Toggle) option(screen(client), "mob_highlighter.enabled")).binding().set(true));
		context.waitTick();
		// The feature's own card, pressed on its left, away from its switch.
		int[] card = context.computeOnClient(client -> {
			Widget widget = screen(client).page().card(option(screen(client), "mob_highlighter.enabled"));
			return new int[] {widget.x() + 40, widget.y() + widget.height() / 2, widget.x() + 8, widget.y() + widget.height() - 3};
		});
		Path held = holdOn(context, card, "t3.46-pressed-card");
		int pressed = pixel(context, held, card[2], card[3]);
		int expected = Widget.pressed(Theme.CARD, 1F) & 0xFFFFFF;
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		// The release reaches the screen in the next frame; the 150 ms count from there.
		context.waitTick();
		clock.addAndGet(150);
		context.waitTick();
		int after = pixel(context, context.takeScreenshot("t3.46-released-card"), card[2], card[3]);
		check(close(pressed, expected), "the held card is its colour mixed 12 % toward black: " + Integer.toHexString(pressed) + ", expected "
				+ Integer.toHexString(expected));
		check(close(after, Theme.CARD_HOVER & 0xFFFFFF), "150 ms after the release it is its hover colour: " + Integer.toHexString(after));
		clickOn(context, card);
		// The switch: its track as drawn while held, mixed the same; sampled on the track's row 1 GUI px from the end the knob
		// is not at.
		int[] knob = context.computeOnClient(client -> {
			Widget widget = screen(client).page().control(option(screen(client), "mob_highlighter.enabled"));
			return new int[] {widget.x() + widget.width() / 2, widget.y() + widget.height() / 2, widget.x(), widget.y(), widget.width()};
		});
		Path switchShot = holdOn(context, knob, "t3.46-pressed-switch");
		int[] track = context.computeOnClient(client -> {
			ToggleSwitch widget = (ToggleSwitch) screen(client).page().control(option(screen(client), "mob_highlighter.enabled"));
			float t = widget.knob();
			int colour = ToggleSwitch.trackColour(t, Theme.current().accent(), true);
			return new int[] {Widget.pressed(colour, 1F) & 0xFFFFFF, t < 0.5F ? 1 : 0};
		});
		// The knob sits at the off end while t is below one half: sample the on end, and the other way round.
		int trackX = track[1] == 1 ? knob[2] + knob[4] - 3 : knob[2] + 2;
		int trackPixel = pixel(context, switchShot, trackX, knob[3] + 1);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(close(trackPixel, track[0]), "the held switch's track is its colour mixed 12 % toward black: " + Integer.toHexString(trackPixel)
				+ ", expected " + Integer.toHexString(track[0]));
		clickOn(context, knob);
		// A rule header, held: it opens the rule as a click does.
		int[] header = context.computeOnClient(client -> {
			Widget widget = screen(client).page().header(rule(screen(client), "Voidgloom"));
			return new int[] {widget.x() + widget.width() / 3, widget.y() + widget.height() / 2, widget.x() + widget.width() / 2, widget.y() + 2};
		});
		Path headerShot = holdOn(context, header, "t3.46-pressed-header");
		int headerPixel = pixel(context, headerShot, header[2], header[3]);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(close(headerPixel, expected), "the held rule header is its colour mixed 12 % toward black: " + Integer.toHexString(headerPixel));
		boolean on = context.computeOnClient(client -> ((Toggle) option(screen(client), "mob_highlighter.enabled")).binding().get());
		check(on, "the card and switch pressed twice each leave Mob Highlighter on");
		aPressAnotherButtonTookOverEnds(context, clock);
		LOGGER.info("pressed looks: card {} then {}, header {}", Integer.toHexString(pressed), Integer.toHexString(after), Integer.toHexString(headerPixel));
	}

	/**
	 * T3.46 review: the left button held on a toggle card while the right one presses a key binding button (which takes it):
	 * the card's press ends then, so once both are up it no longer looks pressed under the pointer. Animations, which the
	 * card flips, is set back after.
	 */
	private static void aPressAnotherButtonTookOverEnds(ClientGameTestContext context, AtomicLong clock) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		boolean animations = context.computeOnClient(client -> ((Toggle) option(screen(client), "interface.animations")).binding().get());
		try {
			clock.addAndGet(1_000);
			context.waitTicks(2);
			// The card and the key binding button both in view: the page scrolled to put the button at the bottom.
			context.runOnClient(client -> {
				ConfigScreen screen = screen(client);
				ConfigLayout.OptionRow row = screen.page().page().rows().stream()
						.filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals("keybinds.open_settings"))
						.map(r -> (ConfigLayout.OptionRow) r).findFirst().orElseThrow();
				screen.scrollArea().ensureVisible(row.y(), row.y() + row.height());
				screen.scrollArea().setScroll(screen.scrollArea().target());
			});
			clock.addAndGet(1_000);
			context.waitTicks(2);
			boolean bothShow = context.computeOnClient(client -> screen(client).page().control(option(screen(client), "keybinds.open_settings")).fullyShowing()
					&& screen(client).page().card(option(screen(client), "interface.animations")).fullyShowing());
			check(bothShow, "the Animations card and the Open settings key binding button both show");
			int[] card = context.computeOnClient(client -> {
				Widget widget = screen(client).page().card(option(screen(client), "interface.animations"));
				return new int[] {widget.x() + 40, widget.y() + widget.height() / 2};
			});
			int[] key = context.computeOnClient(client -> {
				Widget widget = screen(client).page().control(option(screen(client), "keybinds.open_settings"));
				return window(widget.x() + widget.width() / 2, widget.y() + widget.height() / 2);
			});
			holdOn(context, card, "t3.46-held-card-before-right-click");
			context.getInput().setCursorPos(key[0], key[1]);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTick();
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
			clock.addAndGet(150);
			context.waitTick();
			float look = context.computeOnClient(client -> screen(client).page().card(option(screen(client), "interface.animations")).pressLook(card[0], card[1]));
			check(look == 0F, "a press another button took over ends: the card no longer looks pressed under the pointer: " + look);
		} finally {
			context.runOnClient(client -> ((Toggle) option(screen(client), "interface.animations")).binding().set(animations));
		}
	}

	/** The option {@code id} laid out on the shown page. */
	private static com.k8bas.skyblockutility.ui.option.Option option(ConfigScreen screen, String id) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow o && o.option().id().equals(id))
				.map(row -> ((ConfigLayout.OptionRow) row).option()).findFirst().orElseThrow();
	}

	/** Holds the left button at GUI point ({@code at[0]}, {@code at[1]}) for one frame and takes a screenshot. */
	private static Path holdOn(ClientGameTestContext context, int[] at, String name) {
		int[] window = context.computeOnClient(client -> window(at[0], at[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		return context.takeScreenshot(name);
	}

	private static void clickOn(ClientGameTestContext context, int[] at) {
		int[] window = context.computeOnClient(client -> window(at[0], at[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static boolean close(int a, int b) {
		return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) <= 2 && Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) <= 2 && Math.abs((a & 0xFF) - (b & 0xFF)) <= 2;
	}

	/** The second rule's header, "Voidgloom", in GUI px from the page's top (an opening card may scroll the page into view). */
	private static int nextHeaderY(ClientGameTestContext context) {
		return context.computeOnClient(client -> screen(client).page().page().rows().stream()
				.filter(row -> row instanceof ConfigLayout.RuleHeader header && "Voidgloom".equals(header.rule().label().get()))
				.findFirst().orElseThrow().y());
	}

	private static com.k8bas.skyblockutility.ui.option.RuleGroup rule(ConfigScreen screen, String label) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.RuleHeader header && label.equals(header.rule().label().get()))
				.map(row -> ((ConfigLayout.RuleHeader) row).rule()).findFirst().orElseThrow();
	}

	/** A control's centre in GUI px, scrolled into view at once first. */
	private static int[] controlOf(ClientGameTestContext context, String optionId) {
		context.runOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = screen.page().page().rows().stream()
					.filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals(optionId)).map(r -> (ConfigLayout.OptionRow) r)
					.findFirst().orElseThrow();
			screen.scrollArea().ensureVisible(row.y(), row.y() + row.height());
			screen.scrollArea().setScroll(screen.scrollArea().target());
		});
		context.waitTicks(2);
		return context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow row = screen.page().page().rows().stream()
					.filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals(optionId)).map(r -> (ConfigLayout.OptionRow) r)
					.findFirst().orElseThrow();
			Widget control = screen.page().control(row.option());
			return new int[] {control.x() + control.width() / 2, control.y() + control.height() / 2};
		});
	}

	/** Reads what the screen's frames see: on the screen's source, as they run. */
	private static <T> T onScreen(UiTime time, java.util.function.Supplier<T> read) {
		UiTime previous = UiClock.enter(time);
		try {
			return read.get();
		} finally {
			UiClock.leave(previous);
		}
	}

	/** A dropdown opened mid-switch settles the motion, so the list is placed at its settled box. */
	private static void anOverlaySettlesTheMotion(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).motion().animating()), "switching to General");
		context.runOnClient(client -> {
			ConfigLayout.OptionRow row = choiceRow(screen(client));
			// Brought into view at once: on the still test clock a glide would not move (T3.26).
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
			screen(client).scrollArea().setScroll(screen(client).scrollArea().target());
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

	/**
	 * AC-UI-46 [C] (REQ-UI-45, T3.35): a tab switch at GUI scale 2 as a screenshot sequence on the test clock: the selected tab's
	 * accent bar is on the old tab at the switch, strictly between the two 75 ms later, and on the new tab at 150 ms.
	 */
	private static void tabIndicatorSlides(ClientGameTestContext context, AtomicLong clock) {
		check(context.computeOnClient(client -> client.getWindow().getGuiScale()) == 2, "at GUI scale 2");
		int[] tabs = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			List<Category> categories = screen.view().categories();
			return new int[] {screen.tabs().x(), screen.tabs().y(), categories.indexOf(screen.selected()), categories.indexOf(Category.FISHING),
					ConfigLayout.TAB + ConfigLayout.TAB_GAP, screen.tabs().y() + screen.tabs().height()};
		});
		check(tabs[2] >= 0 && tabs[3] - tabs[2] >= 2 && context.computeOnClient(client -> screen(client).tabs().rows().scroll()) == 0,
				"a switch across at least two tabs, the sidebar unscrolled: " + java.util.Arrays.toString(tabs));
		int accent = Theme.current().accent() & 0xFFFFFF;
		int fromTop = (tabs[1] + tabs[2] * tabs[4] + 4) * 2;
		int toTop = (tabs[1] + tabs[3] * tabs[4] + 4) * 2;
		context.runOnClient(client -> screen(client).select(screen(client).view().categories().get(tabs[3])));
		context.waitTicks(2);
		int atSwitch = accentBar(context.takeScreenshot("t3.35-tab-switch-at-0ms"), tabs, accent, "at the switch");
		clock.addAndGet(75);
		context.waitTicks(2);
		int midway = accentBar(context.takeScreenshot("t3.35-tab-switch-at-75ms"), tabs, accent, "75 ms in");
		clock.addAndGet(75);
		context.waitTicks(2);
		int arrived = accentBar(context.takeScreenshot("t3.35-tab-switch-at-150ms"), tabs, accent, "at 150 ms");
		LOGGER.info("tab indicator, physical row of its accent bar's top: {} at the switch, {} at 75 ms, {} at 150 ms (tabs at {} and {})", atSwitch, midway,
				arrived, fromTop, toTop);
		check(atSwitch == fromTop, "at the switch, on the old tab: " + atSwitch + ", not " + fromTop);
		check(midway > fromTop && midway < toTop, "75 ms later, strictly between " + fromTop + " and " + toTop + ": " + midway);
		check(arrived == toTop, "at 150 ms, on the new tab: " + arrived + ", not " + toTop);
		// The pointer on the selected tab: its hover never covers the highlight's accent bar.
		int[] selectedTab = context.computeOnClient(client -> window(tabs[0] + 30, tabs[1] + tabs[3] * tabs[4] + ConfigLayout.TAB / 2));
		context.getInput().setCursorPos(selectedTab[0], selectedTab[1]);
		context.waitTicks(2);
		clock.addAndGet(150);
		context.waitTicks(2);
		check(accentBar(context.takeScreenshot("t3.35-tab-hovered"), tabs, accent, "the selected tab hovered") == toTop, "its bar shows under the pointer");
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		// Searches, on the still test clock: the highlight is on the selected tab in the first frame, with no slide, when a search
		// moves that tab in the shown list, when it selects another tab as the old one has no hit (it does not slide from where
		// the old one was), and when the search is cleared.
		Category shown = context.computeOnClient(client -> screen(client).selected());
		check(shown == Category.FISHING, "the switch went to Fishing: " + shown);
		int[] kept = search(context, "bobber");
		check(context.computeOnClient(client -> screen(client).selected()) == shown && kept[0] >= 0 && kept[0] < tabs[3] && kept[1] == kept[0] * 1000,
				"\"bobber\": Fishing stays selected, moved up the shown tabs, its highlight there at once: tab " + kept[0] + ", at " + kept[1] / 1000F);
		int[] back = search(context, "");
		check(back[0] == tabs[3] && back[1] == tabs[3] * 1000, "cleared: Fishing back at its place, its highlight there at once: tab " + back[0] + ", at "
				+ back[1] / 1000F);
		// From Fishing's place, a search without it: the highlight goes to the newly selected tab at once, not sliding from there.
		int[] moved = search(context, "npc");
		check(context.computeOnClient(client -> screen(client).selected()) != shown && moved[0] >= 0 && moved[1] == moved[0] * 1000,
				"\"npc\": another tab selected, its highlight there at once: tab " + moved[0] + ", at " + moved[1] / 1000F);
		int[] cleared = search(context, "");
		check(cleared[0] >= 0 && cleared[1] == cleared[0] * 1000, "cleared: the highlight on the selected tab at once: tab " + cleared[0] + ", at "
				+ cleared[1] / 1000F);
	}

	/** Searches for {@code query} as typing would; returns the selected tab's index in the shown tabs and the highlight's place, x1000. */
	private static int[] search(ClientGameTestContext context, String query) {
		context.runOnClient(client -> {
			screen(client).search().model().setText(query);
			screen(client).applyQueryForTests();
		});
		context.waitTicks(2);
		return context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			return new int[] {screen.view().categories().indexOf(screen.selected()), Math.round(screen.tabIndicatorPosition() * 1000)};
		});
	}

	/**
	 * The accent bar down the sidebar's left edge in a screenshot at GUI scale 2: exactly one run of the accent colour in that
	 * column, as tall as the bar ({@code TAB} less 8 GUI px); returns its top physical row.
	 */
	private static int accentBar(Path shot, int[] tabs, int accent, String when) {
		java.awt.image.BufferedImage image = com.k8bas.skyblockutility.ui.render.HairlineProbe.read(shot);
		int column = tabs[0] * 2 + 1;
		int runs = 0;
		int top = -1;
		int length = 0;
		boolean inRun = false;
		for (int row = tabs[1] * 2; row < tabs[5] * 2; row++) {
			boolean on = (image.getRGB(column, row) & 0xFFFFFF) == accent;
			if (on && !inRun) {
				runs++;
				if (runs == 1) {
					top = row;
				}
			}
			if (on && runs == 1) {
				length++;
			}
			inRun = on;
		}
		check(runs == 1 && length == (ConfigLayout.TAB - 8) * 2, when + ": one accent bar " + (ConfigLayout.TAB - 8) * 2 + " px tall down the tabs, not "
				+ runs + " (the first " + length + " px)");
		return top;
	}

	/**
	 * AC-UI-46 (REQ-UI-45, T3.35) on the test clock: an open dropdown's highlight and a database picker row's hover ease in as a
	 * card's: 75 ms after the pointer arrives the row's colour lies between its own and the hover colour, and at 150 ms it is
	 * the hover colour.
	 */
	private static void listHoversEase(ClientGameTestContext context, AtomicLong clock) {
		context.runOnClient(client -> screen(client).select(Category.GENERAL));
		clock.addAndGet(1_000);
		context.waitTicks(2);
		click(context, controlOf(context, "interface.notice_position"));
		int[] entry = context.computeOnClient(client -> {
			com.k8bas.skyblockutility.ui.widget.Overlay list = screen(client).overlay();
			check(list != null, "the dropdown opened");
			Widget rows = list.widgets().get(0);
			// An entry that is not the selected one (that one has the accent look), at its right end, away from its label.
			var choice = (com.k8bas.skyblockutility.ui.option.Choice<?>) screen(client).page().page().rows().stream()
					.filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals("interface.notice_position"))
					.map(r -> ((ConfigLayout.OptionRow) r).option()).findFirst().orElseThrow();
			int index = choice.values().indexOf(choice.binding().get()) == 0 ? 1 : 0;
			return new int[] {rows.x() + rows.width() - 24, rows.y() + com.k8bas.skyblockutility.ui.widget.Dropdown.LIST_PADDING
					+ index * com.k8bas.skyblockutility.ui.widget.Dropdown.ROW_HEIGHT + 3};
		});
		easesIn(context, clock, entry, Theme.CARD, "an open dropdown's entry");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		clock.addAndGet(1_000);
		context.waitTicks(2);
		click(context, controlOf(context, "mob_highlighter.add_from_database"));
		clock.addAndGet(1_000);
		context.waitTicks(2);
		int[] folder = context.computeOnClient(client -> {
			check(screen(client).overlay() instanceof DatabasePickerOverlay, "the picker opened");
			Widget list = ((DatabasePickerOverlay) screen(client).overlay()).list();
			return new int[] {list.x() + list.width() - 40, list.y() + 3};
		});
		easesIn(context, clock, folder, Theme.PANEL, "a database picker folder");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).overlay()) == null, "Esc closed the picker");
	}

	/** The pointer onto GUI point {@code at}: its colour starts at {@code base}, lies between at 75 ms and is the hover colour at 150 ms. */
	private static void easesIn(ClientGameTestContext context, AtomicLong clock, int[] at, int base, String what) {
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		clock.addAndGet(1_000);
		context.waitTicks(2);
		int[] window = context.computeOnClient(client -> window(at[0], at[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTicks(2);
		int atOnce = pixel(context, context.takeScreenshot("t3.35-ease-0ms"), at[0], at[1]);
		clock.addAndGet(75);
		context.waitTicks(2);
		int midway = pixel(context, context.takeScreenshot("t3.35-ease-75ms"), at[0], at[1]);
		clock.addAndGet(75);
		context.waitTicks(2);
		int full = pixel(context, context.takeScreenshot("t3.35-ease-150ms"), at[0], at[1]);
		LOGGER.info("{}: {} at 0 ms, {} at 75, {} at 150", what, Integer.toHexString(atOnce), Integer.toHexString(midway), Integer.toHexString(full));
		check(atOnce == (base & 0xFFFFFF), what + ": its own colour as the pointer arrives: " + Integer.toHexString(atOnce));
		check(between(midway, base, Theme.CARD_HOVER), what + ": 75 ms in, between: " + Integer.toHexString(midway));
		check(full == (Theme.CARD_HOVER & 0xFFFFFF), what + ": the hover colour at 150 ms: " + Integer.toHexString(full));
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

	/** How far two screenshots at GUI scale 2 differ over a GUI box {x0, y0, x1, y1}: the sum of their pixels' channel differences. */
	private static long difference(Path a, Path b, int[] box) {
		try {
			var one = javax.imageio.ImageIO.read(a.toFile());
			var two = javax.imageio.ImageIO.read(b.toFile());
			int scale = 2;
			long sum = 0;
			for (int y = box[1] * scale; y < box[3] * scale; y++) {
				for (int x = box[0] * scale; x < box[2] * scale; x++) {
					int p = one.getRGB(x, y);
					int q = two.getRGB(x, y);
					sum += Math.abs((p >> 16 & 0xFF) - (q >> 16 & 0xFF)) + Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF)) + Math.abs((p & 0xFF) - (q & 0xFF));
				}
			}
			return sum;
		} catch (IOException e) {
			throw new AssertionError("cannot read a screenshot", e);
		}
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
