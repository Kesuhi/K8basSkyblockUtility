package com.k8bas.skyblockutility.skyblock;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AC-GS-07 [C] (T3.0h): the inventory counter counts the player's own 36 main and hotbar slots and the
 * offhand by SkyBlock id; the ender chest and an open container are never counted; a removed key shows within
 * 2 ticks of the client seeing the change; nothing is read while unsubscribed.
 */
public class InventoryReaderGameTest implements FabricClientGameTest {
	private static final String TUNGSTEN = "minecraft:tripwire_hook[minecraft:custom_data={id:\"TUNGSTEN_KEY\"}]";
	private static final String UMBER = "minecraft:tripwire_hook[minecraft:custom_data={id:\"UMBER_KEY\"}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		AtomicBoolean active = new AtomicBoolean(false);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.runOnClient(client -> InventoryReader.subscribe("gametest", active::get));

			server.runCommand("item replace entity @p hotbar.0 with " + TUNGSTEN);
			server.runCommand("item replace entity @p inventory.0 with " + TUNGSTEN);
			server.runCommand("item replace entity @p enderchest.0 with " + UMBER);
			server.runCommand("item replace entity @p hotbar.1 with minecraft:stone 5");
			context.waitFor(client -> client.player.getInventory().getItem(9).getCount() == 1, 40);
			int parses = context.computeOnClient(client -> InventoryReader.parseCount());
			context.waitTicks(10);
			check(context.computeOnClient(client -> InventoryReader.parseCount()) == parses, "nothing read while unsubscribed");
			check(context.computeOnClient(client -> InventoryReader.snapshot()).equals(InventoryCounts.EMPTY), "nothing kept while unsubscribed");

			active.set(true);
			context.waitTicks(2);
			InventoryCounts counts = context.computeOnClient(client -> InventoryReader.snapshot());
			check(counts.count("TUNGSTEN_KEY") == 2, "a key in the hotbar and one in the main inventory: " + counts);
			// The client never receives ender chest contents unless it is opened, so this holds by construction;
			// the open-container case below is the one that could fail.
			check(counts.count("UMBER_KEY") == 0, "the ender chest is never counted: " + counts);
			check(counts.getCounts().keySet().equals(java.util.Set.of("TUNGSTEN_KEY")), "items without a SkyBlock id are not counted: " + counts);

			server.runCommand("item replace entity @p weapon.offhand with " + UMBER);
			context.waitFor(client -> !client.player.getOffhandItem().isEmpty(), 40);
			context.waitTicks(2);
			check(context.computeOnClient(client -> InventoryReader.snapshot()).count("UMBER_KEY") == 1, "the offhand is counted");

			// AC-GS-07: after one key is removed, the count is 1 within 2 ticks.
			server.runCommand("item replace entity @p hotbar.0 with minecraft:air");
			context.waitFor(client -> client.player.getInventory().getItem(0).isEmpty(), 40);
			context.waitTicks(2);
			check(context.computeOnClient(client -> InventoryReader.snapshot()).count("TUNGSTEN_KEY") == 1, "the removed key is gone within 2 ticks");

			// An open container is not the player's inventory: its keys are not counted.
			server.runCommand("setblock 3 -60 3 minecraft:chest");
			server.runCommand("item replace block 3 -60 3 container.0 with " + TUNGSTEN);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				if (s.overworld().getBlockEntity(new BlockPos(3, -60, 3)) instanceof MenuProvider chest) {
					player.openMenu(chest);
				}
			});
			context.waitFor(client -> client.gui.screen() instanceof AbstractContainerScreen<?>, 40);
			context.waitTicks(2);
			check(context.computeOnClient(client -> InventoryReader.snapshot()).count("TUNGSTEN_KEY") == 1, "a key in an open chest is not counted");
			server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).closeContainer());
			server.runCommand("setblock 3 -60 3 minecraft:air");

			active.set(false);
			context.waitTicks(2);
			check(context.computeOnClient(client -> InventoryReader.snapshot()).equals(InventoryCounts.EMPTY), "dropped when the last consumer is off");
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
