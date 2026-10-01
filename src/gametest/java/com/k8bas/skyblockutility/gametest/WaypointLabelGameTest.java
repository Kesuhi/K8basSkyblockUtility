package com.k8bas.skyblockutility.gametest;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcWaypointRenderer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AC-PORT-06 / REQ-PORT-07: a fixed waypoint label stays fully legible behind nothing, stone, glass
 * and water, at 5 and 30 blocks, and keeps its 10-block size beyond 10 blocks. Each label screenshot
 * is paired with the same view without the label; their difference is the label.
 */
public class WaypointLabelGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final Map<String, String> OBSTACLES = new LinkedHashMap<>();

	static {
		OBSTACLES.put("nothing", null);
		OBSTACLES.put("stone", "minecraft:stone");
		OBSTACLES.put("glass", "minecraft:light_blue_stained_glass");
		OBSTACLES.put("water", "minecraft:water");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			context.runOnClient(client -> {
				setHudHidden(client, true);
				client.options.cloudStatus().set(CloudStatus.OFF);
				IslandTracker.forceIsland("Hub");
			});

			Map<String, Label> labels = new LinkedHashMap<>();
			for (int distance : new int[]{5, 10, 30}) {
				for (Map.Entry<String, String> obstacle : OBSTACLES.entrySet()) {
					if (distance == 10 && obstacle.getValue() != null) {
						continue; // 10 blocks is only the size reference
					}
					String name = distance + "-" + obstacle.getKey();
					labels.put(name, measure(context, singleplayer, server, distance, obstacle.getValue(), name));
				}
			}

			for (Map.Entry<String, Label> entry : labels.entrySet()) {
				LOGGER.info("label {}: {}", entry.getKey(), entry.getValue());
			}
			for (int distance : new int[]{5, 30}) {
				Label open = labels.get(distance + "-nothing");
				check(open.height > 10 && open.textPixels > 50, "label visible at " + distance + " blocks: " + open);
				for (String obstacle : List.of("stone", "glass", "water")) {
					Label behind = labels.get(distance + "-" + obstacle);
					check(Math.abs(behind.height - open.height) <= 2 && Math.abs(behind.width - open.width) <= 2,
							"same label box behind " + obstacle + " at " + distance + ": " + behind + " vs " + open);
					// Glyphs land slightly differently on the pixel grid from frame to frame; a label that
					// translucent terrain draws over keeps none of its exact text colour.
					check(behind.textPixels >= open.textPixels * 0.6,
							"text keeps its exact colour behind " + obstacle + " at " + distance + ": " + behind + " vs " + open);
				}
			}
			int at10 = labels.get("10-nothing").height;
			int at30 = labels.get("30-nothing").height;
			check(Math.abs(at30 - at10) <= 2, "height at 30 blocks (" + at30 + ") equals height at 10 blocks (" + at10 + ") +-2 px");

			waypointsFollowTheIsland(context, singleplayer, server);

			context.runOnClient(client -> {
				setHudHidden(client, false);
				IslandTracker.forceIsland(null);
				NpcWaypointRenderer.setActiveWaypoints(List.of());
			});
		}
	}

	/** AC-LOC-04 [B] (T1.9b): a fixed Dungeon Hub waypoint renders in the Dungeon Hub and not in a run. */
	private static void waypointsFollowTheIsland(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server) {
		server.runCommand("fill -8 -60 1 8 -50 40 minecraft:air");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.runOnClient(client -> {
			NpcWaypointRenderer.setActiveWaypoints(List.of());
			IslandTracker.forceIsland(null);
		});
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(10);
		Path none = context.takeScreenshot("t1.9b-no-waypoint");

		context.runOnClient(client -> {
			NpcRule croesus = new NpcRule();
			croesus.label = "Croesus";
			croesus.island = "Dungeon Hub";
			croesus.fixed = true;
			croesus.color = TEXT_COLOR;
			croesus.x = 0.5;
			croesus.y = -60;
			croesus.z = 5.5;
			NpcWaypointRenderer.setActiveWaypoints(List.of(croesus));
			IslandTracker.forceIsland("Dungeon Hub");
		});
		context.waitTicks(5);
		Label inLobby = Label.of(context.takeScreenshot("t1.9b-dungeon-hub"), none);
		context.runOnClient(client -> IslandTracker.forceIsland("Catacombs"));
		context.waitTicks(5);
		Label inRun = Label.of(context.takeScreenshot("t1.9b-catacombs"), none);
		LOGGER.info("Dungeon Hub waypoint: in the lobby {}, in a run {}", inLobby, inRun);
		check(inLobby.textPixels > 50, "the Dungeon Hub waypoint renders in the Dungeon Hub: " + inLobby);
		check(inRun.textPixels == 0, "the Dungeon Hub waypoint does not render in a run: " + inRun);
		context.runOnClient(client -> IslandTracker.forceIsland("Hub"));
	}

	private static Label measure(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server,
			int distance, String obstacle, String name) {
		// In front of the label, also of a label beyond 10 blocks, which is drawn pulled in to 10 blocks.
		int wallZ = distance <= 5 ? 2 : 5;
		server.runCommand("fill -8 -60 1 8 -50 40 minecraft:air");
		if ("minecraft:water".equals(obstacle)) {
			// Clear glass (not translucent) keeps the water from flowing to the player and moving the camera.
			server.runCommand("fill -8 -60 " + (wallZ - 1) + " 8 -51 " + (wallZ + 1) + " minecraft:glass");
			server.runCommand("fill -7 -60 " + wallZ + " 7 -52 " + wallZ + " minecraft:water");
		} else if (obstacle != null) {
			server.runCommand("fill -8 -60 " + wallZ + " 8 -52 " + wallZ + " " + obstacle);
		}
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.runOnClient(client -> NpcWaypointRenderer.setActiveWaypoints(List.of()));
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(10);
		Path without = context.takeScreenshot("t1.3-" + name + "-without");

		context.runOnClient(client -> {
			NpcRule rule = new NpcRule();
			rule.label = "Label Test";
			rule.island = "Hub";
			rule.fixed = true;
			rule.color = TEXT_COLOR;
			rule.x = 0.5;
			rule.y = -60;
			rule.z = distance + 0.5;
			NpcWaypointRenderer.setActiveWaypoints(List.of(rule));
		});
		context.waitTicks(5);
		Path with = context.takeScreenshot("t1.3-" + name);
		return Label.of(with, without);
	}

	/** The label's text, found as the pixels that have exactly the text colour with the label and
	 *  not without it: their bounding box and count. Animated water and the sky do not affect it. */
	private record Label(int width, int height, int textPixels) {
		static Label of(Path with, Path without) {
			try {
				BufferedImage a = ImageIO.read(with.toFile());
				BufferedImage b = ImageIO.read(without.toFile());
				int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1, text = 0;
				for (int y = 0; y < a.getHeight(); y++) {
					for (int x = 0; x < a.getWidth(); x++) {
						boolean textWith = (a.getRGB(x, y) & 0xFFFFFF) == TEXT_COLOR;
						boolean textWithout = (b.getRGB(x, y) & 0xFFFFFF) == TEXT_COLOR;
						if (textWith && !textWithout) {
							minX = Math.min(minX, x);
							minY = Math.min(minY, y);
							maxX = Math.max(maxX, x);
							maxY = Math.max(maxY, y);
							text++;
						}
					}
				}
				return maxX < 0 ? new Label(0, 0, 0) : new Label(maxX - minX + 1, maxY - minY + 1, text);
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
