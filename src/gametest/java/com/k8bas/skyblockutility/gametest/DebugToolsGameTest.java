package com.k8bas.skyblockutility.gametest;

import com.k8bas.skyblockutility.debug.DebugCommand;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcWaypointRenderer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tier C checks of the debug tools (T0.4) in a singleplayer world, plus baseline screenshots of the
 * 1.0.1 waypoint label for the submit-based renderer (T1.1) to compare against.
 */
public class DebugToolsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();

			commandsAreRegistered(context);
			islandCanBeForced(context);
			tabAndSidebarAreDumped(context, server);
			waypointBaselineScreenshots(context, singleplayer, server);
		}
	}

	/** AC-GS-13 [B], AC-LOC-09 [B]: in a dev run all debug commands exist. */
	private static void commandsAreRegistered(ClientGameTestContext context) {
		context.runOnClient(client -> {
			var dispatcher = ClientCommands.getActiveDispatcher();
			check(dispatcher != null, "client command dispatcher available");
			for (List<String> path : List.of(
					List.of("ksu", "debug", "dump", "tab"),
					List.of("ksu", "debug", "dump", "sidebar"),
					List.of("ksu", "debug", "island"),
					List.of("kskyblockutility", "debug", "dump", "tab"))) {
				check(dispatcher.findNode(path) != null, "command registered: /" + String.join(" ", path));
			}
			check(FabricLoader.getInstance().isDevelopmentEnvironment(), "gametests run in the dev environment");
		});
	}

	/** AC-LOC-09 [B]: `/ksu debug island Catacombs` sets the island; `clear` removes the override. */
	private static void islandCanBeForced(ClientGameTestContext context) {
		runClientCommand(context, "ksu debug island Catacombs");
		check("Catacombs".equals(context.computeOnClient(client -> IslandTracker.getCurrentIsland())), "island forced to Catacombs");
		runClientCommand(context, "ksu debug island Dungeon Hub");
		check("Dungeon Hub".equals(context.computeOnClient(client -> IslandTracker.getCurrentIsland())), "multi-word island name");
		runClientCommand(context, "ksu debug island clear");
		check(context.computeOnClient(client -> IslandTracker.getCurrentIsland()) == null, "override cleared");
	}

	/** AC-GS-13 [B]: the dumps read what the client shows and write tagged lines to the log. */
	private static void tabAndSidebarAreDumped(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("scoreboard objectives add k8test dummy \"Test Board\"");
		server.runCommand("scoreboard objectives setdisplay sidebar k8test");
		server.runCommand("scoreboard players set Alpha k8test 3");
		server.runCommand("scoreboard players set Beta k8test 5");
		context.waitTicks(5);

		List<String> tab = context.computeOnClient(client -> DebugCommand.dumpTab());
		check(tab.size() == 1 && tab.get(0).startsWith("000 "), "tab dump lists the one player: " + tab);
		List<String> sidebar = context.computeOnClient(client -> DebugCommand.dumpSidebar());
		check(sidebar.equals(List.of("title | Test Board", "00 score=5 | Beta", "01 score=3 | Alpha")), "sidebar dump in display order: " + sidebar);

		runClientCommand(context, "ksu debug dump tab");
		runClientCommand(context, "ksu debug dump sidebar");
		context.waitTicks(5);
		String log = latestLog();
		check(log.contains(DebugCommand.DUMP_TAG + " tab 000 "), "tab lines tagged in latest.log");
		check(log.contains(DebugCommand.DUMP_TAG + " sidebar 00 score=5 | Beta"), "sidebar lines tagged in latest.log");
		server.runCommand("scoreboard objectives remove k8test");
	}

	/** Baseline for T1.1: the 1.0.1 label in the open and behind an opaque wall. */
	private static void waypointBaselineScreenshots(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.runOnClient(client -> {
			client.options.hideGui = true;
			NpcRule rule = new NpcRule();
			rule.label = "Baseline NPC";
			rule.island = "Hub";
			rule.fixed = true;
			rule.x = 0.5;
			rule.y = -60;
			rule.z = 6.5;
			NpcWaypointRenderer.setActiveWaypoints(List.of(rule));
		});
		runClientCommand(context, "ksu debug island Hub");
		singleplayer.getClientLevel().waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("t0.4-waypoint-open");

		server.runCommand("fill -4 -60 3 4 -54 3 minecraft:stone");
		singleplayer.getClientLevel().waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("t0.4-waypoint-behind-stone");
		// The label must stay pixel-identical to the 1.0.1 baseline (T1.1 replaces the renderer).
		context.assertScreenshotContains(TestScreenshotComparisonOptions.of("waypoint-label-1.0.1-behind-stone")
				.withAlgorithm(TestScreenshotComparisonAlgorithm.exact()));

		// The same view without the waypoint, so the label's pixels can be cut out by difference.
		context.runOnClient(client -> NpcWaypointRenderer.setActiveWaypoints(List.of()));
		context.waitTicks(5);
		context.takeScreenshot("t0.4-stone-no-waypoint");

		server.runCommand("fill -4 -60 3 4 -54 3 minecraft:air");
		runClientCommand(context, "ksu debug island clear");
		context.runOnClient(client -> {
			client.options.hideGui = false;
			NpcWaypointRenderer.setActiveWaypoints(List.of());
		});
	}

	private static void runClientCommand(ClientGameTestContext context, String command) {
		context.runOnClient(client -> client.player.connection.sendCommand(command));
		context.waitTick();
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
