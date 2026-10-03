package com.k8bas.skyblockutility.debug;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.ui.screen.ConfigScreen;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * `/ksu debug …`: test and capture helpers (REQ-XC-VERIFY-02).
 * <ul>
 *   <li>`debug island <name|clear>` forces an island. Dev-only: it is not registered in production
 *       builds (REQ-LOC-08).</li>
 *   <li>`debug newui` opens the new settings screen while it is built (T2.4a). Dev-only too.</li>
 *   <li>`debug dump tab|sidebar|entities` writes what the client currently shows to latest.log,
 *       one `[K8BAS-DUMP]` line each, for parser fixtures (REQ-GS-13). `debug dump containers
 *       on|off` arms the menu capture (ContainerDump). All of them ship in production, only read,
 *       and write nothing unless the player runs them (REQ-GS-12).</li>
 * </ul>
 */
public final class DebugCommand {
	public static final String DUMP_TAG = "[K8BAS-DUMP]";
	private static final String[] ROOTS = {"ksu", "kskyblockutility"};
	/** Vanilla's sidebar order (Gui#SCORE_DISPLAY_ORDER is private): score high to low, then owner. */
	private static final Comparator<PlayerScoreEntry> SIDEBAR_ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final int SIDEBAR_MAX_LINES = 15;
	private static final double ENTITY_RADIUS = 8;
	private static final AtomicBoolean LOGGED_TREE = new AtomicBoolean();

	private DebugCommand() {
	}

	public static void register() {
		boolean dev = FabricLoader.getInstance().isDevelopmentEnvironment();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (String root : ROOTS) {
				LiteralArgumentBuilder<FabricClientCommandSource> debug = ClientCommands.literal("debug")
						.then(ClientCommands.literal("dump")
								.then(ClientCommands.literal("tab").executes(context -> report("tab", dumpTab())))
								.then(ClientCommands.literal("sidebar").executes(context -> report("sidebar", dumpSidebar())))
								.then(ClientCommands.literal("entities").executes(context -> report("entities", dumpEntities())))
								.then(ClientCommands.literal("containers")
										.then(ClientCommands.literal("on").executes(context -> {
											ContainerDump.arm();
											return 1;
										}))
										.then(ClientCommands.literal("off").executes(context -> {
											ContainerDump.disarm();
											return 1;
										}))));
				if (dev) {
					debug.then(ClientCommands.literal("island")
							.then(ClientCommands.argument("name", StringArgumentType.greedyString())
									.suggests((context, builder) -> {
										builder.suggest("clear");
										IslandTracker.islands().forEach(island -> builder.suggest(island.name()));
										return builder.buildFuture();
									})
									.executes(context -> forceIsland(StringArgumentType.getString(context, "name")))));
					// The new settings screen, until it replaces the Cloth one (T2.5b).
					debug.then(ClientCommands.literal("newui").executes(context -> {
						var client = context.getSource().getClient();
						// Next tick, so the chat's own Enter does not reach the new screen.
						client.execute(() -> client.gui.setScreen(new ConfigScreen(client.gui.screen())));
						return 1;
					}));
				}
				dispatcher.register(ClientCommands.literal(root).then(debug));
			}
			// Once per launch, the registered tree, so a production log shows the override is absent (AC-LOC-09).
			if (LOGGED_TREE.compareAndSet(false, true)) {
				var debugNode = dispatcher.getRoot().getChild(ROOTS[0]).getChild("debug");
				K8basSkyblockUtilityClient.LOGGER.info("/{} debug subcommands: {}; dump: {}", ROOTS[0],
						debugNode.getChildren().stream().map(node -> node.getName()).sorted().collect(Collectors.joining(", ")),
						debugNode.getChild("dump").getChildren().stream().map(node -> node.getName()).sorted().collect(Collectors.joining(", ")));
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
		// Real player names never reach the log (decided 2026-10-01).
		NameMasker.SESSION.learnFrom(Minecraft.getInstance());
		for (String line : lines) {
			K8basSkyblockUtilityClient.LOGGER.info("{} {} {}", DUMP_TAG, what, NameMasker.SESSION.mask(line));
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

	/** Every displayed name tag within {@link #ENTITY_RADIUS} blocks, nearest first, with the entity
	 *  standing below it and the height difference (fixture for the name-line resolver, T3.1). An
	 *  entity whose name is not displayed is never listed, so an invisible body stays out. */
	public static List<String> dumpEntities() {
		List<String> lines = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return lines;
		}
		Player player = client.player;
		List<Entity> nearby = client.level.getEntities(player, player.getBoundingBox().inflate(ENTITY_RADIUS));
		List<Entity> tags = nearby.stream()
				.filter(entity -> entity.hasCustomName() && entity.isCustomNameVisible())
				.sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
				.toList();
		int index = 0;
		for (Entity tag : tags) {
			Entity below = entityBelow(tag, nearby);
			lines.add(String.format(Locale.ROOT, "%02d type=%s invisible=%b pos=%.2f,%.2f,%.2f below=%s dy=%s | %s", index++,
					EntityType.getKey(tag.getType()), tag.isInvisible(), tag.getX(), tag.getY(), tag.getZ(),
					below == null ? "-" : EntityType.getKey(below.getType()),
					below == null ? "-" : String.format(Locale.ROOT, "%.2f", tag.getY() - below.getY()),
					tag.getCustomName().getString()));
		}
		return lines;
	}

	/** The highest entity under the tag (within 0.8 blocks sideways) that is not itself a displayed tag. */
	private static Entity entityBelow(Entity tag, List<Entity> nearby) {
		Entity best = null;
		for (Entity other : nearby) {
			if (other == tag || (other.hasCustomName() && other.isCustomNameVisible()) || other.getY() >= tag.getY()) {
				continue;
			}
			double dx = other.getX() - tag.getX();
			double dz = other.getZ() - tag.getZ();
			if (dx * dx + dz * dz <= 0.64 && (best == null || other.getY() > best.getY())) {
				best = other;
			}
		}
		return best;
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
