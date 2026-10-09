package com.k8bas.skyblockutility.skyblock;

import com.k8bas.skyblockutility.util.ChatUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AC-GS-04 [C] (T3.0g): with one test listener that cancels a profile chat line and another that rewrites an
 * action-bar line, the chat reader's subscriber gets each original text once, with the right overlay flag.
 * Player chat (REQ-GS-05), the mod's own chat lines and a line another mod posts in place of a hidden one
 * never arrive; nothing arrives while unsubscribed.
 */
public class ChatReaderGameTest implements FabricClientGameTest {
	private static final String PROFILE = "You are playing on profile: Raspberry";
	private static final String XP = "+5 SkyBlock XP (Run the race in 120s)";
	private static final String DROP = "RARE DROP! Enchanted Book";

	@Override
	public void runTest(ClientGameTestContext context) {
		AtomicBoolean on = new AtomicBoolean(false);
		List<ServerMessage> heard = new ArrayList<>();
		List<String> shown = new ArrayList<>();
		context.runOnClient(client -> {
			ChatReader.subscribe("gametest", on::get, heard::add);
			// Another mod, in Fabric's default phase: hides the profile line, hides the drop line and shows its
			// own action-bar line instead (as Skyblocker chat rules do), rewrites the XP line.
			ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
				if (on.get() && message.getString().equals(DROP)) {
					client.player.sendOverlayMessage(Component.literal("Rule: a drop!"));
					return false;
				}
				return !(on.get() && message.getString().equals(PROFILE));
			});
			ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
					on.get() && overlay && message.getString().equals(XP) ? Component.literal("rewritten") : message);
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
				if (on.get()) {
					shown.add(message.getString());
				}
			});
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();

			int before = context.computeOnClient(client -> ChatReader.deliveredCount());
			server.runCommand("tellraw @a \"Profile ID: 1234\"");
			context.waitTicks(5);
			check(context.computeOnClient(client -> ChatReader.deliveredCount()) == before, "nothing delivered while unsubscribed");

			on.set(true);
			server.runCommand("tellraw @a \"" + PROFILE + "\"");
			server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(player -> player.sendOverlayMessage(Component.literal(XP))));
			server.runCommand("tellraw @a \"[326] ☀ [MVP+] X: SKYBLOCK LEVEL UP Level 325 ➡ [326]\"");
			context.runOnClient(client -> ChatUtils.chat("Container dump armed for 60 minutes"));
			server.runCommand("tellraw @a \"" + DROP + "\"");
			context.waitTicks(5);

			List<ServerMessage> got = context.computeOnClient(client -> List.copyOf(heard));
			check(got.equals(List.of(new ServerMessage(PROFILE, false), new ServerMessage(XP, true), new ServerMessage(DROP, false))),
					"each original text once, with its overlay flag; no player chat, no own line, no line another mod posted: " + got);
			List<String> displayed = context.computeOnClient(client -> List.copyOf(shown));
			check(!displayed.contains(PROFILE), "the other mod's cancel still applies: " + displayed);
			check(displayed.contains("rewritten") && !displayed.contains(XP), "the other mod's rewrite still applies: " + displayed);
		} finally {
			on.set(false);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
