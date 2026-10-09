package com.k8bas.skyblockutility.debug

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.util.ChatUtils
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors

/**
 * `/ksu debug …`: test and capture helpers (REQ-XC-VERIFY-02).
 * - `debug island <name|clear>` forces an island. Dev-only: it is not registered in production
 *   builds (REQ-LOC-08).
 * - `debug dump tab|sidebar|entities` writes what the client currently shows to latest.log,
 *   one `[K8BAS-DUMP]` line each, for parser fixtures (REQ-GS-13). `debug dump containers
 *   on|off` arms the menu capture (ContainerDump). All of them ship in production, only read,
 *   and write nothing unless the player runs them (REQ-GS-12).
 */
object DebugCommand {
	const val DUMP_TAG: String = "[K8BAS-DUMP]"
	private val ROOTS = arrayOf("ksu", "kskyblockutility")

	/** Vanilla's sidebar order (Gui#SCORE_DISPLAY_ORDER is private): score high to low, then owner. */
	private val SIDEBAR_ORDER: Comparator<PlayerScoreEntry> = Comparator.comparingInt<PlayerScoreEntry> { it.value() }.reversed()
		.thenComparing({ it.owner() }, String.CASE_INSENSITIVE_ORDER)
	private const val SIDEBAR_MAX_LINES = 15
	private const val ENTITY_RADIUS = 8.0
	private val LOGGED_TREE = AtomicBoolean()

