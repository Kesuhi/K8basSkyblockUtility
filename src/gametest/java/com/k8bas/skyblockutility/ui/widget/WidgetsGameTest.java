package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.ui.render.CornerMask;
import com.k8bas.skyblockutility.ui.render.Corners;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * T2.3a, the basic widgets in a real client:
 * <ul>
 *   <li>every widget state on one screen, screenshotted at GUI scales 1-4;</li>
 *   <li>AC-UI-38 on a screen without keyboard navigation (REQ-UI-37, T3.28): the wheel over a 0.1-step slider, idle, after a
 *       press and release on its knob and on its track, with and without Shift, scrolls the region it sits in exactly as over
 *       its empty space and never changes the value; at the region's top and bottom it changes nothing; during a drag it is
 *       ignored over the slider and off it, the value follows only the pointer and is committed once; a press on the track
 *       moves the knob there and starts a drag;</li>
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
	/** A blend computed from the image's own colours matches to rounding; a wrong coverage is off by more. */
	private static final int BLEND_TOLERANCE = 3;

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
			hairlinesAreThin(context);
			outlineCornersKeepTheirColour(context);
			knobsKeepTheirOwnColour(context);
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
			knobHasNoFillLine(context);
			knobsAreAntiAliased(context);
			hoveredKnobGrows(context);
			wheelOverASlider(context);
			dragCommitsOnce(context);
			typingAndShortcuts(context);
			// It closes the screen itself.
			pointerShapes(context);
			context.setScreen(() -> null);
			focusedFieldTakesTheKeys(context);
		} finally {
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/**
	 * AC-UI-28's Prove-It (T3.15, the 1.2.1 knob line): at GUI scale 2 with Smooth corners OFF, the knob of the
	 * 0-128 slider at 64 is a 20 px disc whose seventh physical row is the track's first. Every pixel of that row
	 * inside the disc must have the knob's own colour, not the accent fill's.
	 */
	private static void knobHasNoFillLine(ClientGameTestContext context) {
		Boolean before = context.computeOnClient(client -> ConfigManager.general().smoothCorners);
		context.runOnClient(client -> ConfigManager.general().smoothCorners = Boolean.FALSE);
		try {
			context.waitTicks(3);
			int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
			int[] knob = context.computeOnClient(client -> screen(client.gui.screen()).middleKnob());
			BufferedImage shot = read(context.takeScreenshot("t3.15-knob-scale-2-off"));
			int size = Slider.KNOB_SIZE * scale;
			int left = knob[0] * scale;
			int top = knob[1] * scale;
			int centre = shot.getRGB(left + size / 2, top + size / 2);
			int row = top + 6;
			int wrong = 0;
			int inside = 0;
			// The knob is a smooth disc whatever the setting (REQ-UI-29): the pixels of that row its disc mask fully covers.
			for (int x = 0; x < size; x++) {
				if (CornerMask.discAlpha(size, x, 6) == 255) {
					inside++;
					if (!near(shot.getRGB(left + x, row), centre)) {
						wrong++;
					}
				}
			}
			LOGGER.info("knob row 7 at scale {}: {} of {} pixels differ from the knob colour {}", scale, wrong, inside,
					Integer.toHexString(centre));
			check(scale == 2, "the check runs at GUI scale 2: " + scale);
			check(wrong == 0, "no fill pixel in the knob's seventh row: " + wrong + " differ");
		} finally {
			context.runOnClient(client -> ConfigManager.general().smoothCorners = before);
		}
	}

	/**
	 * AC-UI-31 [C] (T3.18): at GUI scale 2, with Smooth corners OFF and ON, the knob of the 0-128 slider at 64 and the knobs
	 * of the "on" and "off" toggles (GUI 8, 8 and 40, 8, a 22x12 track with an 8 px knob 2 px inside it) are discs of the
	 * disc mask: each pixel is the knob's colour over its backdrop by the mask's coverage, within {@link #BLEND_TOLERANCE},
	 * so the rim is anti-aliased with no staircase. The slider knob's rows over the track are left out, since the fill and
	 * the track lie there. The knobs before T3.18, four corner quadrants of the ramp mask, are off by 4 to 8 in 12 to 20
	 * pixels a knob, so the check sees the exact disc.
	 */
	private static void knobsAreAntiAliased(ClientGameTestContext context) {
		Boolean before = context.computeOnClient(client -> ConfigManager.general().smoothCorners);
		try {
			for (boolean smooth : new boolean[] {false, true}) {
				context.runOnClient(client -> ConfigManager.general().smoothCorners = smooth);
				context.waitTicks(3);
				check(context.computeOnClient(client -> ConfigManager.general().smoothCorners()) == smooth, "the pass renders with Smooth corners "
						+ (smooth ? "ON" : "OFF"));
				int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
				check(scale == 2, "the check runs at GUI scale 2: " + scale);
				String pass = "scale 2" + (smooth ? " smooth" : " off");
				BufferedImage shot = read(context.takeScreenshot("t3.18-knobs-scale-2" + (smooth ? "-smooth" : "-off")));
				int[] knob = context.computeOnClient(client -> screen(client.gui.screen()).middleKnob());
				int trackTop = context.computeOnClient(client -> screen(client.gui.screen()).knobSliders()[1][1]) * scale;
				int panel = shot.getRGB(knob[0] * scale + Slider.KNOB_SIZE * scale / 2, (knob[1] - 2) * scale);
				int blended = checkDisc(shot, scale, knob[0], knob[1], Slider.KNOB_SIZE, panel, trackTop, trackTop + Slider.TRACK_HEIGHT * scale,
						pass + ", slider knob");
				int knobSize = ToggleSwitch.HEIGHT - 4;
				// On: the knob at the track's right end over the accent; off: at its left end over the track's grey.
				int onTrack = shot.getRGB((8 + 6) * scale, (8 + 6) * scale);
				blended += checkDisc(shot, scale, 8 + ToggleSwitch.WIDTH - 2 - knobSize, 10, knobSize, onTrack, 0, 0, pass + ", on toggle knob");
				int offTrack = shot.getRGB((40 + 16) * scale, (8 + 6) * scale);
				blended += checkDisc(shot, scale, 40 + 2, 10, knobSize, offTrack, 0, 0, pass + ", off toggle knob");
				LOGGER.info("knob discs at {}: {} rim pixels blend the knob over its backdrop", pass, blended);
			}
		} finally {
			context.runOnClient(client -> ConfigManager.general().smoothCorners = before);
		}
	}

	/**
	 * One knob, {@code size} GUI px at GUI (left, top), against the disc mask: every pixel outside the screen rows from
	 * {@code skipFrom} to {@code skipTo} is the knob's centre colour over {@code backdrop} by the mask's coverage. Returns how
	 * many of them must show a blend that differs from both colours, so that a staircase could not pass.
	 */
	private static int checkDisc(BufferedImage shot, int scale, int left, int top, int size, int backdrop, int skipFrom, int skipTo, String what) {
		int d = size * scale;
		int x0 = left * scale;
		int y0 = top * scale;
		int knob = shot.getRGB(x0 + d / 2, y0 + d / 2);
		int blended = 0;
		int wrong = 0;
		String first = "";
		for (int y = 0; y < d; y++) {
			if (y0 + y >= skipFrom && y0 + y < skipTo) {
				continue;
			}
			for (int x = 0; x < d; x++) {
				int expected = blend(knob, backdrop, CornerMask.discAlpha(d, x, y));
				int actual = shot.getRGB(x0 + x, y0 + y);
				if (!within(actual, expected, BLEND_TOLERANCE)) {
					if (wrong++ == 0) {
						first = " (first at " + x + "," + y + ": " + Integer.toHexString(actual) + ", expected " + Integer.toHexString(expected) + ")";
					}
				}
				if (!near(expected, knob) && !near(expected, backdrop)) {
					blended++;
				}
			}
		}
		check(wrong == 0, what + ": " + wrong + " pixels differ from the disc mask's coverage" + first);
		check(blended > 0, what + ": no rim pixel blends the knob " + Integer.toHexString(knob) + " over " + Integer.toHexString(backdrop));
		return blended;
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

	/** {@code top} over {@code bottom} at coverage {@code alpha} (0-255), per colour channel, as the GUI blend draws it. */
	private static int blend(int top, int bottom, int alpha) {
		int out = 0xFF000000;
		for (int shift = 0; shift <= 16; shift += 8) {
			int value = Math.round(((top >> shift & 0xFF) * alpha + (bottom >> shift & 0xFF) * (255 - alpha)) / 255F);
			out |= value << shift;
		}
		return out;
	}

	/**
	 * AC-UI-34 [C] (REQ-UI-33, T3.22): at GUI scales 2, 3 and 4 the outline of the idle "Cancel" button (GUI 8, 68, 46x16)
	 * and of the empty "Search settings..." field (GUI 8, 132, 140x16) measure ceil(guiScale / 2) physical px on their top
	 * edge, and the caret of the focused "Zealot" field (GUI 160, 132, its clock stopped so the caret always shows) is that
	 * wide: 1, 2 and 2 px.
	 */
	private static void hairlinesAreThin(ClientGameTestContext context) {
		for (int scale = 2; scale <= 4; scale++) {
			int s = scale;
			context.runOnClient(client -> client.options.guiScale().set(s));
			context.waitTicks(3);
			BufferedImage shot = read(context.takeScreenshot("t3.22-hairlines-scale-" + scale));
			// REQ-UI-33's rule written out, not read from the code under test.
			int thick = (int) Math.ceil(scale / 2.0);
			int button = run(shot, (8 + 23) * scale, 68 * scale, 0, 1, Theme.SEPARATOR);
			int field = run(shot, (8 + 70) * scale, 132 * scale, 0, 1, Theme.SEPARATOR);
			int caretX = context.computeOnClient(client -> 160 + 4 + client.font.width("Zealot"));
			int caret = run(shot, caretX * scale, (132 + 7) * scale, 1, 0, Theme.TEXT_PRIMARY);
			boolean clearBefore = !near(shot.getRGB(caretX * scale - 1, (132 + 7) * scale), Theme.TEXT_PRIMARY);
			LOGGER.info("hairlines at scale {}: button {} px, field {} px, caret {} px (rule {})", scale, button, field, caret, thick);
			check(button == thick && field == thick && caret == thick && clearBefore, "scale " + scale + ": hairlines " + thick + " px: button " + button
					+ ", field " + field + ", caret " + caret + (clearBefore ? "" : ", the caret starts earlier"));
		}
	}

	/** How many pixels from (x, y), stepping by (dx, dy), have the colour {@code argb}. */
	private static int run(BufferedImage shot, int x, int y, int dx, int dy, int argb) {
		int n = 0;
		while (near(shot.getRGB(x + n * dx, y + n * dy), argb)) {
			n++;
		}
		return n;
	}

	/**
	 * AC-UI-29's outline corners (T3.15): the idle "Cancel" button (GUI 8, 68, 46x16, radius 4, a hairline outline) at GUI
	 * scales 3 and 4, Smooth corners OFF and ON. Every corner pixel the outline fully covers shows the outline colour;
	 * in 1.2.1 the fill was drawn over some of them.
	 */
	private static void outlineCornersKeepTheirColour(ClientGameTestContext context) {
		Boolean before = context.computeOnClient(client -> ConfigManager.general().smoothCorners);
		try {
			for (int scale = 3; scale <= 4; scale++) {
				for (boolean smooth : new boolean[] {false, true}) {
					int s = scale;
					context.runOnClient(client -> {
						client.options.guiScale().set(s);
						ConfigManager.general().smoothCorners = smooth;
					});
					context.waitTicks(3);
					BufferedImage shot = read(context.takeScreenshot("t3.15-outline-scale-" + scale + (smooth ? "-smooth" : "-off")));
					int wrong = 0;
					int checked = 0;
					int w = 46 * scale;
					int h = 16 * scale;
					int r = Shapes.RADIUS_CONTROL * scale;
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							boolean corner = (x < r || x >= w - r) && (y < r || y >= h - r);
							if (corner && outlineCovers(smooth, w, h, r, Shapes.hairlinePx(scale), x, y)) {
								checked++;
								if (!near(shot.getRGB(8 * scale + x, 68 * scale + y), Theme.SEPARATOR)) {
									wrong++;
								}
							}
						}
					}
					LOGGER.info("outline corners at scale {}{}: {} of {} pixels lack the outline colour", scale, smooth ? " smooth" : "", wrong,
							checked);
					check(checked > 0 && wrong == 0, "outline corners at scale " + scale + (smooth ? " smooth" : "") + ": " + wrong + " of "
							+ checked + " covered by the fill");
				}
			}
		} finally {
			context.runOnClient(client -> ConfigManager.general().smoothCorners = before);
		}
	}

	/**
	 * AC-UI-28 in full (T3.16), at GUI scales 2-4 with Smooth corners OFF and ON, on the sliders at 0, 64 and 128,
	 * the hovered one and the dimmed one (its card idle and hovered):
	 * <ul>
	 *   <li>the knob's interior (every pixel it fully covers) has the colour of the knob's centre: no fill shows;</li>
	 *   <li>no pixel of a dimmed knob's box has the dimmed fill's colour;</li>
	 *   <li>at 64 and on the hovered slider, the track's top and bottom rows show no backdrop next to the knob;</li>
	 *   <li>the knob's outermost pixels left and right match on every row above and below the track;</li>
	 *   <li>with the knob at either end, nothing of the fill or the track shows outside the full track pill and the
	 *       knob: the short bar beside the knob keeps its rounded end.</li>
	 * </ul>
	 * Knobs and pills are smooth discs whatever the setting (REQ-UI-29), so their pixels are judged by mask coverage.
	 */
	private static void knobsKeepTheirOwnColour(ClientGameTestContext context) {
		Boolean before = context.computeOnClient(client -> ConfigManager.general().smoothCorners);
		try {
			for (int scale = 2; scale <= 4; scale++) {
				for (boolean smooth : new boolean[] {false, true}) {
					for (boolean cardHover : new boolean[] {false, true}) {
						int s = scale;
						context.runOnClient(client -> {
							client.options.guiScale().set(s);
							ConfigManager.general().smoothCorners = smooth;
							screen(client.gui.screen()).dimmedCardHover = cardHover;
						});
						context.waitTicks(3);
						String pass = "scale " + scale + (smooth ? " smooth" : "") + (cardHover ? " card hovered" : "");
						BufferedImage shot = read(context.takeScreenshot("t3.16-knobs-scale-" + scale + (smooth ? "-smooth" : "-off")
								+ (cardHover ? "-card-hover" : "")));
						int[][] sliders = context.computeOnClient(client -> screen(client.gui.screen()).knobSliders());
						for (int i = 0; i < sliders.length; i++) {
							checkKnob(shot, scale, smooth, sliders[i], i == 1 || i == 3, pass + ", slider " + i);
							// The forced-hot ones, plain and dimmed, are drawn grown (T3.36); the others at their layout size.
							int expected = (i == 3 || i == 5 ? Slider.KNOB_GROWN : Slider.KNOB_SIZE) * scale;
							check(sliders[i][8] == expected, pass + ", slider " + i + ": its knob is drawn " + sliders[i][8] + " px, not " + expected);
							if (i == 0 || i == 2) {
								checkTrackEnds(shot, scale, sliders[i], pass + ", slider " + i);
							}
						}
					}
				}
			}
		} finally {
			context.runOnClient(client -> {
				ConfigManager.general().smoothCorners = before;
				screen(client.gui.screen()).dimmedCardHover = false;
			});
		}
	}

	private static void checkKnob(BufferedImage shot, int scale, boolean smooth, int[] slider, boolean notchCheck, String what) {
		// The knob as drawn, on the physical-pixel grid (REQ-UI-34, T3.25), grown when hot (T3.36).
		int size = slider[8];
		int r = size / 2;
		int left = slider[6];
		int top = slider[7];
		check(size == Slider.KNOB_SIZE * scale || size == Slider.KNOB_GROWN * scale, what + ": the knob is drawn " + size + " px, 10 or 12 GUI px across");
		boolean dimmed = slider[5] == 1;
		int centre = shot.getRGB(left + r, top + r);
		int interior = 0;
		int wrong = 0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean full = CornerMask.discAlpha(size, x, y) == 255;
				if (full) {
					interior++;
					if (!near(shot.getRGB(left + x, top + y), centre)) {
						wrong++;
					}
				}
			}
		}
		check(interior > 0 && wrong == 0, what + ": " + wrong + " of " + interior + " knob pixels differ from its centre");
		int trackTop = slider[1] * scale;
		if (dimmed) {
			int fill = shot.getRGB(slider[0] * scale + 2 * scale, trackTop + Slider.TRACK_HEIGHT * scale / 2);
			int fillPixels = 0;
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					if (near(shot.getRGB(left + x, top + y), fill)) {
						fillPixels++;
					}
				}
			}
			check(fillPixels == 0, what + ": " + fillPixels + " pixels of the dimmed fill inside the knob's box");
		}
		if (notchCheck) {
			int backdrop = shot.getRGB(left + r, top - 2 * scale);
			for (int y : new int[] {trackTop, trackTop + Slider.TRACK_HEIGHT * scale - 1}) {
				for (int x = left - scale; x < left + size + scale; x++) {
					check(!near(shot.getRGB(x, y), backdrop), what + ": the backdrop shows on the track's row " + (y - top) + " at x " + (x - left));
				}
			}
		}
		checkRimSymmetry(shot, size, left, top, trackTop, scale, what);
	}

	/**
	 * Above and below the track the knob's outermost pixels left and right match: the knob is a smooth disc whatever
	 * the setting (REQ-UI-29), so its rim is anti-aliased in both passes and must blend over the same backdrop.
	 */
	private static void checkRimSymmetry(BufferedImage shot, int size, int left, int top, int trackTop, int scale, String what) {
		int trackRows = Slider.TRACK_HEIGHT * scale;
		for (int y = 0; y < size; y++) {
			int row = top + y;
			if (row >= trackTop && row < trackTop + trackRows) {
				continue;
			}
			int a = -1;
			int b = -1;
			for (int x = 0; x < size; x++) {
				if (CornerMask.discAlpha(size, x, y) > 0) {
					if (a < 0) {
						a = x;
					}
					b = x;
				}
			}
			if (a >= 0) {
				int la = shot.getRGB(left + a, row);
				int rb = shot.getRGB(left + b, row);
				check(near(la, rb), what + ": the rim differs left and right on knob row " + y + ": " + Integer.toHexString(la) + " vs "
						+ Integer.toHexString(rb));
			}
		}
	}

	/** Next to a knob at either end, every pixel outside both the full track pill and the knob's disc is backdrop. */
	private static void checkTrackEnds(BufferedImage shot, int scale, int[] slider, String what) {
		// The knob as drawn, on the physical-pixel grid (REQ-UI-34, T3.25).
		int size = slider[8];
		int left = slider[6];
		int top = slider[7];
		int trackX = slider[0] * scale;
		int trackTop = slider[1] * scale;
		int trackW = slider[2] * scale;
		int trackH = Slider.TRACK_HEIGHT * scale;
		int backdrop = shot.getRGB(left + size / 2, top - 2 * scale);
		// Discs and pills are smooth whatever the setting (REQ-UI-29): a pixel any of their masks touches may be tinted.
		// The pill's caps are the outer halves of the disc mask of its height (REQ-UI-30).
		int cap = trackH / 2;
		for (int y = 0; y < trackH; y++) {
			for (int x = left - scale; x < left + size + scale; x++) {
				int px = x - trackX;
				boolean inPill = px >= 0 && px < trackW && (px >= cap && px < trackW - cap
						|| CornerMask.discAlpha(trackH, px < cap ? px : px - (trackW - trackH), y) > 0);
				int ky = trackTop + y - top;
				boolean inDisc = x >= left && x < left + size && ky >= 0 && ky < size && CornerMask.discAlpha(size, x - left, ky) > 0;
				if (!inPill && !inDisc) {
					check(near(shot.getRGB(x, trackTop + y), backdrop), what + ": fill or track outside the pill and the knob at track row " + y
							+ ", x " + (x - left) + " from the knob");
				}
			}
		}
	}

	/** Whether the 1 GUI px outline fully covers screen pixel (x, y) of the w×h button with radius r. */
	private static boolean outlineCovers(boolean smooth, int w, int h, int r, int t, int x, int y) {
		if (smooth) {
			int mx = x < r ? x : x - (w - 2 * r);
			int my = y < r ? y : y - (h - 2 * r);
			return CornerMask.ringAlpha(r, t, mx, my) == 255;
		}
		for (Corners.Span span : Corners.ring(w, h, r, t)) {
			if (x >= span.x0() && x < span.x1() && y >= span.y0() && y < span.y1()) {
				return true;
			}
		}
		return false;
	}

	private static boolean near(int a, int b) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > 8) {
				return false;
			}
		}
		return true;
	}

	private static BufferedImage read(Path shot) {
		try {
			return ImageIO.read(shot.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read " + shot, e);
		}
	}

	/** AC-UI-38 [A] on a screen without keyboard navigation, with real wheel events (T3.28). */
	private static void wheelOverASlider(ClientGameTestContext context) {
		// The reference: 3 notches down and 3 up over the region's empty space.
		setPage(context, WidgetTestScreen.PAGE_START);
		pointAt(context, WidgetTestScreen::pageEmpty);
		int down = notches(context, -3) - WidgetTestScreen.PAGE_START;
		int up = notches(context, 3) - WidgetTestScreen.PAGE_START - down;
		check(down > 0 && up == -down, "the region scrolls both ways over its empty space: " + down + ", " + up);
		for (String state : java.util.List.of("idle", "after a press on the knob", "after a press on the track")) {
			setPage(context, WidgetTestScreen.PAGE_START);
			context.runOnClient(client -> screen(client.gui.screen()).setDecimal(100));
			if (!state.equals("idle")) {
				pointAt(context, state.endsWith("knob") ? WidgetTestScreen::liveKnob : screen -> screen.sliderPoint(0.8));
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				context.waitTicks(2);
			}
			long value = decimal(context);
			pointAt(context, WidgetTestScreen::liveCentres);
			check(context.computeOnClient(client -> screen(client.gui.screen()).sliderInRegion()), state + ": the slider shows in the region");
			int afterDown = notches(context, -3);
			int afterUp = notches(context, 3);
			check(afterDown == WidgetTestScreen.PAGE_START + down && afterUp == WidgetTestScreen.PAGE_START, state + ": the wheel over the slider scrolls "
					+ "the region as over its empty space: " + afterDown + ", " + afterUp);
			context.getInput().holdShift();
			notches(context, -3);
			notches(context, 3);
			context.getInput().releaseShift();
			check(decimal(context) == value, state + ": the value is unchanged, with Shift too: " + value + " -> " + decimal(context));
		}
		// At the region's top and bottom the wheel over the slider, placed to show there, changes nothing.
		int max = context.computeOnClient(client -> screen(client.gui.screen()).pageMax());
		for (int edge : new int[] {0, max}) {
			context.runOnClient(client -> screen(client.gui.screen()).setSliderTop(edge + 8));
			setPage(context, edge);
			check(context.computeOnClient(client -> screen(client.gui.screen()).sliderInRegion()), "the slider shows at the edge " + edge);
			long value = decimal(context);
			pointAt(context, WidgetTestScreen::liveCentres);
			int after = notches(context, edge == 0 ? 3 : -3);
			check(after == edge && decimal(context) == value, "at " + (edge == 0 ? "the top" : "the bottom") + " nothing changes: " + after + ", "
					+ decimal(context));
		}
		context.runOnClient(client -> screen(client.gui.screen()).setSliderTop(-1));
		// During a drag the wheel is ignored, over the slider and off it; the value follows only the pointer; one commit.
		setPage(context, WidgetTestScreen.PAGE_START);
		context.runOnClient(client -> screen(client.gui.screen()).setDecimal(100));
		int commits = context.computeOnClient(client -> screen(client.gui.screen()).decimalCommits);
		pointAt(context, WidgetTestScreen::liveKnob);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		pointAt(context, screen -> screen.sliderPoint(0.6));
		long held = decimal(context);
		check(Math.abs(held - 200) <= 5, "the value follows the pointer: 0.6 along gives about 2.00: " + held);
		int scroll = notches(context, -3);
		check(scroll == WidgetTestScreen.PAGE_START && decimal(context) == held, "a drag over the slider ignores the wheel: " + scroll + ", " + decimal(context));
		pointAt(context, WidgetTestScreen::pageEmpty);
		long off = decimal(context);
		scroll = notches(context, 3);
		check(scroll == WidgetTestScreen.PAGE_START && decimal(context) == off, "and off it: " + scroll + ", " + decimal(context));
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(context.computeOnClient(client -> screen(client.gui.screen()).decimalCommits) == commits + 1, "the drag is committed once");
		// A press on the track away from the knob moves the knob there and starts a drag.
		context.runOnClient(client -> screen(client.gui.screen()).setDecimal(100));
		pointAt(context, screen -> screen.sliderPoint(0.9));
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		boolean dragging = context.computeOnClient(client -> screen(client.gui.screen()).sliderDragging());
		long moved = decimal(context);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(dragging && moved > 250, "a press on the track moves the knob there (" + moved + ") and starts a drag (" + dragging + ")");
		setPage(context, WidgetTestScreen.PAGE_START);
		context.runOnClient(client -> screen(client.gui.screen()).setDecimal(100));
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		LOGGER.info("wheel over a slider: the region scrolled {} per 3 notches over it as over its empty space, the value never moved", down);
	}

	private static void setPage(ClientGameTestContext context, int scroll) {
		context.runOnClient(client -> screen(client.gui.screen()).setPageScroll(scroll));
		context.waitTicks(2);
	}

	private static long decimal(ClientGameTestContext context) {
		return context.computeOnClient(client -> screen(client.gui.screen()).decimalValue);
	}

	private interface Point {
		int[] at(WidgetTestScreen screen);
	}

	private static void pointAt(ClientGameTestContext context, Point point) {
		int[] at = context.computeOnClient(client -> point.at(screen(client.gui.screen())));
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
	}

	/** Sends {@code count} notches (positive up) and returns the region's scroll after them. */
	private static int notches(ClientGameTestContext context, int count) {
		for (int i = 0; i < Math.abs(count); i++) {
			context.getInput().scroll(Math.signum(count));
			context.waitTick();
		}
		return context.computeOnClient(client -> screen(client.gui.screen()).pageScroll());
	}

	/**
	 * AC-UI-44 [C] (REQ-UI-43, T3.33) at GUI scale 2: the hand over an enabled toggle and slider, the I-beam over a text field,
	 * the arrow over their disabled twins and empty space; a dragged slider keeps the horizontal-resize pointer off its track
	 * and gives it up on release. EC-UI-27: closed while a text field or a slider drag shows its shape, the screen leaves the
	 * window's default cursor at once.
	 */
	private static void pointerShapes(ClientGameTestContext context) {
		PointerProbe.expect(context, 8 + ToggleSwitch.WIDTH / 2.0, 8 + ToggleSwitch.HEIGHT / 2.0, PointerShape.HAND, "a toggle");
		PointerProbe.expect(context, 72 + ToggleSwitch.WIDTH / 2.0, 8 + ToggleSwitch.HEIGHT / 2.0, PointerShape.DEFAULT, "a disabled toggle");
		PointerProbe.expect(context, 257, 54, PointerShape.HAND, "a slider");
		PointerProbe.expect(context, 170, 54, PointerShape.DEFAULT, "a disabled slider");
		PointerProbe.expect(context, 78, 140, PointerShape.TEXT, "a text field");
		PointerProbe.expect(context, 230, 182, PointerShape.DEFAULT, "a disabled text field");
		PointerProbe.expect(context, 630, 470, PointerShape.DEFAULT, "empty space");
		// A drag: the shape stays with the slider wherever the pointer goes, until the release.
		int[] start = context.computeOnClient(client -> screen(client.gui.screen()).sliderPoint(0.5));
		context.getInput().setCursorPos(start[0], start[1]);
		context.waitTicks(2);
		PointerProbe.check(context, PointerShape.HAND, "the live slider");
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		try {
			context.waitTicks(2);
			PointerProbe.check(context, PointerShape.RESIZE_HORIZONTAL, "the live slider, pressed");
			PointerProbe.move(context, 400, 300);
			PointerProbe.check(context, PointerShape.RESIZE_HORIZONTAL, "the live slider, dragged far off its track");
		} finally {
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
		}
		PointerProbe.check(context, PointerShape.DEFAULT, "empty space after the release");
		// EC-UI-27: a text field's I-beam, then the screen closes.
		PointerProbe.expect(context, 78, 140, PointerShape.TEXT, "a text field, before the screen closes");
		context.setScreen(() -> null);
		context.waitTicks(2);
		check(PointerProbe.windowCursor(context) == com.mojang.blaze3d.platform.cursor.CursorType.DEFAULT,
				"closed over a text field, the window's cursor is the default: " + PointerProbe.windowCursor(context));
		// And a slider drag, held while the screen closes.
		context.setScreen(WidgetTestScreen::new);
		context.waitForScreen(WidgetTestScreen.class);
		context.waitTicks(2);
		int[] again = context.computeOnClient(client -> screen(client.gui.screen()).sliderPoint(0.5));
		context.getInput().setCursorPos(again[0], again[1]);
		context.waitTicks(2);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		PointerProbe.check(context, PointerShape.RESIZE_HORIZONTAL, "the live slider, pressed, before the screen closes");
		try {
			context.setScreen(() -> null);
			context.waitTicks(2);
			check(PointerProbe.windowCursor(context) == com.mojang.blaze3d.platform.cursor.CursorType.DEFAULT,
					"closed during a slider drag, the window's cursor is the default: " + PointerProbe.windowCursor(context));
		} finally {
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
		}
		LOGGER.info("pointer shapes on the widget test screen: hand, I-beam, arrow when disabled, horizontal resize while dragged; the default after a close");
	}

	/** The pixels in the accent colour in the live slider's value label (GUI 94..160 x 46..62 at GUI scale 2). */
	private static int accentPixels(Path shot, ClientGameTestContext context) {
		int accent = context.computeOnClient(client -> com.k8bas.skyblockutility.ui.render.Theme.current().accent()) & 0xFFFFFF;
		BufferedImage image = read(shot);
		int count = 0;
		for (int y = 46 * 2; y < 62 * 2; y++) {
			for (int x = 94 * 2; x < 160 * 2; x++) {
				if ((image.getRGB(x, y) & 0xFFFFFF) == accent) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * AC-UI-47 (REQ-UI-46, T3.36) at GUI scale 2: a hovered knob is drawn 12 GUI px across, 4 screen px wider than an idle one,
	 * measured the same way along the row through their centres (the forced-hot slider at 96 of 0..128, the idle one at 64).
	 */
	private static void hoveredKnobGrows(ClientGameTestContext context) {
		check(context.computeOnClient(client -> client.getWindow().getGuiScale()) == 2, "at GUI scale 2");
		BufferedImage shot = read(context.takeScreenshot("t3.36-knobs"));
		int[] idle = runAt(shot, 135 * 2, 34 * 2, Theme.TEXT_SECONDARY & 0xFFFFFF);
		int[] hot = runAt(shot, 267 * 2, 54 * 2, Theme.TEXT_PRIMARY & 0xFFFFFF);
		int idleWidth = idle[1] - idle[0] + 1;
		int hotWidth = hot[1] - hot[0] + 1;
		LOGGER.info("knob widths at GUI scale 2, full-colour pixels through the centre: idle {}, hovered {}", idleWidth, hotWidth);
		check(idleWidth >= 16 && hotWidth - idleWidth == 4, "the hovered knob 2 GUI px wider than the idle one: " + hotWidth + " against " + idleWidth);
		// About the same centre: the idle knob at 64 of 0..128 is centred on GUI x 135, the hovered one at 96 on 267.
		check(idle[0] + idle[1] == 2 * 135 * 2 - 1 && hot[0] + hot[1] == 2 * 267 * 2 - 1, "both centred on their place: " + java.util.Arrays.toString(idle)
				+ ", " + java.util.Arrays.toString(hot));
	}

	/** The first and last pixel of the run of exactly {@code rgb} along a row through ({@code x}, {@code y}); x, x - 1 when (x, y) is not it. */
	private static int[] runAt(BufferedImage image, int x, int y, int rgb) {
		if ((image.getRGB(x, y) & 0xFFFFFF) != rgb) {
			return new int[] {x, x - 1};
		}
		int left = x;
		while (left > 0 && (image.getRGB(left - 1, y) & 0xFFFFFF) == rgb) {
			left--;
		}
		int right = x;
		while (right + 1 < image.getWidth() && (image.getRGB(right + 1, y) & 0xFFFFFF) == rgb) {
			right++;
		}
		return new int[] {left, right};
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
		// AC-UI-47 (REQ-UI-46, T3.36): while dragged its value is in the accent colour, and not after the release.
		int accentWhileDragged = accentPixels(context.takeScreenshot("t3.36-slider-dragged"), context);
		double atEnd = context.computeOnClient(client -> screen(client.gui.screen()).decimalValue) / 100.0;
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		int afterRelease = context.computeOnClient(client -> screen(client.gui.screen()).decimalCommits);
		int accentReleased = accentPixels(context.takeScreenshot("t3.36-slider-released"), context);
		LOGGER.info("decimal slider value label: {} accent pixels while dragged, {} after the release", accentWhileDragged, accentReleased);
		check(accentWhileDragged > 0 && accentReleased == 0, "the value in the accent colour while dragged only: " + accentWhileDragged + ", then "
				+ accentReleased);
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
