package com.k8bas.skyblockutility.data;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tier C/B checks of the bundled-data registry (T3.0c): it started at client init without blocking the
 * client thread for more than 200 ms (AC-DATA-07 [B]), logged its tables once (REQ-DATA-11), and
 * `/ksu debug data` lists them (AC-DATA-09 [B]; the index is empty until T3.8 bundles the first tables).
 */
public class BundledDataGameTest implements FabricClientGameTest {
	private static final Pattern STARTED = Pattern.compile("Data registry: (\\d+) table\\(s\\) in the index, read in \\d+ ms off the client thread; "
			+ "the client thread blocked (\\d+) ms");

	@Override
	public void runTest(ClientGameTestContext context) {
		String log = latestLog();
		Matcher started = STARTED.matcher(log);
		check(started.find(), "the registry logged its start");
		check(Integer.parseInt(started.group(2)) <= 200, "the client thread blocked at most 200 ms: " + started.group());
		check(Integer.parseInt(started.group(1)) == context.computeOnClient(client -> BundledData.describe().size()),
				"the log and the listing name the same tables");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> check(ClientCommands.getActiveDispatcher().findNode(List.of("ksu", "debug", "data")) != null,
					"/ksu debug data is registered"));
			List<String> lines = context.computeOnClient(client -> BundledData.describe());
			context.runOnClient(client -> client.player.connection.sendCommand("ksu debug data"));
			context.waitTicks(3);
			String chat = latestLog();
			if (lines.isEmpty()) {
				check(chat.contains("[KSU] No bundled data tables"), "an empty index says so");
			} else {
				for (String line : lines) {
					check(chat.contains("[KSU] " + line), "listed: " + line);
				}
			}
		}
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
