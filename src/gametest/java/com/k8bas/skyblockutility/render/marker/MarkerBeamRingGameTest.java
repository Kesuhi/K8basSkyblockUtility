package com.k8bas.skyblockutility.render.marker;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * T3.0n, beams and rings in a real frame:
 * <ul>
 *   <li>AC-MARK-01: a white label with a red beam 30 blocks away; behind stone, a glass pane, a 3-block
 *       water column and ice the label stays legible and the beam shows above the obstacle.</li>
 *   <li>REQ-MARK-02: a wall that fills the view hides the beam completely (depth-tested).</li>
 *   <li>AC-MARK-03: a ring of radius 3 on a water surface is visible in the open, untinted, and hidden
 *       behind a stone wall.</li>
 *   <li>AC-MARK-06: 150 label-and-beam markers keep the marker pass at 1 ms or less on average over 600
 *       frames, and a marker behind the camera is counted as not submitted.</li>
 *   <li>EC-MARK-06: beams stay with F1; EC-MARK-11: no beam above the build height.</li>
 * </ul>
 */
public class MarkerBeamRingGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final int WHITE = 0xFFFFFF;

	private static final class TestProvider implements MarkerProvider {
		volatile List<Marker> markers = List.of();

		@Override
		public boolean isActive() {
			return !markers.isEmpty();
		}

		@Override
		public void collect(Consumer<Marker> out) {
			markers.forEach(out);
		}

		@Override
		public void reset() {
			markers = List.of();
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		TestProvider provider = new TestProvider();
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			context.runOnClient(client -> {
				WorldMarkers.add(provider);
				WorldMarkers.setHideLabelsWithHud(false);
				setHudHidden(client, true);
				client.options.cloudStatus().set(CloudStatus.OFF);
			});
			try {
				beamsShowAboveObstacles(context, singleplayer, server, provider);
				ringsOnWater(context, singleplayer, server, provider);
				beamsStayWithF1AndAreClipped(context, provider);
				performanceWith150Markers(context, singleplayer, server, provider);
			} finally {
				context.runOnClient(client -> {
					WorldMarkers.remove(provider);
					WorldMarkers.setHideLabelsWithHud(true);
					WorldMarkers.setRecordFrames(false);
					setHudHidden(client, false);
				});
			}
		}
	}

	private static Marker labelAndBeam(double x, double y, double z, String text) {
		MarkerLabel label = MarkerLabel.of(new MarkerLabel.Line(Component.literal(text), WHITE)).asSeeThrough();
		return new Marker(MarkerAnchor.fixed(x, y, z), label, new MarkerBeam(0xFF0000), null);
	}

	/** AC-MARK-01 (beam part) and the depth test of REQ-MARK-02. */
	private static void beamsShowAboveObstacles(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server,
			TestProvider provider) {
		// Walls 5 blocks out, up to y -57: the label (at eye height, 30 blocks out) is behind them, the sky above them is clear.
		Map<String, String[]> obstacles = new LinkedHashMap<>();
		obstacles.put("nothing", new String[0]);
		obstacles.put("stone", new String[] {"fill -8 -60 5 8 -57 5 minecraft:stone"});
		obstacles.put("glass pane", new String[] {"fill -8 -60 5 8 -57 5 minecraft:glass_pane"});
		obstacles.put("water", new String[] {"fill -8 -60 3 8 -56 7 minecraft:glass", "fill -7 -60 4 7 -57 6 minecraft:water"});
		obstacles.put("ice", new String[] {"fill -8 -60 5 8 -57 5 minecraft:ice"});
		Marker marker = labelAndBeam(0.5, -58.5, 30.5, "Beam Test");
		Map<String, Shot> shots = new LinkedHashMap<>();
		for (Map.Entry<String, String[]> obstacle : obstacles.entrySet()) {
			server.runCommand("fill -8 -60 1 8 -40 40 minecraft:air");
			for (String command : obstacle.getValue()) {
				server.runCommand(command);
			}
			shots.put(obstacle.getKey(), shoot(context, singleplayer, server, provider, marker, "t3.0n-beam-" + obstacle.getKey().replace(' ', '-')));
		}
		// A wall that fills the whole view: the beam behind it must not show anywhere.
		server.runCommand("fill -8 -60 1 8 -40 40 minecraft:air");
		server.runCommand("fill -20 -60 5 20 -30 5 minecraft:stone");
		Shot hidden = shoot(context, singleplayer, server, provider,
				new Marker(MarkerAnchor.fixed(0.5, -60, 30.5), null, new MarkerBeam(0xFF0000), null), "t3.0n-beam-behind-a-full-wall");
		// Only the wall: a /fill of more than 32768 blocks fails without changing anything.
		server.runCommand("fill -20 -60 5 20 -30 5 minecraft:air");
		LOGGER.info("beams: {}; behind a wall that fills the view {}", shots, hidden);

		Shot open = shots.get("nothing");
		check(open.textPixels > 30 && open.topChanged > 20, "label and beam are visible in the open: " + open);
		for (Map.Entry<String, Shot> entry : shots.entrySet()) {
			Shot shot = entry.getValue();
			check(shot.textPixels >= open.textPixels * 0.9, "the label stays legible behind " + entry.getKey() + ": " + shot + " vs " + open);
			check(shot.topChanged > 20, "the beam shows above the " + entry.getKey() + ": " + shot);
		}
		// Counts red beam pixels, not every change: a terrain section being rebuilt can leave a one-frame gap in either shot.
		check(hidden.redPixels == 0 && open.redPixels > 20, "a beam behind a wall that fills the view is hidden (depth-tested): " + hidden + " vs open " + open);
	}

	/** AC-MARK-03: a ring of radius 3 on a water surface. */
	private static void ringsOnWater(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server,
			TestProvider provider) {
		Marker onGrass = new Marker(MarkerAnchor.fixed(0.5, -60, 15.5), null, null, new MarkerRing(3, 0x80FF00FF, MarkerRing.Style.BOTH));
		Shot grass = shoot(context, singleplayer, server, provider, onGrass, "t3.0n-ring-on-grass");
		server.runCommand("fill -8 -61 9 8 -61 22 minecraft:water");
		// A full water block's surface is 8/9 of a block up.
		Marker ring = new Marker(MarkerAnchor.fixed(0.5, -61 + 8.0 / 9.0, 15.5), null, null, new MarkerRing(3, 0x80FF00FF, MarkerRing.Style.BOTH));
		Shot open = shoot(context, singleplayer, server, provider, ring, "t3.0n-ring-open");
		server.runCommand("fill -8 -60 5 8 -57 5 minecraft:stone");
		Shot behind = shoot(context, singleplayer, server, provider, ring, "t3.0n-ring-behind-stone");
		server.runCommand("fill -8 -60 5 8 -57 5 minecraft:air");
		server.runCommand("fill -8 -61 9 8 -61 22 minecraft:grass_block");
		LOGGER.info("ring: on grass {}, on water {}, on water behind stone {}", grass, open, behind);
		// The outline is opaque magenta: magenta pixels on the water mean the water did not tint it.
		check(grass.magentaPixels > 50, "the ring is visible on grass: " + grass);
		check(open.magentaPixels > 50, "the ring on the water is visible in the open, untinted: " + open);
		check(behind.magentaPixels == 0, "the ring is hidden behind a stone wall: " + behind);
	}

	/** EC-MARK-06 and EC-MARK-11. */
	private static void beamsStayWithF1AndAreClipped(ClientGameTestContext context, TestProvider provider) {
		context.runOnClient(client -> {
			WorldMarkers.setHideLabelsWithHud(true);
			provider.markers = List.of(labelAndBeam(0.5, -58.5, 20.5, "F1 Beam"));
		});
		context.waitTicks(5);
		WorldMarkers.Frame f1 = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> {
			WorldMarkers.setHideLabelsWithHud(false);
			provider.markers = List.of(labelAndBeam(0.5, 400, 20.5, "Above"), labelAndBeam(0.5, -100, 20.5, "Below"));
		});
		context.waitTicks(5);
		WorldMarkers.Frame clipped = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> provider.markers = List.of());
		LOGGER.info("beams with F1 {}; above and below the world {}", f1, clipped);
		check(f1.beams() == 1 && f1.submitted() == 1, "with F1 the label hides, the beam stays: " + f1);
		check(clipped.beams() == 1, "no beam above the build height, one from the floor for a marker below the world: " + clipped);
	}

	/** AC-MARK-06: 150 label-and-beam markers ahead and one behind the camera. */
	private static void performanceWith150Markers(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server,
			TestProvider provider) {
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		singleplayer.getConnection().waitForChunksRender();
		List<Marker> markers = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			for (int j = 0; j < 10; j++) {
				markers.add(labelAndBeam(-14.5 + 2 * i, -58.5, 20.5 + 4 * j, "Marker " + i + "/" + j));
			}
		}
		markers.add(labelAndBeam(0.5, -58.5, -20.5, "Behind"));
		context.runOnClient(client -> {
			provider.markers = List.copyOf(markers);
			WorldMarkers.setRecordFrames(true);
		});
		context.waitFor(client -> WorldMarkers.stats().frames() >= 600, 20 * 120);
		WorldMarkers.Stats stats = context.computeOnClient(client -> WorldMarkers.stats());
		WorldMarkers.Frame frame = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> {
			WorldMarkers.setRecordFrames(false);
			provider.markers = List.of();
		});
		LOGGER.info("151 markers: {} frames, average {} ms per frame; last frame {}", stats.frames(), String.format("%.3f", stats.averageMillis()), frame);
		check(stats.averageMillis() <= 1.0, "the marker pass averages at most 1 ms per frame over 600 frames: " + stats);
		check(frame.submitted() + frame.culled() == 151 && frame.culled() >= 1, "the marker behind the camera is not submitted: " + frame);
	}

	private static Shot shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server, TestProvider provider,
			Marker marker, String name) {
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.runOnClient(client -> provider.markers = List.of());
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(10);
		Path without = context.takeScreenshot(name + "-without");
		context.runOnClient(client -> provider.markers = List.of(marker));
		context.waitTicks(5);
		Path with = context.takeScreenshot(name);
		context.runOnClient(client -> provider.markers = List.of());
		return Shot.of(with, without);
	}

	/**
	 * A screenshot pair: exact-white text pixels added, pixels changed in the top 8% of the image (above
	 * the walls, where only the beam can be), every changed pixel, exact-magenta pixels added, and added
	 * pixels with the red beam's hue.
	 */
	private record Shot(int textPixels, int topChanged, int changed, int magentaPixels, int redPixels) {
		static Shot of(Path with, Path without) {
			try {
				BufferedImage a = ImageIO.read(with.toFile());
				BufferedImage b = ImageIO.read(without.toFile());
				int top = (int) (a.getHeight() * 0.08);
				int text = 0, topChanged = 0, changed = 0, magenta = 0, red = 0;
				for (int y = 0; y < a.getHeight(); y++) {
					for (int x = 0; x < a.getWidth(); x++) {
						int rgbWith = a.getRGB(x, y) & 0xFFFFFF;
						int rgbWithout = b.getRGB(x, y) & 0xFFFFFF;
						if (rgbWith != rgbWithout) {
							changed++;
							if (y < top) {
								topChanged++;
							}
							if (rgbWith == WHITE) {
								text++;
							}
							int r = rgbWith >> 16 & 0xFF, g = rgbWith >> 8 & 0xFF, bl = rgbWith & 0xFF;
							// Magenta through the lightmap: within a few steps of #FF00FF, not tinted blue or grey.
							if (r >= 0xF0 && g <= 0x10 && bl >= 0xF0) {
								magenta++;
							}
							if (r >= g + 40 && r >= bl + 40) {
								red++;
							}
						}
					}
				}
				return new Shot(text, topChanged, changed, magenta, red);
			} catch (IOException e) {
				throw new AssertionError("cannot read screenshots", e);
			}
		}
	}

	private static void setHudHidden(Minecraft client, boolean hidden) {
		if (client.gui.hud.isHidden() != hidden) {
			client.gui.hud.toggle();
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