	@JvmStatic
	fun register() {
		val dev = FabricLoader.getInstance().isDevelopmentEnvironment
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			for (root in ROOTS) {
				// Every prefix runs here and says how to go on: one without a command would go to the server (REQ-UI-25).
				val debug: LiteralArgumentBuilder<FabricClientCommandSource> = ClientCommands.literal("debug")
					.executes { usage(it.source, root, if (dev) "debug <dump|island>" else "debug dump") }
					.then(
						ClientCommands.literal("dump")
							.executes { usage(it.source, root, "debug dump <tab|sidebar|entities|containers>") }
							.then(ClientCommands.literal("tab").executes { report("tab", dumpTab()) })
							.then(ClientCommands.literal("sidebar").executes { report("sidebar", dumpSidebar()) })
							.then(ClientCommands.literal("entities").executes { report("entities", dumpEntities()) })
							.then(
								ClientCommands.literal("containers")
									.executes { usage(it.source, root, "debug dump containers <on|off>") }
									.then(
										ClientCommands.literal("on").executes {
											ContainerDump.arm()
											1
										},
									)
									.then(
										ClientCommands.literal("off").executes {
											ContainerDump.disarm()
											1
										},
									),
							),
					)
				if (dev) {
					debug.then(
						ClientCommands.literal("island")
							.executes { usage(it.source, root, "debug island <name|clear>") }
							.then(
								ClientCommands.argument("name", StringArgumentType.greedyString())
									.suggests { _, builder ->
										builder.suggest("clear")
										IslandTracker.islands().forEach { builder.suggest(it.name) }
										builder.buildFuture()
									}
									.executes { forceIsland(StringArgumentType.getString(it, "name")) },
							),
					)
				}
				dispatcher.register(ClientCommands.literal(root).then(debug))
			}
			// Once per launch, the registered tree, so a production log shows the override is absent (AC-LOC-09).
			if (LOGGED_TREE.compareAndSet(false, true)) {
				val debugNode = dispatcher.root.getChild(ROOTS[0]).getChild("debug")
				K8basSkyblockUtilityClient.LOGGER.info(
					"/{} debug subcommands: {}; dump: {}", ROOTS[0],
					debugNode.children.stream().map { it.name }.sorted().collect(Collectors.joining(", ")),
					debugNode.getChild("dump").children.stream().map { it.name }.sorted().collect(Collectors.joining(", ")),
				)
			}
		}
	}

	/** A local error with how the command goes on; nothing is sent. */
	private fun usage(source: FabricClientCommandSource, root: String, rest: String): Int {
		source.sendError(Component.literal("Usage: /$root $rest"))
		return 0
	}

	private fun forceIsland(name: String): Int {
		val clear = name.equals("clear", ignoreCase = true)
		IslandTracker.forceIsland(if (clear) null else name)
		ChatUtils.chat(if (clear) "Island override cleared" else "Island forced to $name")
		return 1
	}

	private fun report(what: String, lines: List<String>): Int {
		// Real player names never reach the log (decided 2026-10-01).
		NameMasker.SESSION.learnFrom(Minecraft.getInstance())
		for (line in lines) {
			K8basSkyblockUtilityClient.LOGGER.info("{} {} {}", DUMP_TAG, what, NameMasker.SESSION.mask(line))
		}
		ChatUtils.chat("Wrote " + lines.size + " " + what + " lines to latest.log")
		return lines.size
	}

	/**
	 * Every listed tab entry, in the client's list order, with Hypixel's sort key (the profile
	 * name) and the displayed text.
	 */
	@JvmStatic
	fun dumpTab(): List<String> {
		val lines = ArrayList<String>()
		val connection = Minecraft.getInstance().connection ?: return lines
		var index = 0
		for (info in connection.listedOnlinePlayers) {
			val text = info.tabListDisplayName?.string ?: ""
			// The default locale, as Java's String.format(String, ...) used.
			lines.add(java.lang.String.format("%03d order=%d name=%s | %s", index++, info.tabListOrder, info.profile.name(), text))
		}
		return lines
	}

	/**
	 * Every displayed name tag within [ENTITY_RADIUS] blocks, nearest first, with the entity
	 * standing below it and the height difference (fixture for the name-line resolver, T3.1). An
	 * entity whose name is not displayed is never listed, so an invisible body stays out.
	 */
	@JvmStatic
	fun dumpEntities(): List<String> {
		val lines = ArrayList<String>()
		val client = Minecraft.getInstance()
		val level = client.level
		val player = client.player
		if (level == null || player == null) {
			return lines
		}
		val nearby = level.getEntities(player, player.boundingBox.inflate(ENTITY_RADIUS))
		val tags = nearby.stream()
			.filter { it.hasCustomName() && it.isCustomNameVisible }
			.sorted(Comparator.comparingDouble { it.distanceToSqr(player) })
			.toList()
		var index = 0
		for (tag in tags) {
			val below = entityBelow(tag, nearby)
			lines.add(
				String.format(
					Locale.ROOT, "%02d type=%s invisible=%b pos=%.2f,%.2f,%.2f below=%s dy=%s | %s", index++,
					EntityType.getKey(tag.type), tag.isInvisible, tag.x, tag.y, tag.z,
					if (below == null) "-" else EntityType.getKey(below.type),
					if (below == null) "-" else String.format(Locale.ROOT, "%.2f", tag.y - below.y),
					tag.customName!!.string,
				),
			)
		}
		return lines
	}

	/** The highest entity under the tag (within 0.8 blocks sideways) that is not itself a displayed tag. */
	private fun entityBelow(tag: Entity, nearby: List<Entity>): Entity? {
		var best: Entity? = null
		for (other in nearby) {
			if (other === tag || (other.hasCustomName() && other.isCustomNameVisible) || other.y >= tag.y) {
				continue
			}
			val dx = other.x - tag.x
			val dz = other.z - tag.z
			if (dx * dx + dz * dz <= 0.64 && (best == null || other.y > best.y)) {
				best = other
			}
		}
		return best
	}

	/** The sidebar title, then each shown line top to bottom as vanilla draws it. */
	@JvmStatic
	fun dumpSidebar(): List<String> {
		val lines = ArrayList<String>()
		val level = Minecraft.getInstance().level ?: return lines
		val scoreboard = level.scoreboard
		val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return lines
		lines.add("title | " + objective.displayName.string)
		val entries = scoreboard.listPlayerScores(objective).stream()
			.filter { !it.isHidden }
			.sorted(SIDEBAR_ORDER)
			.limit(SIDEBAR_MAX_LINES.toLong())
			.toList()
		var index = 0
		for (entry in entries) {
			val text = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).string
			// The default locale, as Java's String.format(String, ...) used.
			lines.add(java.lang.String.format("%02d score=%d | %s", index++, entry.value(), text))
		}
		return lines
	}
}
