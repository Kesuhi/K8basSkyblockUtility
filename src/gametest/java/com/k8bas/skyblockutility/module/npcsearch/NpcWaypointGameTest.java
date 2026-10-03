package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.gametest.TestWaypoints;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.render.marker.FrameTimer;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
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

/**
 * T3.4, the Skyblocker-style NPC waypoints in a real frame (Improved Transparency off):
 * <ul>
 *   <li>AC-NPCWP-03: a white label with no plate, a yellow distance line and a beam in the island
 *       colour (Crimson Isle, red) for a rule coloured blue; with "White waypoint labels" OFF both lines
 *       are blue and the beam stays red. The label stays visible behind stone, glass and water, and a
 *       wall that fills the view hides the beam.</li>
 *   <li>AC-NPCWP-04: the label's text height is equal within 1 px at 20 and 100 blocks, larger at 5.</li>
 *   <li>AC-NPCWP-05: "Show beacon beams" OFF keeps the label; "Show distance" OFF leaves one line.</li>
 *   <li>AC-NPCWP-10: 100 waypoints raise the mean frame time by at most 1 ms against none, over 600
 *       frames each in the 854x480 gametest window.</li>
 * </ul>
 */
public class NpcWaypointGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final int WHITE = 0xFFFFFF;
	private static final int YELLOW = NpcWaypointMarkers.DISTANCE_YELLOW;
	private static final int RULE_BLUE = 0x00AAFF;
	private static final String ISLAND = "Crimson Isle";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			context.runOnClient(client -> {
				WorldMarkers.setHideLabelsWithHud(false);
				setHudHidden(client, true);
				client.options.cloudStatus().set(CloudStatus.OFF);
				IslandTracker.forceIsland(ISLAND);
			});
			try {
				lookAndColours(context, singleplayer, server);
				visibleBehindObstacles(context, singleplayer, server);
				constantSizeFromTenBlocks(context, singleplayer, server);
				togglesHideTheirPart(context, singleplayer, server);
				hundredWaypointsCostAtMostOneMillisecond(context, singleplayer, server);
			} finally {
				context.runOnClient(client -> {
					TestWaypoints.clear();
					WorldMarkers.setHideLabelsWithHud(true);
					WorldMarkers.setRecordFrames(false);
					setHudHidden(client, false);
					IslandTracker.forceIsland(null);
				});
			}
		}
	}

	/** The waypoint block 20 blocks ahead at the player's feet: the label is at about eye height. */
	private static NpcRule blueRule(int distance) {
		return TestWaypoints.fixedRule("Researcher Timmy", ISLAND, RULE_BLUE, 0, -60, distance);
	}

	/** AC-NPCWP-03, the look; REQ-NPCWP-02 no plate; R21 the colours with the setting OFF. */
	private static void lookAndColours(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server) {
		Shot defaults = shoot(context, singleplayer, server, List.of(blueRule(20)), TestWaypoints.DEFAULTS, "t3.4-defaults");
		Shot ruleColour = shoot(context, singleplayer, server, List.of(blueRule(20)), TestWaypoints.settings(false, true, true), "t3.4-rule-colour");
		Shot noBeam = shoot(context, singleplayer, server, List.of(blueRule(20)), TestWaypoints.LABELS_ONLY, "t3.4-no-plate");
		LOGGER.info("waypoint look: defaults {}, white labels off {}, without beam {}", defaults, ruleColour, noBeam);
		check(defaults.white > 50 && defaults.yellow > 20 && defaults.blue == 0, "a white label with a yellow distance line: " + defaults);
		check(defaults.redTop > 20 && defaults.frame.beams() == 1, "a beam in the island colour (Crimson Isle, red) above the label: " + defaults);
		check(ruleColour.blue >= (defaults.white + defaults.yellow) * 0.9 && ruleColour.white == 0 && ruleColour.yellow == 0,
				"with \"White waypoint labels\" OFF both lines take the rule's colour: " + ruleColour);
		check(ruleColour.redTop > 20, "the beam keeps the island colour with white labels OFF: " + ruleColour);
		// The name sits 1.5 blocks above the block (y -58.5), about at eye height (-58.38): near the middle
		// of the 480 px window. At the block itself (no rise) it would be about 27 px lower.
		check(Math.abs(defaults.whiteCentreY - 240) <= 8, "the label is drawn 1.5 blocks above its block: name centre row " + defaults.whiteCentreY);
		// A plate would darken every pixel of the text's box; without one only the glyphs change there.
		check(noBeam.boxArea > 0 && noBeam.otherInBox <= noBeam.boxArea / 20, "no background plate behind the label: " + noBeam);
	}

	/** AC-NPCWP-03: behind stone, glass and water the label stays; a wall that fills the view hides the beam. */
	private static void visibleBehindObstacles(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server) {
		Map<String, String[]> obstacles = new LinkedHashMap<>();
		obstacles.put("nothing", new String[0]);
		obstacles.put("stone", new String[] {"fill -8 -60 5 8 -57 5 minecraft:stone"});
		obstacles.put("glass", new String[] {"fill -8 -60 5 8 -57 5 minecraft:light_blue_stained_glass"});
		obstacles.put("water", new String[] {"fill -8 -60 5 8 -57 9 minecraft:glass", "fill -7 -59 6 7 -57 8 minecraft:water"});
		Map<String, Shot> shots = new LinkedHashMap<>();
		for (Map.Entry<String, String[]> obstacle : obstacles.entrySet()) {
			server.runCommand("fill -8 -60 1 8 -40 40 minecraft:air");
			for (String command : obstacle.getValue()) {
				server.runCommand(command);
			}
			shots.put(obstacle.getKey(), shoot(context, singleplayer, server, List.of(blueRule(20)), TestWaypoints.DEFAULTS, "t3.4-behind-" + obstacle.getKey()));
		}
		server.runCommand("fill -8 -60 1 8 -40 40 minecraft:air");
		server.runCommand("fill -20 -60 5 20 -30 5 minecraft:stone");
		Shot wall = shoot(context, singleplayer, server, List.of(blueRule(20)), TestWaypoints.DEFAULTS, "t3.4-behind-a-full-wall");
		server.runCommand("fill -20 -60 5 20 -30 5 minecraft:air");
		LOGGER.info("waypoint behind obstacles {}; behind a wall that fills the view {}", shots, wall);

		Shot open = shots.get("nothing");
		for (Map.Entry<String, Shot> entry : shots.entrySet()) {
			Shot shot = entry.getValue();
			check(shot.white >= open.white * 0.9 && shot.yellow >= open.yellow * 0.9, "the label stays legible behind " + entry.getKey() + ": " + shot + " vs " + open);
		}
		check(wall.white >= open.white * 0.9, "the label shows through a wall that fills the view: " + wall);
		check(wall.frame.beams() == 1 && wall.red == 0, "the beam behind that wall is hidden (depth-tested): " + wall);
	}

	/** AC-NPCWP-04: constant on-screen size from 10 blocks out, true size closer. */
	private static void constantSizeFromTenBlocks(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server) {
		NpcWaypointMarkers.Settings nameOnly = TestWaypoints.settings(true, false, false);
		Shot at5 = shoot(context, singleplayer, server, List.of(blueRule(5)), nameOnly, "t3.4-size-5");
		Shot at20 = shoot(context, singleplayer, server, List.of(blueRule(20)), nameOnly, "t3.4-size-20");
		Shot at100 = shoot(context, singleplayer, server, List.of(blueRule(100)), nameOnly, "t3.4-size-100");
		LOGGER.info("waypoint size: at 5 {}, at 20 {}, at 100 {}", at5, at20, at100);
		check(at20.whiteHeight > 0 && Math.abs(at100.whiteHeight - at20.whiteHeight) <= 1,
				"the text height is equal at 20 and 100 blocks within 1 px: " + at20.whiteHeight + " vs " + at100.whiteHeight);
		check(at5.whiteHeight > at20.whiteHeight, "the text is larger at 5 blocks: " + at5.whiteHeight + " vs " + at20.whiteHeight);
	}

	/** AC-NPCWP-05 [C]: each toggle hides only its own part; the distance is measured from the player. */
	private static void togglesHideTheirPart(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server) {
		context.runOnClient(client -> WorldMarkers.setRecordFrames(true));
		Shot both = shoot(context, singleplayer, server, List.of(blueRule(20)), TestWaypoints.DEFAULTS, "t3.4-toggles-on");
		Shot noBeams = shoot(context, singleplayer, server, List.of(blueRule(20)), TestWaypoints.settings(true, false, true), "t3.4-beams-off");
		Shot noDistance = shoot(context, singleplayer, server, List.of(blueRule(20)), TestWaypoints.settings(true, true, false), "t3.4-distance-off");
		context.runOnClient(client -> WorldMarkers.setRecordFrames(false));
		LOGGER.info("toggles: on {}, beams off {}, distance off {}", both, noBeams, noDistance);
		// The player stands at (0.5, -60, 0.5); the label is at (0.5, -58.5, 20.5): 20.06 m.
		check(both.frame.distanceTexts().equals(List.of("20m")), "the distance line reads 20m: " + both.frame);
		check(noBeams.white > 50 && noBeams.frame.beams() == 0 && noBeams.redTop == 0, "\"Show beacon beams\" OFF: the label, no beam: " + noBeams);
		check(noDistance.white > 50 && noDistance.yellow == 0 && noDistance.frame.distanceTexts().isEmpty() && noDistance.redTop > 20,
				"\"Show distance\" OFF: one text line, the beam stays: " + noDistance);
	}

	/** AC-NPCWP-10: 100 waypoints on one island against 0. */
	private static void hundredWaypointsCostAtMostOneMillisecond(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			TestServerContext server) {
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		singleplayer.getConnection().waitForChunksRender();
		List<NpcRule> rules = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				rules.add(TestWaypoints.fixedRule("NPC " + i + "/" + j, ISLAND, RULE_BLUE, -9 + 2 * i, -60, 20 + 4 * j));
			}
		}
		context.runOnClient(client -> TestWaypoints.show(rules, TestWaypoints.DEFAULTS));
		context.waitTicks(5);
		WorldMarkers.Frame frame = context.computeOnClient(client -> WorldMarkers.lastFrame());
		int[] window = context.computeOnClient(client -> new int[] {client.getWindow().getWidth(), client.getWindow().getHeight()});
		FrameTimer.Comparison comparison = FrameTimer.compare(context, () -> TestWaypoints.show(rules, TestWaypoints.DEFAULTS), TestWaypoints::clear, 600);
		LOGGER.info("100 waypoints at {}x{}: {}; frame {}", window[0], window[1], comparison, frame);
		check(frame.submitted() == 100 && frame.beams() == 100, "all 100 waypoints are drawn with their beams: " + frame);
		check(comparison.frameDelta() <= 1.0, "100 waypoints add at most 1 ms of CPU to the mean frame time: " + comparison);
		check(comparison.with().minDrawn() == 100 && comparison.without().maxDrawn() == 0, "every timed frame drew all or none of the waypoints: " + comparison);
	}

	private static Shot shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server, List<NpcRule> rules,
			NpcWaypointMarkers.Settings settings, String name) {
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.runOnClient(client -> TestWaypoints.clear());
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(10);
		Path without = context.takeScreenshot(name + "-without");
		context.runOnClient(client -> TestWaypoints.show(rules, settings));
		context.waitTicks(5);
		Path with = context.takeScreenshot(name);
		WorldMarkers.Frame frame = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> TestWaypoints.clear());
		return Shot.of(with, without, frame);
	}

	/**
	 * A screenshot pair: exact white, yellow and rule-blue pixels added; the white text's box height;
	 * added red-hue pixels (the Crimson Isle beam) anywhere and in the top 8% of the image, above the
	 * label, where only the beam can be; and, inside the box around all text pixels, how many other
	 * pixels changed (a background plate would change all of them).
	 */
	private record Shot(int white, int yellow, int blue, int whiteHeight, int whiteCentreY, int red, int redTop, int boxArea, int otherInBox, WorldMarkers.Frame frame) {
		static Shot of(Path with, Path without, WorldMarkers.Frame frame) {
			try {
				BufferedImage a = ImageIO.read(with.toFile());
				BufferedImage b = ImageIO.read(without.toFile());
				int top = (int) (a.getHeight() * 0.08);
				int white = 0, yellow = 0, blue = 0, red = 0, redTop = 0;
				int whiteMinY = Integer.MAX_VALUE, whiteMaxY = -1;
				int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
				boolean[][] changed = new boolean[a.getHeight()][a.getWidth()];
				boolean[][] text = new boolean[a.getHeight()][a.getWidth()];
				for (int y = 0; y < a.getHeight(); y++) {
					for (int x = 0; x < a.getWidth(); x++) {
						int rgbWith = a.getRGB(x, y) & 0xFFFFFF;
						int rgbWithout = b.getRGB(x, y) & 0xFFFFFF;
						if (rgbWith == rgbWithout) {
							continue;
						}
						changed[y][x] = true;
						boolean isText = rgbWith == WHITE || rgbWith == YELLOW || rgbWith == RULE_BLUE;
						if (rgbWith == WHITE) {
							white++;
							whiteMinY = Math.min(whiteMinY, y);
							whiteMaxY = Math.max(whiteMaxY, y);
						} else if (rgbWith == YELLOW) {
							yellow++;
						} else if (rgbWith == RULE_BLUE) {
							blue++;
						}
						if (isText) {
							text[y][x] = true;
							minX = Math.min(minX, x);
							minY = Math.min(minY, y);
							maxX = Math.max(maxX, x);
							maxY = Math.max(maxY, y);
						}
						int r = rgbWith >> 16 & 0xFF, g = rgbWith >> 8 & 0xFF, bl = rgbWith & 0xFF;
						if (r >= g + 40 && r >= bl + 40) {
							red++;
							if (y < top) {
								redTop++;
							}
						}
					}
				}
				int boxArea = 0, otherInBox = 0;
				if (maxX >= 0) {
					for (int y = minY; y <= maxY; y++) {
						for (int x = minX; x <= maxX; x++) {
							boxArea++;
							if (changed[y][x] && !text[y][x]) {
								otherInBox++;
							}
						}
					}
				}
				int whiteHeight = whiteMaxY < 0 ? 0 : whiteMaxY - whiteMinY + 1;
				int whiteCentreY = whiteMaxY < 0 ? -1 : (whiteMinY + whiteMaxY) / 2;
				return new Shot(white, yellow, blue, whiteHeight, whiteCentreY, red, redTop, boxArea, otherInBox, frame);
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
