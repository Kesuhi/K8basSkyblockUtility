package com.k8bas.skyblockutility.debug;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.util.ChatUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * `/ksu debug …`: test and capture helpers (REQ-XC-VERIFY-02).
 * <ul>
 *   <li>`debug island <name|clear>` forces an island. Dev-only: it is not registered in production
 *       builds (REQ-LOC-08).</li>
 *   <li>`debug dump tab|sidebar` writes what the client currently shows to latest.log, one
 *       `[K8BAS-DUMP]` line each, for parser fixtures (REQ-GS-13). It ships in production, reads
 *       only, and writes nothing unless the player runs it (REQ-GS-12).</li>
 * </ul>
 */
public final class DebugCommand {
	public static final String DUMP_TAG = "[K8BAS-DUMP]";
	private static final String[] ROOTS = {"ksu", "kskyblockutility"};
	/** Vanilla's sidebar order (Gui#SCORE_DISPLAY_ORDER is private): score high to low, then owner. */
	private static final Comparator<PlayerScoreEntry> SIDEBAR_ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final int SIDEBAR_MAX_LINES = 15;

	private DebugCommand() {
	}

	public static void register() {
		boolean dev = FabricLoader.getInstance().isDevelopmentEnvironment();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (String root : ROOTS) {
				LiteralArgumentBuilder<FabricClientCommandSource> debug = ClientCommands.literal("debug")
						.then(ClientCommands.literal("dump")
								.then(ClientCommands.literal("tab").executes(context -> report("tab", dumpTab())))
								.then(ClientCommands.literal("sidebar").executes(context -> report("sidebar", dumpSidebar()))));
				if (dev) {
					debug.then(ClientCommands.literal("island")
							.then(ClientCommands.argument("name", StringArgumentType.greedyString())
									.suggests((context, builder) -> {
										builder.suggest("clear");
										IslandTracker.knownIslands().forEach(builder::suggest);
										return builder.buildFuture();
									})
									.executes(context -> forceIsland(StringArgumentType.getString(context, "name")))));
				}
				dispatcher.register(ClientCommands.literal(root).then(debug));
			}
		});
	}

	private static int forceIsland(String name) {
		boolean clear = name.equalsIgnoreCase("clear");
		IslandTracker.forceIsland(clear ? null : name);
		ChatUtils.chat(clear ? "Island override cleared" : "Island forced to " + name);
		return 1;
	}

	private static int report(String what, List<String> lines) {
		for (String line : lines) {
			K8basSkyblockUtilityClient.LOGGER.info("{} {} {}", DUMP_TAG, what, line);
		}
		ChatUtils.chat("Wrote " + lines.size() + " " + what + " lines to latest.log");
		return lines.size();
	}

	/** Every listed tab entry, in the client's list order, with Hypixel's sort key (the profile
	 *  name) and the displayed text. */
	public static List<String> dumpTab() {
		List<String> lines = new ArrayList<>();
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return lines;
		}
		int index = 0;
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			String text = info.getTabListDisplayName() != null ? info.getTabListDisplayName().getString() : "";
			lines.add(String.format("%03d order=%d name=%s | %s", index++, info.getTabListOrder(), info.getProfile().name(), text));
		}
		return lines;
	}

	/** The sidebar title, then each shown line top to bottom as vanilla draws it. */
	public static List<String> dumpSidebar() {
		List<String> lines = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return lines;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			return lines;
		}
		lines.add("title | " + objective.getDisplayName().getString());
		List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
				.filter(entry -> !entry.isHidden())
				.sorted(SIDEBAR_ORDER)
				.limit(SIDEBAR_MAX_LINES)
				.toList();
		int index = 0;
		for (PlayerScoreEntry entry : entries) {
			String text = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString();
			lines.add(String.format("%02d score=%d | %s", index++, entry.value(), text));
		}
		return lines;
	}
}
