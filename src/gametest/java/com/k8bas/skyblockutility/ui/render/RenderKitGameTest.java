package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * T2.2, the render kit in a real frame:
 * <ul>
 *   <li>a test panel (header with the accent line, tabs, cards, buttons, text styles, a clipped region)
 *       screenshotted at GUI scales 1-4, with "Smooth corners" off and, for T2.9d (R32), on: the corner
 *       checks hold for both;</li>
 *   <li>at a 320×240 GUI, clips of zero and negative size draw nothing, log nothing and throw nothing
 *       (REQ-UI-02).</li>
 * </ul>
 */
public class RenderKitGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		// 1280×960 gives GUI sizes 1280×960, 640×480, 427×320 and 320×240 at scales 1 to 4.
		context.getInput().resizeWindow(1280, 960);
		try {
			context.setScreen(RenderKitTestScreen::new);
			context.waitForScreen(RenderKitTestScreen.class);
			int[][] sizes = {{1280, 960}, {640, 480}, {427, 320}, {320, 240}};
			for (boolean smooth : new boolean[] {false, true}) {
			context.runOnClient(client -> ConfigManager.general().smoothCorners = smooth ? Boolean.TRUE : null);
			String look = smooth ? " (smooth)" : "";
			for (int scale = 1; scale <= 4; scale++) {
				int s = scale;
				context.runOnClient(client -> client.options.guiScale().set(s));
				context.waitTicks(3);
				int[] gui = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScale(),
						client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
				check(gui[0] == s && gui[1] == sizes[s - 1][0] && gui[2] == sizes[s - 1][1],
						"GUI scale " + s + " gives " + sizes[s - 1][0] + "x" + sizes[s - 1][1] + ": " + gui[0] + " " + gui[1] + "x" + gui[2]);
				Path shot = context.takeScreenshot((smooth ? "t2.9d-render-kit-smooth-scale-" : "t2.2-render-kit-scale-") + s);
				// The first card's corners are cut in screen pixels: its corner shows the panel, its edges the outline.
				int[] card = context.computeOnClient(client -> ((RenderKitTestScreen) client.gui.screen()).firstCard);
				int left = card[0] * s, top = card[1] * s, radius = Shapes.RADIUS_CARD * s;
				check(pixel(shot, left, top) == (Theme.PANEL & 0xFFFFFF), "scale " + s + look + ": the corner pixel is cut");
				check(pixel(shot, left + radius, top) == (Theme.SEPARATOR & 0xFFFFFF), "scale " + s + look + ": the top edge is drawn from the radius on");
				check(pixel(shot, left, top + radius) == (Theme.SEPARATOR & 0xFFFFFF), "scale " + s + look + ": the left edge is drawn from the radius down");
				check(pixel(shot, left + radius / 2, top + radius / 2) != (Theme.PANEL & 0xFFFFFF), "scale " + s + look + ": the curve is round, not square");
			}
			}
			context.runOnClient(client -> ConfigManager.general().smoothCorners = null);

			// GUI scale 4: a 320×240 GUI, the smallest vanilla allows.
			// The probe frames sit between two log markers; the end marker in the file proves it is flushed.
			String start = "k8bas render kit probe start " + System.nanoTime();
			String end = "k8bas render kit probe end " + System.nanoTime();
			LOGGER.info(start);
			context.runOnClient(client -> ((RenderKitTestScreen) client.gui.screen()).probe = true);
			context.waitTicks(3);
			Path shot = context.takeScreenshot("t2.2-render-kit-empty-clips-320x240");
			List<Boolean> visible = context.computeOnClient(client -> ((RenderKitTestScreen) client.gui.screen()).probeVisible);
			boolean balanced = context.computeOnClient(client -> ((RenderKitTestScreen) client.gui.screen()).probeBalanced);
			// A few more frames, so anything reported late (e.g. a GL debug message) lands before the end marker.
			context.waitTicks(5);
			LOGGER.info(end);
			String log = "";
			for (int tries = 0; tries < 40 && !log.contains(end); tries++) {
				context.waitTick();
				log = latestLog();
			}
			check(log.contains(start) && log.contains(end), "both probe markers reached latest.log");
			String logged = log.substring(log.indexOf(start) + start.length(), log.indexOf(end));
			// Up to the end marker's own line prefix; besides the screenshot message nothing may appear.
			logged = logged.substring(0, logged.lastIndexOf('\n') + 1);
			List<String> lines = logged.lines().filter(line -> !line.isBlank() && !line.contains("Saved screenshot")).toList();
			int magenta = countPixels(shot, RenderKitTestScreen.PROBE_COLOR & 0xFFFFFF);
			LOGGER.info("empty clips at 320x240: visible {}, balanced {}, {} magenta pixels, other log lines between the markers {}", visible, balanced, magenta, lines);
			check(visible.equals(List.of(false, false, true, false, false)), "only the full-screen clip is visible: " + visible);
			check(balanced, "every clip pushed was popped");
			check(magenta == 0, "nothing drawn in an empty clip shows: " + magenta + " magenta pixels");
			check(lines.isEmpty(), "nothing is logged while drawing empty clips: " + lines);
			Screen screen = context.computeOnClient(client -> client.gui.screen());
			check(screen instanceof RenderKitTestScreen, "the screen is still open: " + screen);
		} finally {
			context.runOnClient(client -> ConfigManager.general().smoothCorners = null);
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static int pixel(Path screenshot, int x, int y) {
		try {
			return ImageIO.read(screenshot.toFile()).getRGB(x, y) & 0xFFFFFF;
		} catch (IOException e) {
			throw new AssertionError("cannot read " + screenshot, e);
		}
	}

	private static int countPixels(Path screenshot, int rgb) {
		try {
			BufferedImage image = ImageIO.read(screenshot.toFile());
			int count = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					if ((image.getRGB(x, y) & 0xFFFFFF) == rgb) {
						count++;
					}
				}
			}
			return count;
		} catch (IOException e) {
			throw new AssertionError("cannot read " + screenshot, e);
		}
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
