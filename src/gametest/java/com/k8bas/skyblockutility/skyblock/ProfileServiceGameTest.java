package com.k8bas.skyblockutility.skyblock;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tier C check of the profile service (T3.0d): with no consumer it is not fed; with one, a profile chat line
 * that another mod cancels still identifies the profile (AC-GS-08, second bullet), the id line attaches the
 * id, and a consumer's state is written under config/k8bas_skyblock_utility/profiles/<account>/<profile>/ and
 * read back (REQ-GS-09).
 */
public class ProfileServiceGameTest implements FabricClientGameTest {
	private static final String LINE = "You are playing on profile: Raspberry (Co-op)";
	// Built from numbers: the privacy scan allows no UUID literal.
	private static final String ID = new java.util.UUID(0L, 0xabcdL).toString();

	@Override
	public void runTest(ClientGameTestContext context) {
		AtomicBoolean consumer = new AtomicBoolean(false);
		AtomicBoolean cancel = new AtomicBoolean(false);
		context.runOnClient(client -> {
			ProfileService.subscribe("gametest", consumer::get);
			// Another mod hides the profile line (Skyblocker chat rules can).
			ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !(cancel.get() && message.getString().equals(LINE)));
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();

			server.runCommand("tellraw @a \"" + LINE + "\"");
			context.waitTicks(3);
			check(context.computeOnClient(client -> ProfileService.current()) == null, "not fed without a consumer");

			consumer.set(true);
			cancel.set(true);
			server.runCommand("tellraw @a \"" + LINE + "\"");
			server.runCommand("tellraw @a \"Profile ID: " + ID + "\"");
			context.waitTicks(3);
			Profile profile = context.computeOnClient(client -> ProfileService.current());
			check(profile != null && profile.getKey().equals("raspberry"), "identified from a cancelled chat line: " + profile);
			check(ID.equals(profile.getId()), "the id is attached: " + profile);
			check(profile.getAccount().equals(context.computeOnClient(client -> client.getUser().getProfileId())), "the account is the player's");

			context.runOnClient(client -> ProfileService.write("gametest", "{\"kills\": 3}"));
			check("{\"kills\": 3}".equals(context.computeOnClient(client -> ProfileService.read("gametest"))), "the state reads back");
			Path file = FabricLoader.getInstance().getConfigDir().resolve("k8bas_skyblock_utility/profiles")
					.resolve(profile.getAccount().toString()).resolve("raspberry").resolve("gametest.json");
			check(Files.isRegularFile(file), "the state is in the profile's folder: " + file);
		} finally {
			consumer.set(false);
			cancel.set(false);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
