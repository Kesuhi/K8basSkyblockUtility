package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.gametest.ModuleStates;
import com.k8bas.skyblockutility.hud.HudRegistry;
import com.k8bas.skyblockutility.hud.HudText;
import com.k8bas.skyblockutility.skyblock.WidgetHint;
import com.k8bas.skyblockutility.ui.screen.HudEditorScreen;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * T5.2, T5.3 [B]: the Bestiary Tracker reads the real chat and menu paths only while it is ON, and its HUD element.
 * <ul>
 *   <li>With "reset on island change", the first island change after switching it ON ends the session, also when the
 *   island was known before the tracker existed (AC-BEST-06).</li>
 *   <li>A BESTIARY block another mod hides still sets the family's tier (EC-BEST-13, the chat reader's cancelled-message
 *   path): a listener in Fabric's default phase cancels the lines, as SkyHanni's compact bestiary messages do.</li>
 *   <li>Switching the tracker off and on forgets a block it had begun to read.</li>
 *   <li>A menu titled "Bestiary" with the G1 milestone item gives milestone 351 with 2 tiers to the next, and nothing
 *   while OFF (the menu subscription with {@link BestiaryMenu#matchesTitle}).</li>
 *   <li>T5.3: the "Bestiary Tracker" element is in the HUD editor with its preview (AC-BEST-01 [C], screenshot
 *   t5.3-bestiary-hud-editor), and with a line hidden, another order and Roman numerals the preview follows them
 *   (AC-BEST-07 [C], t5.3-bestiary-hud-editor-changed); with the tracker ON on an island without a Bestiary widget, the HUD
 *   shows the shared widget hint after 10 s, and none in the Dungeon Hub, while nothing is sent to the server (AC-BEST-11
 *   [C], screenshot t5.3-bestiary-widget-hint).</li>
 * </ul>
 */
public class BestiaryGameTest implements FabricClientGameTest {
	private static final String BORDER = "▬".repeat(64);
	private static final String TIER = "Ghost XIV ➡ XV";
	private static final BlockPos CHEST = new BlockPos(3, -60, 3);

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleStates states = context.computeOnClient(client -> ModuleStates.capture());
		BestiaryModule module = context.computeOnClient(client -> ModuleManager.modules().stream()
				.filter(BestiaryModule.class::isInstance).map(BestiaryModule.class::cast).findFirst().orElseThrow());
		AtomicBoolean hide = new AtomicBoolean(false);
		List<String> shown = new ArrayList<>();
		context.runOnClient(client -> {
			ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !(hide.get() && isBlockLine(message.getString())));
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
				if (hide.get()) {
					shown.add(message.getString());
				}
			});
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.runOnClient(client -> module.setEnabled(false));
			boolean resetWas = context.computeOnClient(client -> module.configForTest().resetOnIslandChange);
			try {
				// The island is known while the tracker is OFF; then it is switched ON with the option, counts, and the player
				// moves to another island.
				context.runOnClient(client -> IslandTracker.forceIsland("Dwarven Mines"));
				context.waitTicks(2);
				context.runOnClient(client -> {
					module.configForTest().resetOnIslandChange = true;
					module.setEnabled(true);
				});
				context.waitFor(client -> module.tracker() != null, 100);
				context.runOnClient(client -> {
					module.tracker().profileChanged();
					module.tracker().widget(List.of(BestiaryWidget.parseLine("Ghost 15: 12,449/12,500")), "Dwarven Mines", 1_000);
					module.tracker().widget(List.of(BestiaryWidget.parseLine("Ghost 15: 12,452/12,500")), "Dwarven Mines", 2_000);
				});
				check(context.computeOnClient(client -> module.tracker().session().kills()) == 3, "three kills counted");
				context.runOnClient(client -> IslandTracker.forceIsland("Hub"));
				context.waitTicks(2);
				check(context.computeOnClient(client -> module.tracker().session().kills()) == 0, "another island ends the session with the option ON");
			} finally {
				context.runOnClient(client -> {
					IslandTracker.forceIsland(null);
					module.configForTest().resetOnIslandChange = resetWas;
					module.setEnabled(false);
				});
			}
			// The family table loads off the client thread at start.
			context.waitFor(client -> module.tracker() != null, 100);
			context.runOnClient(client -> module.tracker().profileChanged());

			send(context, server, BORDER, "BESTIARY", TIER, "REWARDS", BORDER);
			check(context.computeOnClient(client -> module.tracker().family("ghost")) == null, "nothing is read while OFF");

			context.runOnClient(client -> module.setEnabled(true));
			hide.set(true);
			send(context, server, BORDER, "BESTIARY", TIER, "REWARDS", BORDER);
			FamilyView ghost = context.computeOnClient(client -> module.tracker().family("ghost"));
			check(ghost != null && ghost.tier() == 15 && ghost.source() == BestiarySource.CHAT, "the hidden tier-up block is read: " + ghost);
			List<String> displayed = context.computeOnClient(client -> List.copyOf(shown));
			check(!displayed.contains(TIER), "the other mod's hiding still applies: " + displayed);
			hide.set(false);

			// A header read, then the tracker switched off and on: the next line is no value of it.
			context.runOnClient(client -> module.tracker().profileChanged());
			send(context, server, BORDER, "BESTIARY");
			context.runOnClient(client -> {
				module.setEnabled(false);
				module.setEnabled(true);
			});
			send(context, server, TIER);
			check(context.computeOnClient(client -> module.tracker().family("ghost")) == null, "a half-read block is forgotten on a toggle");

			// The menu: a chest titled "Bestiary" with the milestone item of the G1 capture.
			server.runCommand("setblock 3 -60 3 minecraft:chest{CustomName:\"Bestiary\"}");
			server.runCommand("item replace block 3 -60 3 container.0 with minecraft:player_head[minecraft:custom_name=\"Bestiary Milestone CCCLI\","
					+ "minecraft:lore=[\"Total Tiers Unlocked: 3,518\",\"\",\"Progress to Milestone CCCLII: 80%\",\"                          8/10\"]]");
			context.runOnClient(client -> module.setEnabled(false));
			openAndClose(context, server);
			check(context.computeOnClient(client -> module.tracker().milestone()) == null, "no menu is read while OFF");
			context.runOnClient(client -> module.setEnabled(true));
			openAndClose(context, server);
			MilestoneView milestone = context.computeOnClient(client -> module.tracker().milestone());
			check(milestone != null && milestone.milestone() == 351 && Integer.valueOf(2).equals(milestone.tiersToNext())
					&& milestone.source() == BestiarySource.MENU, "the menu's milestone item is read: " + milestone);
			server.runCommand("setblock 3 -60 3 minecraft:air");

			// T5.3: the element and its preview in the HUD editor.
			check(context.computeOnClient(client -> HudRegistry.elements().stream().anyMatch(element -> element.id().equals(BestiaryHud.ID))),
					"the element is registered");
			context.runOnClient(client -> module.tracker().profileChanged());
			context.setScreen(() -> new HudEditorScreen(null, BestiaryHud.ID));
			context.waitForScreen(HudEditorScreen.class);
			context.getInput().setCursorPos(1, 1);
			context.waitTicks(3);
			context.takeScreenshot("t5.3-bestiary-hud-editor");
			context.setScreen(() -> null);
			// AC-BEST-07 [C]: "to next tier" hidden, session first, Roman numerals; the preview follows on the next tick.
			BestiaryConfig settings = module.configForTest();
			boolean toNextWas = settings.lineToNext;
			LineOrder orderWas = settings.lineOrder;
			TierNumerals numeralsWas = settings.numerals;
			try {
				context.runOnClient(client -> {
					settings.lineToNext = false;
					settings.lineOrder = LineOrder.SESSION_FIRST;
					settings.numerals = TierNumerals.ROMAN;
				});
				context.waitTicks(2);
				List<String> preview = context.computeOnClient(client -> ((HudText) BestiaryHud.INSTANCE.preview()).lines());
				check(preview.equals(List.of("§6Ghost §eXV§7/XX", "§7Session: §f+100", "§7Rate: §f1,200/h", "§7Kills: §f12,449",
						"§7Milestone: §fCCLXII §8(7 tiers to next)")), "the preview follows the settings: " + preview);
				context.setScreen(() -> new HudEditorScreen(null, BestiaryHud.ID));
				context.waitForScreen(HudEditorScreen.class);
				context.getInput().setCursorPos(1, 1);
				context.waitTicks(3);
				context.takeScreenshot("t5.3-bestiary-hud-editor-changed");
				context.setScreen(() -> null);
			} finally {
				context.runOnClient(client -> {
					settings.lineToNext = toNextWas;
					settings.lineOrder = orderWas;
					settings.numerals = numeralsWas;
				});
			}

			// AC-BEST-11: no Bestiary section on an island for 10 s shows the shared hint on the HUD; none in the Dungeon Hub.
			List<String> hint = context.computeOnClient(client -> BestiaryHudLines.wrap(WidgetHint.text(BestiaryWidget.HEADER).getString(), 34)
					.stream().map(line -> "§7" + line).toList());
			AtomicBoolean watchSends = new AtomicBoolean(true);
			List<String> sent = new ArrayList<>();
			context.runOnClient(client -> {
				ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
					if (watchSends.get()) {
						sent.add(message);
					}
					return true;
				});
				ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
					if (watchSends.get()) {
						sent.add("/" + command);
					}
					return true;
				});
			});
			try {
				context.runOnClient(client -> IslandTracker.forceIsland("Hub"));
				context.waitTicks(100);
				check(!hintShown(context, hint), "not before 10 s");
				context.waitTicks(130);
				check(hintShown(context, hint), "the hint after 10 s on the Hub: " + context.computeOnClient(client -> BestiaryModule.hudText()));
				context.takeScreenshot("t5.3-bestiary-widget-hint");
				context.runOnClient(client -> IslandTracker.forceIsland("Dungeon Hub"));
				context.waitTicks(230);
				check(!hintShown(context, hint), "no hint in the Dungeon Hub");
				check(context.computeOnClient(client -> List.copyOf(sent)).isEmpty(), "nothing is sent: " + sent);
			} finally {
				watchSends.set(false);
				context.runOnClient(client -> IslandTracker.forceIsland(null));
			}
		} finally {
			hide.set(false);
			context.runOnClient(client -> {
				if (module.tracker() != null) {
					module.tracker().profileChanged();
				}
				states.restore();
			});
		}
	}

	private static boolean hintShown(ClientGameTestContext context, List<String> hint) {
		return context.computeOnClient(client -> {
			HudText text = BestiaryModule.hudText();
			return text != null && text.lines().equals(hint);
		});
	}

	private static boolean isBlockLine(String text) {
		return text.equals(BORDER) || text.equals("BESTIARY") || text.equals(TIER) || text.equals("REWARDS");
	}

	/** Server lines, one chat message each, as the server sends a BESTIARY block. */
	private static void send(ClientGameTestContext context, TestServerContext server, String... lines) {
		for (String line : lines) {
			server.runCommand("tellraw @a \"" + line + "\"");
		}
		context.waitTicks(5);
	}

	/** Opens the chest's menu, lets the reader take a stable snapshot, and closes it on both sides. */
	private static void openAndClose(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			if (player.level().getBlockEntity(CHEST) instanceof MenuProvider provider) {
				player.openMenu(provider);
			}
		});
		context.waitFor(client -> client.gui.screen() instanceof AbstractContainerScreen<?>);
		context.waitTicks(6);
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
