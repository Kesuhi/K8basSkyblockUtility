package com.k8bas.skyblockutility.module.hotspot;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Toggle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.world.InteractionHand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * T3.6b end to end (AC-HOT-11's warning part [B], REQ-HOT-07, REQ-HOT-08): with SkyBlock forced, the player's own rod
 * is cast into water and a hotspot (with its Sea Creature Chance line) is placed where the bobber floats, so the bobber
 * is in its area. When the stands are removed, exactly one local "[KSU] Sea Creature Chance hotspot is gone." appears
 * after the grace period. With "Hotspot gone warning" off, the same removal gives none.
 */
public class HotspotGoneGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String LINE = "[KSU] Sea Creature Chance hotspot is gone.";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("fill -7 -63 2 8 -61 16 minecraft:water");
			server.runCommand("give @p minecraft:fishing_rod");
			context.waitFor(client -> client.player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD), 40);
			context.runOnClient(client -> IslandTracker.forceIsland("Hub"));

			check(fishAndRemove(context, singleplayer, server) == 1, "one warning for the hotspot fished in: " + lines(context));
			set(context, false);
			check(fishAndRemove(context, singleplayer, server) == 0, "none with \"Hotspot gone warning\" off: " + lines(context));
			LOGGER.info("hotspot gone: one local warning while on, none while off");
		} finally {
			context.runOnClient(client -> {
				set(true);
				IslandTracker.forceIsland(null);
			});
		}
	}

	/** Casts, places the hotspot under the bobber, removes it after a second, and counts the warning lines it gave. */
	private static int fishAndRemove(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server) {
		server.runCommand("tp @a 0.5 -60 0.5 0 25");
		singleplayer.getConnection().waitForChunksRender();
		int before = lines(context).size();
		context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
		context.waitFor(client -> client.player.fishing != null && client.player.fishing.isInWater(), 100);
		context.waitTicks(10);
		double[] bobber = context.computeOnClient(client -> new double[] {client.player.fishing.getX(), client.player.fishing.getZ()});
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:armor_stand %.2f -59.0 %.2f {Invisible:1b,NoGravity:1b,Marker:1b,"
				+ "CustomNameVisible:1b,CustomName:{text:\"HOTSPOT\"},Tags:[\"k8hot\"]}", bobber[0], bobber[1]));
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:armor_stand %.2f -59.3 %.2f {Invisible:1b,NoGravity:1b,Marker:1b,"
				+ "CustomNameVisible:1b,CustomName:{text:\"+5α Sea Creature Chance\"},Tags:[\"k8hot\"]}", bobber[0], bobber[1]));
		context.waitFor(client -> module().hotspotsForTest().stream().anyMatch(spot -> spot.getFishedAt() != null), 40);
		context.waitTicks(20);
		server.runCommand("kill @e[type=minecraft:armor_stand,tag=k8hot]");
		context.waitTicks((int) HotspotTracker.GRACE_TICKS + 10);
		context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
		context.waitFor(client -> client.player.fishing == null, 40);
		return lines(context).size() - before;
	}

	private static HotspotModule module() {
		return ModuleManager.modules().stream().filter(HotspotModule.class::isInstance).map(HotspotModule.class::cast).findFirst().orElseThrow();
	}

	private static void set(ClientGameTestContext context, boolean value) {
		context.runOnClient(client -> set(value));
	}

	private static void set(boolean value) {
		((Toggle) OptionCatalog.live().stream().flatMap(card -> card.all().stream()).filter(option -> option.id().equals("fishing_hotspots.warning"))
				.findFirst().orElseThrow()).binding().set(value);
	}

	private static List<String> lines(ClientGameTestContext context) {
		return context.computeOnClient(client -> messages(client).stream().map(message -> message.content().getString())
				.filter(LINE::equals).toList());
	}

	private static List<GuiMessage> messages(Minecraft client) {
		try {
			Field field = ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<GuiMessage> messages = (List<GuiMessage>) field.get(client.gui.hud.getChat());
			return List.copyOf(messages);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read the chat", e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
