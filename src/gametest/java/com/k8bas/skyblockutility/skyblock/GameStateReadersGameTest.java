package com.k8bas.skyblockutility.skyblock;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tier C check of the game-state readers' game side (T3.0a): the sidebar reader reads the scoreboard the
 * server sends, composes each line from team prefix, holder and suffix and strips the codes (REQ-GS-03);
 * the tab reader reads the client's player list (REQ-GS-02; a singleplayer list has no widgets); both read
 * nothing while no subscriber is active (REQ-GS-16).
 */
public class GameStateReadersGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		AtomicBoolean active = new AtomicBoolean(false);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.runOnClient(client -> {
				SidebarReader.subscribe("gametest", active::get);
				TabReader.subscribe("gametest", active::get);
			});
			server.runCommand("scoreboard objectives add k8read dummy \"§6§lSKYBLOCK\"");
			server.runCommand("scoreboard objectives setdisplay sidebar k8read");
			server.runCommand("team add k8read");
			server.runCommand("team modify k8read prefix \" §7Glacite M\"");
			server.runCommand("team modify k8read suffix \"§bineshafts\"");
			server.runCommand("scoreboard players set §q k8read 6");
			server.runCommand("team join k8read §q");
			server.runCommand("scoreboard players set Purse k8read 7");
			server.runCommand("scoreboard players set #hidden k8read 9");
			context.waitTicks(5);

			int sidebarParses = context.computeOnClient(client -> SidebarReader.parseCount());
			int tabParses = context.computeOnClient(client -> TabReader.parseCount());
			context.waitTicks(20);
			check(context.computeOnClient(client -> SidebarReader.parseCount()) == sidebarParses, "no sidebar parse while unsubscribed");
			check(context.computeOnClient(client -> TabReader.parseCount()) == tabParses, "no tab parse while unsubscribed");
			check(context.computeOnClient(client -> SidebarReader.snapshot()).equals(SidebarSnapshot.EMPTY), "nothing kept while unsubscribed");

			active.set(true);
			context.waitTicks(3);
			SidebarSnapshot sidebar = context.computeOnClient(client -> SidebarReader.snapshot());
			check(sidebar.getTitle().equals("SKYBLOCK"), "title without codes: " + sidebar);
			check(sidebar.getLines().equals(List.of("Purse", "Glacite Mineshafts")),
					"lines in display order, hidden holder skipped, prefix + holder + suffix joined and stripped: " + sidebar);
			check(context.computeOnClient(client -> TabReader.snapshot()).equals(TabSnapshot.EMPTY), "a singleplayer tab list has no widgets");
			check(context.computeOnClient(client -> TabReader.parseCount()) > tabParses, "the tab list was read once subscribed");

			int parses = context.computeOnClient(client -> SidebarReader.parseCount());
			context.waitTicks(20);
			check(context.computeOnClient(client -> SidebarReader.parseCount()) == parses, "an unchanged sidebar is not parsed again");
			server.runCommand("scoreboard players set Purse k8read 1");
			context.waitTicks(3);
			check(context.computeOnClient(client -> SidebarReader.snapshot()).getLines().equals(List.of("Glacite Mineshafts", "Purse")),
					"a change is seen within a few ticks");

			active.set(false);
			context.waitTicks(2);
			check(context.computeOnClient(client -> SidebarReader.snapshot()).equals(SidebarSnapshot.EMPTY), "dropped when the last consumer is off");
			server.runCommand("scoreboard objectives remove k8read");
			server.runCommand("team remove k8read");
		} finally {
			active.set(false);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
