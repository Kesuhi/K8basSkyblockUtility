package com.k8bas.skyblockutility.skyblock;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tier C check of the menu reader (T3.0e, AC-GS-06): with its consumer off it reads nothing; with it on, an
 * opened chest titled like a SkyBlock menu gives one snapshot with the title, the 27 chest slots only (the
 * player's inventory below is left out), the name, the SkyBlock id and the server lore, unchanged while a
 * test tooltip modifier rewrites the tooltip; a slot update gives a new snapshot; close is signalled; a
 * menu with another title gives nothing. Opening happens on the server, as on Hypixel.
 */
public class MenuReaderGameTest implements FabricClientGameTest {
	private static final BlockPos BESTIARY = new BlockPos(1, -60, 2);
	private static final BlockPos OTHER = new BlockPos(-1, -60, 2);

	@Override
	public void runTest(ClientGameTestContext context) {
		AtomicBoolean active = new AtomicBoolean(false);
		AtomicBoolean rewrite = new AtomicBoolean(false);
		List<MenuEvent> events = new CopyOnWriteArrayList<>();
		context.runOnClient(client -> {
			MenuReader.subscribe("gametest", active::get, "Bestiary"::equals, events::add);
			// A tooltip mod rewriting a lore line; the reader must not see it (REQ-GS-06).
			ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> {
				if (rewrite.get()) {
					lines.replaceAll(line -> line.getString().equals("Line one") ? Component.literal("Rewritten") : line);
				}
			});
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("setblock 1 -60 2 minecraft:chest{CustomName:\"Bestiary\"}");
			server.runCommand("setblock -1 -60 2 minecraft:chest{CustomName:\"Ender Stash\"}");
			server.runCommand("item replace block 1 -60 2 container.0 with minecraft:diamond[minecraft:custom_name=\"Test Gem\","
					+ "minecraft:lore=[\"Line one\",\"Line two\"],minecraft:custom_data={id:\"TEST_GEM\"}]");
			server.runCommand("item replace block -1 -60 2 container.0 with minecraft:emerald");
			// In the player's inventory, below the chest slots on the screen.
			server.runCommand("item replace entity @p inventory.0 with minecraft:stone 3");

			int reads = context.computeOnClient(client -> MenuReader.readCount());
			open(context, server, BESTIARY);
			context.waitTicks(6);
			check(context.computeOnClient(client -> MenuReader.readCount()) == reads, "nothing read with the consumer off");
			check(events.isEmpty() && context.computeOnClient(client -> MenuReader.current()) == null, "nothing shown with the consumer off");
			close(context, server);

			active.set(true);
			rewrite.set(true);
			open(context, server, BESTIARY);
			context.waitTicks(6);
			check(events.size() == 1 && events.get(0) instanceof MenuEvent.Shown, "one snapshot per stable state: " + events);
			MenuSnapshot menu = ((MenuEvent.Shown) events.get(0)).getMenu();
			check(menu.getTitle().equals("Bestiary"), "the title: " + menu.getTitle());
			check(menu.getSize() == 27, "only the chest's own slots: " + menu.getSize());
			check(menu.getSlots().size() == 1, "the player's stone is not part of it: " + menu.getSlots());
			MenuSlot gem = menu.slot(0);
			check(gem != null && gem.getName().equals("Test Gem") && "TEST_GEM".equals(gem.getSkyblockId())
					&& gem.getItem().equals("minecraft:diamond"), "name, SkyBlock id and item: " + gem);
			check(gem.getLore().equals(List.of("Line one", "Line two")), "the server lore, not the rewritten tooltip: " + gem.getLore());
			List<String> tooltip = context.computeOnClient(client -> {
				ItemStack stack = ((AbstractContainerScreen<?>) client.gui.screen()).getMenu().getSlot(0).getItem();
				return stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)
						.stream().map(Component::getString).toList();
			});
			check(tooltip.contains("Rewritten") && !tooltip.contains("Line one"), "the tooltip modifier is active: " + tooltip);

			// A slot update while the menu is open, sent like a server's slot packet.
			server.runOnServer(minecraftServer -> {
				ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
				player.containerMenu.getSlot(1).set(new ItemStack(Items.EMERALD));
				player.containerMenu.broadcastChanges();
			});
			context.waitTicks(6);
			check(events.size() == 2 && events.get(1) instanceof MenuEvent.Shown shown && shown.getMenu().slot(1) != null,
					"a slot update is a new snapshot: " + events);
			close(context, server);
			context.waitTicks(2);
			check(events.size() == 3 && events.get(2) == MenuEvent.Closed.INSTANCE, "close is signalled: " + events);

			open(context, server, OTHER);
			context.waitTicks(6);
			close(context, server);
			check(events.size() == 3, "a menu with another title gives nothing: " + events);

			server.runCommand("setblock 1 -60 2 minecraft:air");
			server.runCommand("setblock -1 -60 2 minecraft:air");
			server.runCommand("item replace entity @p inventory.0 with minecraft:air");
		} finally {
			active.set(false);
			rewrite.set(false);
		}
	}

	private static void open(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		server.runOnServer(minecraftServer -> {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			if (player.level().getBlockEntity(pos) instanceof MenuProvider provider) {
				player.openMenu(provider);
			}
		});
		context.waitFor(client -> client.gui.screen() instanceof AbstractContainerScreen<?>);
	}

	/** Closes on the client and waits until the server has closed it too: a close packet still on its way would
	 *  close the next menu the server opens. */
	private static void close(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(client -> client.player.closeContainer());
		context.waitFor(client -> client.gui.screen() == null);
		for (int i = 0; i < 20 && !server.computeOnServer(minecraftServer -> {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			return player.containerMenu == player.inventoryMenu;
		}); i++) {
			context.waitTicks(1);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
