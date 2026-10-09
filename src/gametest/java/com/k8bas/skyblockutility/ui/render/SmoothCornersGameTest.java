package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.CLIPPED;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.CLIP_WIDTH;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.FADED;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.FADED_ALPHA;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.FILLED;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.H;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.OUTLINE;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.SMALL;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.SMALL_SIZE;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.TOP;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.W;

/**
 * T2.9d (REQ-UI-18, R32), the screenshot comparison at GUI scales 1-4, with "Smooth corners" off and on:
 * <ul>
 *   <li>off, every probe pixel is fully drawn or not at all, as before;</li>
 *   <li>on, each corner pixel's coverage is the mask's at screen-pixel resolution (filled, faded, outline, a
 *       clamped odd radius), and the pixels outside the corners are the same as with it off;</li>
 *   <li>a clip cuts the smooth shape as it cuts fills, an empty clip shows nothing, and nothing is logged
 *       across the scale changes, the toggling and the mask textures made and released.</li>
 * </ul>
 */
public class SmoothCornersGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	/** Blending on an 8-bit framebuffer rounds; a wrong mask is off by far more. */
	private static final int TOLERANCE = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		// 1280×960 gives GUI sizes 1280×960, 640×480, 427×320 and 320×240 at scales 1 to 4.
		context.getInput().resizeWindow(1280, 960);
		try {
			context.setScreen(SmoothCornersTestScreen::new);
			context.waitForScreen(SmoothCornersTestScreen.class);
			String start = "k8bas smooth corners start " + System.nanoTime();
			String end = "k8bas smooth corners end " + System.nanoTime();
			LOGGER.info(start);
			List<String> report = new ArrayList<>();
			for (int scale = 1; scale <= 4; scale++) {
				int s = scale;
				context.runOnClient(client -> client.options.guiScale().set(s));
				context.waitTicks(3);
				int gui = context.computeOnClient(client -> client.getWindow().getGuiScale());
				check(gui == s, "GUI scale " + s + ": " + gui);
				BufferedImage off = shot(context, false, "t2.9d-fill-scale-" + s);
				BufferedImage on = shot(context, true, "t2.9d-smooth-scale-" + s);
				int soft = 0;
				for (Probe probe : probes(s)) {
					soft += probe.compare(on, true, s);
					check(probe.compare(off, false, s) == 0, "scale " + s + ", " + probe.name + ": off draws whole pixels only");
					probe.sameOutsideTheCorners(on, off, s);
				}
				check(soft > 0, "scale " + s + ": smooth corners have soft pixels");
				for (BufferedImage image : List.of(off, on)) {
					check(clippedAway(image, s), "scale " + s + ": nothing of the clipped probe shows right of its clip");
					check(noMagenta(image), "scale " + s + ": nothing drawn in an empty clip shows");
				}
				report.add("scale " + s + ": " + soft + " soft pixels");
			}
			context.waitTicks(5);
			LOGGER.info(end);
			String log = "";
			for (int tries = 0; tries < 40 && !log.contains(end); tries++) {
				context.waitTick();
				log = latestLog();
			}
			check(log.contains(start) && log.contains(end), "both markers reached latest.log");
			String logged = log.substring(log.indexOf(start) + start.length(), log.indexOf(end));
			logged = logged.substring(0, logged.lastIndexOf('\n') + 1);
			List<String> lines = logged.lines().filter(line -> !line.isBlank() && !line.contains("Saved screenshot")).toList();
			check(lines.isEmpty(), "nothing is logged while drawing, toggling and rescaling: " + lines);
			LOGGER.info("smooth corners: {}", report);
		} finally {
			context.runOnClient(client -> ConfigManager.general().smoothCorners = null);
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static BufferedImage shot(ClientGameTestContext context, boolean smooth, String name) {
		context.runOnClient(client -> ConfigManager.general().smoothCorners = smooth);
		context.waitTicks(2);
		Path path = context.takeScreenshot(name);
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read " + path, e);
		}
	}

	private static List<Probe> probes(int s) {
		int r = Shapes.RADIUS_CARD * s;
		int small = SMALL_SIZE * s;
		return List.of(
				new Probe("filled", FILLED * s, TOP * s, W * s, H * s, r, 0, 255, W * s),
				new Probe("faded", FADED * s, TOP * s, W * s, H * s, r, 0, FADED_ALPHA, W * s),
				new Probe("outline", OUTLINE * s, TOP * s, W * s, H * s, r, s, 255, W * s),
				new Probe("clipped", CLIPPED * s, TOP * s, W * s, H * s, r, 0, 255, CLIP_WIDTH * s),
				new Probe("clamped " + small + " px box", SMALL * s, TOP * s, small, small, CornerPieces.clampRadius(small, small, r), 0, 255, small));
	}

	/**
	 * One probe in screen pixels: a w×h shape at (x, y) with radius r and outline thickness t (0 = filled),
	 * drawn in green at the given alpha on black, of which the first {@code visibleW} columns are inside its clip.
	 */
	private record Probe(String name, int x, int y, int w, int h, int r, int t, int alpha, int visibleW) {
		/** Every visible pixel against the expected coverage; returns how many are soft (neither 0 nor full). */
		int compare(BufferedImage image, boolean smooth, int s) {
			int soft = 0;
			for (int ly = 0; ly < h; ly++) {
				for (int lx = 0; lx < visibleW; lx++) {
					int coverage = coverage(lx, ly);
					int expected = Math.round((smooth ? coverage : coverage >= 128 ? 255 : 0) * alpha / 255F);
					int green = green(image, x + lx, y + ly);
					check(Math.abs(green - expected) <= TOLERANCE, "scale " + s + (smooth ? " smooth" : " fill") + ", " + name + " pixel "
							+ lx + "," + ly + ": green " + green + ", expected " + expected);
					if (green > TOLERANCE && green < alpha - TOLERANCE) {
						soft++;
					}
				}
			}
			return soft;
		}

		/** Outside the corner squares the smooth shape is the same plain fills. */
		void sameOutsideTheCorners(BufferedImage on, BufferedImage off, int s) {
			for (int ly = 0; ly < h; ly++) {
				for (int lx = 0; lx < visibleW; lx++) {
					if (corner(lx, ly)) {
						continue;
					}
					check(on.getRGB(x + lx, y + ly) == off.getRGB(x + lx, y + ly), "scale " + s + ", " + name + " pixel " + lx + "," + ly
							+ ": the same with smooth corners on and off");
				}
			}
		}

		private boolean corner(int lx, int ly) {
			return (lx < r || lx >= w - r) && (ly < r || ly >= h - r);
		}

		/** The mask's coverage (0-255) of the shape's pixel (lx, ly), each corner square showing its quadrant. */
		private int coverage(int lx, int ly) {
			if (corner(lx, ly)) {
				int mx = lx < r ? lx : lx - w + 2 * r;
				int my = ly < r ? ly : ly - h + 2 * r;
				return t == 0 ? CornerMask.alpha(r, mx, my) : CornerMask.ringAlpha(r, t, mx, my);
			}
			return t == 0 || lx < t || ly < t || lx >= w - t || ly >= h - t ? 255 : 0;
		}
	}

	private static boolean clippedAway(BufferedImage image, int s) {
		for (int y = TOP * s; y < (TOP + H) * s; y++) {
			for (int x = (CLIPPED + CLIP_WIDTH) * s; x < (CLIPPED + W) * s; x++) {
				if (green(image, x, y) != 0) {
					return false;
				}
			}
		}
		return true;
	}

	/** Everything drawn is green on black, so any red or blue is the empty clip's magenta. */
	private static boolean noMagenta(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int rgb = image.getRGB(x, y);
				if ((rgb >> 16 & 0xFF) != 0 || (rgb & 0xFF) != 0) {
					return false;
				}
			}
		}
		return true;
	}

	private static int green(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) >> 8 & 0xFF;
	}

	private static String latestLog() {
		try {
			return Files.readString(FabricLoader.getInstance().getGameDir().resolve(Path.of("logs", "latest.log")));
		} catch (IOException e) {
			throw new AssertionError("cannot read latest.log", e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
