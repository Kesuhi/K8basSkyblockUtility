package com.k8bas.skyblockutility.gametest;

import com.k8bas.skyblockutility.debug.DebugCommand;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

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
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();

			commandsAreRegistered(context);
			islandCanBeForced(context);
			tabAndSidebarAreDumped(context, server);
			containerDumpCapturesOnlyArmedAllowlistedMenus(context, server);
			entityDumpListsOnlyDisplayedNameTags(context, server);
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
					List.of("ksu", "debug", "dump", "entities"),
					List.of("ksu", "debug", "dump", "containers", "on"),
					List.of("ksu", "debug", "dump", "containers", "off"),
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
		// Decision 2026-10-01 (S-7): the player's own name is masked in the written dump.
		String self = context.computeOnClient(client -> client.player.getGameProfile().name());
		String tabLine = log.lines().filter(line -> line.contains(DebugCommand.DUMP_TAG + " tab 000 ")).findFirst().orElseThrow();
		check(tabLine.contains("name=Self") && !tabLine.contains(self), "own name masked in the tab dump: " + tabLine);
		check(log.contains(DebugCommand.DUMP_TAG + " sidebar 00 score=5 | Beta"), "sidebar lines tagged in latest.log");
		server.runCommand("scoreboard objectives remove k8test");
	}

	/** T0.4b: while armed, an allowlisted menu is written once after its contents are stable;
	 *  nothing is written while disarmed or for other menus. Opening happens on the server, as on
	 *  Hypixel; the dump only reads the screen. */
	private static void containerDumpCapturesOnlyArmedAllowlistedMenus(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("setblock 2 -60 2 minecraft:chest{CustomName:\"Croesus\"}");
		server.runCommand("setblock 4 -60 2 minecraft:chest{CustomName:\"Ender Stash\"}");
		server.runCommand("item replace block 2 -60 2 container.0 with minecraft:diamond[minecraft:custom_name=\"Test Gem\",minecraft:lore=[\"Line one\",\"Line two\"]]");
		server.runCommand("item replace block 4 -60 2 container.0 with minecraft:emerald");

		openAndCloseChest(context, server, new BlockPos(2, -60, 2));
		check(!latestLog().contains("title=Croesus"), "nothing captured while disarmed");

		runClientCommand(context, "ksu debug dump containers on");
		openAndCloseChest(context, server, new BlockPos(2, -60, 2));
		String log = latestLog();
		check(log.contains(DebugCommand.DUMP_TAG + " container 1 title=Croesus slots=27"), "Croesus menu captured");
		check(log.contains("container 1 slot 0 item=minecraft:diamond sbid=- count=1 | Test Gem"), "slot line with item and name");
		check(log.contains("container 1 slot 0 lore 0 | Line one") && log.contains("container 1 slot 0 lore 1 | Line two"), "lore lines");
		check(count(log, "title=Croesus") == 1, "captured exactly once");

		openAndCloseChest(context, server, new BlockPos(4, -60, 2));
		check(!latestLog().contains("title=Ender Stash"), "menus outside the allowlist are not captured");

		runClientCommand(context, "ksu debug dump containers off");
		openAndCloseChest(context, server, new BlockPos(2, -60, 2));
		check(count(latestLog(), "title=Croesus") == 1, "nothing captured after disarming");

		server.runCommand("setblock 2 -60 2 minecraft:air");
		server.runCommand("setblock 4 -60 2 minecraft:air");
	}

	/** T0.4b: displayed name tags are listed with the entity below them; hidden names and an
	 *  invisible body without a displayed name are not. Fixture source for T3.1 (AC-GLOW-08). */
	private static void entityDumpListsOnlyDisplayedNameTags(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("summon minecraft:husk 0.5 -60 4.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		server.runCommand("summon minecraft:armor_stand 0.5 -58 4.5 {Invisible:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:\"Husk Tag\"}");
		server.runCommand("summon minecraft:armor_stand 1.5 -60 4.5 {Invisible:1b,NoGravity:1b}");
		server.runCommand("summon minecraft:armor_stand 2.5 -60 4.5 {NoGravity:1b,CustomName:\"Hidden Name\"}");
		context.waitTicks(10);

		List<String> entities = context.computeOnClient(client -> DebugCommand.dumpEntities());
		check(entities.size() == 1, "one displayed name tag: " + entities);
		String line = entities.get(0);
		check(line.startsWith("00 type=minecraft:armor_stand invisible=true"), "tag entity: " + line);
		check(line.contains("below=minecraft:husk dy=2.00") && line.endsWith("| Husk Tag"), "entity below and height: " + line);

		runClientCommand(context, "ksu debug dump entities");
		context.waitTicks(2);
		check(latestLog().contains(DebugCommand.DUMP_TAG + " entities 00 type=minecraft:armor_stand"), "entity lines tagged in latest.log");
		server.runCommand("kill @e[type=!minecraft:player]");
		context.waitTicks(2);
	}

	private static void openAndCloseChest(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		server.runOnServer(minecraftServer -> {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			if (player.level().getBlockEntity(pos) instanceof MenuProvider provider) {
				player.openMenu(provider);
			}
		});
		context.waitFor(client -> client.gui.screen() instanceof AbstractContainerScreen<?>);
		context.waitTicks(6);
		context.runOnClient(client -> client.player.closeContainer());
		context.waitFor(client -> client.gui.screen() == null);
	}

	private static int count(String text, String part) {
		int count = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
			count++;
		}
		return count;
	}

	/** Baseline for T1.1: the label in the open and behind an opaque wall, in the rule's colour ("White
	 *  waypoint labels" off, R21). T3.4 replaced the 1.0.1 look on purpose (REQ-PORT-06), so the screenshots
	 *  are kept for comparison but no longer matched against the 1.0.1 template. */
	private static void waypointBaselineScreenshots(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.runOnClient(client -> {
			// The screenshots hide the HUD; labels would hide with it, like name tags (EC-MARK-06).
			WorldMarkers.setHideLabelsWithHud(false);
			setHudHidden(client, true);
			NpcRule rule = TestWaypoints.fixedRule("Baseline NPC", "Hub", 0x0AA351, 0, -60, 6);
			TestWaypoints.show(List.of(rule), TestWaypoints.settings(false, false, true));
		});
		runClientCommand(context, "ksu debug island Hub");
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("t0.4-waypoint-open");

		server.runCommand("fill -4 -60 3 4 -54 3 minecraft:stone");
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("t0.4-waypoint-behind-stone");

		// The same view without the waypoint, so the label's pixels can be cut out by difference.
		context.runOnClient(client -> TestWaypoints.clear());
		context.waitTicks(5);
		context.takeScreenshot("t0.4-stone-no-waypoint");

		server.runCommand("fill -4 -60 3 4 -54 3 minecraft:air");
		runClientCommand(context, "ksu debug island clear");
		context.runOnClient(client -> {
			setHudHidden(client, false);
			WorldMarkers.setHideLabelsWithHud(true);
			TestWaypoints.clear();
		});
	}

	/** 26.2 replaces Options.hideGui with Hud.toggle(). */
	private static void setHudHidden(Minecraft client, boolean hidden) {
		if (client.gui.hud.isHidden() != hidden) {
			client.gui.hud.toggle();
		}
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
