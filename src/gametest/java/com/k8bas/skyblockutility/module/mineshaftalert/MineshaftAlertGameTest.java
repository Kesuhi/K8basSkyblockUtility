package com.k8bas.skyblockutility.module.mineshaftalert;

import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Tier C check of the mineshaft entry alert's wiring (T3.2): the location service reports a Glacite Mineshaft (forced),
 * the dev-only shaft simulation gives code OPAL_1 and three corpses, and one Umber Key sits in the hotbar by its
 * SkyBlock id. Once the list has settled, exactly one local `[KSU]` line names the shaft, the corpses and the key
 * shortage, through the real module, mineshaft state and inventory counts; nothing repeats it. Before that, an
 * alert switched off at entry and on inside the shaft posts nothing until the next entry (REQ-GS-16).
 */
public class MineshaftAlertGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String EXPECTED = "[KSU] Opal 1 · 1 Lapis, 2 Umber · Umber Key: 1 in inventory ⚠";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("item replace entity @p hotbar.0 with minecraft:tripwire_hook[minecraft:custom_data={id:\"UMBER_KEY\"}]");
			context.waitFor(client -> client.player.getInventory().getItem(0).getCount() == 1, 40);

			Module module = ModuleManager.modules().stream().filter(m -> m.id().equals(MineshaftAlertModule.ID)).findFirst().orElseThrow();
			context.runOnClient(client -> module.setEnabled(false));
			command(context, "ksu debug island Glacite Mineshafts");
			command(context, "ksu debug shaft OPAL_1 Lapis:NOT_LOOTED Umber:NOT_LOOTED Umber:NOT_LOOTED");
			context.waitTicks(20);
			context.runOnClient(client -> module.setEnabled(true));
			context.waitTicks(60);
			check(alerts(context).isEmpty(), "switched on inside the shaft: nothing until the next entry");
			command(context, "ksu debug shaft clear");
			command(context, "ksu debug island clear");

			command(context, "ksu debug island Glacite Mineshafts");
			command(context, "ksu debug shaft OPAL_1 Lapis:NOT_LOOTED Umber:NOT_LOOTED Umber:NOT_LOOTED");
			context.waitTicks(15);
			check(alerts(context).isEmpty(), "nothing before the list has been stable for 1.5 s");
			context.waitTicks(40);
			List<String> lines = alerts(context);
			check(lines.equals(List.of(EXPECTED)), "one alert with the shaft, corpses and key shortage: " + lines);
			context.waitTicks(60);
			check(alerts(context).size() == 1, "and no second one: " + alerts(context));
			LOGGER.info("mineshaft alert: {}", lines.get(0));
		} finally {
			context.runOnClient(client -> {
				if (client.player != null) {
					client.player.connection.sendCommand("ksu debug shaft clear");
					client.player.connection.sendCommand("ksu debug island clear");
				}
			});
		}
	}

	private static List<String> alerts(ClientGameTestContext context) {
		return context.computeOnClient(client -> messages(client).stream().map(message -> message.content().getString())
				.filter(text -> text.startsWith("[KSU] Opal 1")).toList());
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
