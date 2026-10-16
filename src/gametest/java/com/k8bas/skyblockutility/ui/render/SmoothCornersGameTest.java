package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
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
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.ICON;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.ICON_SIZE;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.OUTLINE;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.SMALL;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.SMALL_OUTLINE;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.SMALL_SIZE;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.TOP;
import static com.k8bas.skyblockutility.ui.render.SmoothCornersTestScreen.W;

/**
 * T2.9d (REQ-UI-18, R32), the screenshot comparison at GUI scales 1-4, with "Smooth corners" off and on:
 * <ul>
 *   <li>off, every probe pixel is fully drawn or not at all, as before, except the 9×9 box with radius 6: a disc,
 *       which is smooth whatever the setting (REQ-UI-29, R46), each pixel the disc mask's (REQ-UI-30, T3.18), and
 *       the same with it on;</li>
 *   <li>on, each corner pixel's coverage is the mask's at screen-pixel resolution (filled, faded, outline, and a 9×9
 *       outline with radius 6, whose clamped radius leaves a 1 px middle between its corner masks at odd scales), and
 *       the pixels outside the corners are the same as with it off; the 9×9 outline is shaped like a disc but follows
 *       the setting, as every outline does (EC-UI-21);</li>
 *   <li>the kit's × (REQ-UI-32, T3.20) is its 8 GUI px icon rasterised at 8 × scale px and drawn 1:1 in its colour, the
 *       same with the setting on and off, at every scale it is rebaked for;</li>
 *   <li>a disc drawn into a fresh render state submits its mask quad with the sampler cache's linear, clamp-to-edge
 *       sampler (REQ-UI-31, T3.18);</li>
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
		// Absent means ON from 1.3.0 (R46): each pass sets its value, and the value found here is put back.
		Boolean before = context.computeOnClient(client -> ConfigManager.general().smoothCorners);
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
					int offSoft = probe.compare(off, false, s);
					boolean always = probe.alwaysSmooth(s);
					check(always ? offSoft > 0 : offSoft == 0, "scale " + s + ", " + probe.name
							+ (always ? ": stays smooth with it off" : ": off draws whole pixels only"));
					probe.sameOutsideTheCorners(on, off, s);
				}
				check(soft > 0, "scale " + s + ": smooth corners have soft pixels");
				for (BufferedImage image : List.of(off, on)) {
					iconIsTheKits(image, s);
				}
				check(context.computeOnClient(client -> drawnWithTheLinearClampSampler()), "scale " + s
						+ ": a mask quad samples with the sampler cache's linear, clamp-to-edge sampler, not the texture's own");
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
			context.runOnClient(client -> ConfigManager.general().smoothCorners = before);
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static BufferedImage shot(ClientGameTestContext context, boolean smooth, String name) {
		context.runOnClient(client -> ConfigManager.general().smoothCorners = smooth);
		context.waitTicks(2);
		if (context.computeOnClient(client -> ConfigManager.general().smoothCorners()) != smooth) {
			throw new AssertionError("FAILED: the pass does not render with Smooth corners " + (smooth ? "ON" : "OFF"));
		}
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
				new Probe("filled", FILLED * s, TOP * s, W * s, H * s, r, 0, 255, W * s, false),
				new Probe("faded", FADED * s, TOP * s, W * s, H * s, r, 0, FADED_ALPHA, W * s, false),
				new Probe("outline", OUTLINE * s, TOP * s, W * s, H * s, r, s, 255, W * s, false),
				new Probe("clipped", CLIPPED * s, TOP * s, W * s, H * s, r, 0, 255, CLIP_WIDTH * s, false),
				new Probe(small + " px disc", SMALL * s, TOP * s, small, small, 0, 0, 255, small, true),
				new Probe(small + " px outline", SMALL_OUTLINE * s, TOP * s, small, small, CornerPieces.clampRadius(small, small, r), s, 255, small,
						false));
	}

	/** T3.20: each pixel of the × probe is the icon's baked coverage in green, within the blend's rounding. */
	private static void iconIsTheKits(BufferedImage image, int s) {
		int px = ICON_SIZE * s;
		int[] alpha = IconShapes.bake(IconShapes.Icon.CROSS, px);
		int soft = 0;
		for (int y = 0; y < px; y++) {
			for (int x = 0; x < px; x++) {
				int green = green(image, ICON * s + x, TOP * s + y);
				check(Math.abs(green - alpha[y * px + x]) <= TOLERANCE, "scale " + s + ", the × pixel " + x + "," + y + ": green " + green
						+ ", expected " + alpha[y * px + x]);
				if (alpha[y * px + x] > TOLERANCE && alpha[y * px + x] < 255 - TOLERANCE) {
					soft++;
				}
			}
		}
		check(soft > 0, "scale " + s + ": the × has anti-aliased edges");
	}

	/**
	 * REQ-UI-31 (T3.18): the 9×9 disc drawn into a fresh render state submits its mask quad with the sampler cache's shared
	 * linear, clamp-to-edge sampler, not the mask texture's own. On the client thread, after a frame drew the same disc, so
	 * its mask already exists at this scale.
	 */
	private static boolean drawnWithTheLinearClampSampler() {
		GuiRenderState state = new GuiRenderState();
		Shapes.roundedRect(new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0), SMALL, TOP, SMALL_SIZE, SMALL_SIZE, Shapes.RADIUS_CARD,
				0xFFFFFFFF);
		List<GpuSampler> samplers = new ArrayList<>();
		state.forEachElement(element -> {
			if (element instanceof ShapeRenderState shape && shape.uvs() != null) {
				samplers.add(shape.textureSetup().sampler0());
			}
		}, GuiRenderState.TraverseRange.ALL);
		GpuSampler linear = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR,
				FilterMode.LINEAR, false);
		return linear != null && !samplers.isEmpty() && samplers.stream().allMatch(sampler -> sampler == linear);
	}

	/**
	 * One probe in screen pixels: a w×h shape at (x, y) with radius r and outline thickness t (0 = filled), or a
	 * w×w disc, drawn in green at the given alpha on black, of which the first {@code visibleW} columns are inside
	 * its clip.
	 */
	private record Probe(String name, int x, int y, int w, int h, int r, int t, int alpha, int visibleW, boolean disc) {
		/** Every visible pixel against the expected coverage; returns how many are soft (neither 0 nor full). */
		int compare(BufferedImage image, boolean smooth, int s) {
			int soft = 0;
			boolean drawnSmooth = smooth || alwaysSmooth(s);
			for (int ly = 0; ly < h; ly++) {
				for (int lx = 0; lx < visibleW; lx++) {
					int coverage = coverage(lx, ly);
					int expected = Math.round((drawnSmooth ? coverage : coverage >= 128 ? 255 : 0) * alpha / 255F);
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

		/** Smooth whatever the setting (REQ-UI-29): filled, with a radius of at least half the shorter side, as {@link Shapes} decides. */
		boolean alwaysSmooth(int s) {
			return ShapePlan.alwaysSmooth(w, h, Shapes.RADIUS_CARD * s, t, Corners.ALL);
		}

		/** Outside the corner squares the smooth shape is the same plain fills; one always smooth is the same everywhere. */
		void sameOutsideTheCorners(BufferedImage on, BufferedImage off, int s) {
			boolean always = alwaysSmooth(s);
			for (int ly = 0; ly < h; ly++) {
				for (int lx = 0; lx < visibleW; lx++) {
					if (!always && corner(lx, ly)) {
						continue;
					}
					check(on.getRGB(x + lx, y + ly) == off.getRGB(x + lx, y + ly), "scale " + s + ", " + name + " pixel " + lx + "," + ly
							+ ": the same with smooth corners on and off");
				}
			}
		}

		private boolean corner(int lx, int ly) {
			return !disc && (lx < r || lx >= w - r) && (ly < r || ly >= h - r);
		}

		/** The mask's coverage (0-255) of the shape's pixel (lx, ly), each corner square showing its quadrant. */
		private int coverage(int lx, int ly) {
			if (disc) {
				return CornerMask.discAlpha(w, lx, ly);
			}
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
