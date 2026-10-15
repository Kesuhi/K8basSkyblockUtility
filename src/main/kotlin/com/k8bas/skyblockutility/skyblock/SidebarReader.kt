package com.k8bas.skyblockutility.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * The sidebar reader (T3.0a, REQ-GS-03): the sidebar objective's title and its lines, top to bottom, from
 * the scoreboard data the server sends, chosen and ordered as vanilla's sidebar does (hidden entries
 * skipped, highest score first, at most 15 lines). It reads on the client thread and only while a
 * subscriber is active (REQ-GS-16).
 */
object SidebarReader {
	/** The lines vanilla's sidebar shows at most. */
	const val MAX_LINES = 15

	/** Vanilla's sidebar order: highest score first, then the holder's name, ignoring case. */
	private val ORDER = compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }

	private val reader = PassiveReader("sidebar", SidebarSnapshot.EMPTY, Supplier { raw() }, Function { SidebarParser.parse(it) },
		GameStateReaders.CLIENT_THREAD)

	/** Wants the sidebar while [active] is true (e.g. the consuming feature is enabled). */
	@JvmStatic
	fun subscribe(owner: String, active: BooleanSupplier) = reader.subscribe(owner, active)

	@JvmStatic
	fun snapshot(): SidebarSnapshot = reader.snapshot()

	@JvmStatic
	fun onChange(listener: Consumer<SidebarSnapshot>) = reader.onChange(listener)

	@JvmStatic
	fun parseCount(): Int = reader.parseCount()

	internal fun tick() = reader.tick()

	internal fun clear() = reader.clear()

	private fun raw(): SidebarRaw? {
		val level = Minecraft.getInstance().level ?: return null
		val scoreboard = level.scoreboard
		val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return null
		val lines = scoreboard.listPlayerScores(objective)
			.filter { !it.isHidden }
			.sortedWith(ORDER)
			.take(MAX_LINES)
			.map { entry ->
				val team = scoreboard.getPlayersTeam(entry.owner())
				SidebarLine(team?.playerPrefix?.string ?: "", entry.ownerName().string, team?.playerSuffix?.string ?: "")
			}
		return SidebarRaw(objective.displayName.string, java.util.List.copyOf(lines))
	}
}
