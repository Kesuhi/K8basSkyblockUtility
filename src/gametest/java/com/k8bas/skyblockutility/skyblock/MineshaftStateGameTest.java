package com.k8bas.skyblockutility.skyblock;

import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tier C checks of the mineshaft state's game side (T3.0m): with every consumer off it reads and keeps
 * nothing in a shaft (AC-GS-16, third bullet); with one on it reads the code from the real sidebar and names
 * it from the bundled table, and the corpse widget (absent in singleplayer) reports absent; leaving the shaft
 * clears it (AC-GS-15); and the dev-only `/ksu debug shaft` simulates a shaft for the T3.2 and T3.9d gametests.
 * The mineshaft alert (T3.2) and the possible corpse spots (T3.3) are consumers that are on by default, so both are
 * switched off for the run.
 */
public class MineshaftStateGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		AtomicBoolean consumer = new AtomicBoolean(false);
		List<Module> consumers = ModuleManager.modules().stream().filter(module -> List.of("mineshaft_alert", "corpse_spots").contains(module.id())).toList();
		check(consumers.size() == 2, "both default-on consumers found: " + consumers);
		List<Module> wereOn = context.computeOnClient(client -> consumers.stream().filter(Module::isEnabled).toList());
		context.runOnClient(client -> consumers.forEach(module -> module.setEnabled(false)));
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.runOnClient(client -> MineshaftState.subscribe("gametest", consumer::get));
			context.runOnClient(client -> check(ClientCommands.getActiveDispatcher().findNode(List.of("ksu", "debug", "shaft")) != null,
					"/ksu debug shaft exists in a dev run"));

			server.runCommand("scoreboard objectives add k8shaft dummy \"SKYBLOCK\"");
			server.runCommand("scoreboard objectives setdisplay sidebar k8shaft");
			// As Hypixel does: a filler holder, the text in its team's prefix and suffix.
			server.runCommand("team add k8shaft");
			server.runCommand("team modify k8shaft prefix \"§710/01/26 §8m000XX \"");
			server.runCommand("team modify k8shaft suffix \"OPAL_1\"");
			server.runCommand("scoreboard players set §s k8shaft 10");
			server.runCommand("team join k8shaft §s");
			command(context, "ksu debug island Glacite Mineshafts");

			int tab = context.computeOnClient(client -> TabReader.parseCount());
			int sidebar = context.computeOnClient(client -> SidebarReader.parseCount());
			context.waitTicks(600);
			check(context.computeOnClient(client -> TabReader.parseCount()) == tab, "no tab parse with every consumer off");
			check(context.computeOnClient(client -> SidebarReader.parseCount()) == sidebar, "no sidebar parse with every consumer off");
			check(context.computeOnClient(client -> MineshaftState.snapshot()).equals(MineshaftSnapshot.NONE), "nothing stored with every consumer off");

			consumer.set(true);
			context.waitTicks(5);
			MineshaftSnapshot shaft = context.computeOnClient(client -> MineshaftState.snapshot());
			check(shaft.getInShaft() && "OPAL_1".equals(shaft.getCode()) && "Opal 1".equals(shaft.getName()),
					"the code from the sidebar, named from the bundled table: " + shaft);
			check(shaft.getCorpses().getPresence() == WidgetPresence.ABSENT, "no Frozen Corpses widget in singleplayer: " + shaft);

			command(context, "ksu debug island Dwarven Mines");
			context.waitTicks(20);
			check(context.computeOnClient(client -> MineshaftState.snapshot()).equals(MineshaftSnapshot.NONE), "leaving the shaft clears it within 1 s");

			command(context, "ksu debug shaft UMBE_1 Umber:NOT_LOOTED Tungsten:LOOTED");
			shaft = context.computeOnClient(client -> MineshaftState.snapshot());
			check("UMBE_1".equals(shaft.getCode()) && shaft.getCorpses().getCorpses().equals(List.of(new Corpse("Umber", false), new Corpse("Tungsten", true))),
					"a simulated shaft: " + shaft);
			context.waitTicks(5);
			check(context.computeOnClient(client -> MineshaftState.snapshot()).getInShaft(), "a simulated shaft stays outside a real one");
			command(context, "ksu debug shaft clear");
			check(context.computeOnClient(client -> MineshaftState.snapshot()).equals(MineshaftSnapshot.NONE), "the simulation is cleared");

			command(context, "ksu debug island clear");
			server.runCommand("scoreboard objectives remove k8shaft");
			server.runCommand("team remove k8shaft");
		} finally {
			consumer.set(false);
			context.runOnClient(client -> wereOn.forEach(module -> module.setEnabled(true)));
		}
	}

	private static void command(ClientGameTestContext context, String command) {
		context.runOnClient(client -> client.player.connection.sendCommand(command));
		context.waitTick();
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
