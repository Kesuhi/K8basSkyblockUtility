package com.k8bas.skyblockutility.skyblock

import net.minecraft.client.Minecraft
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * The tab-widget reader (T3.0a, REQ-GS-02): every listed tab entry's displayed text, grouped into widgets by
 * [TabParser]. It reads the client's own player list on the client thread, the same data vanilla's tab
 * overlay draws, and only while a subscriber is active (REQ-GS-16).
 */
object TabReader {
	private val reader = PassiveReader("tab", TabSnapshot.EMPTY, Supplier { rows() }, Function { parse(it) }, GameStateReaders.CLIENT_THREAD)

	/** Wants the tab widgets while [active] is true (e.g. the consuming feature is enabled). */
	@JvmStatic
	fun subscribe(owner: String, active: BooleanSupplier) = reader.subscribe(owner, active)

	@JvmStatic
	fun snapshot(): TabSnapshot = reader.snapshot()

	@JvmStatic
	fun onChange(listener: Consumer<TabSnapshot>) = reader.onChange(listener)

	@JvmStatic
	fun parseCount(): Int = reader.parseCount()

	internal fun tick() = reader.tick()

	internal fun clear() = reader.clear()

	private fun rows(): List<TabRow>? {
		val connection = Minecraft.getInstance().connection ?: return null
		return connection.listedOnlinePlayers.map { TabRow(it.profile.name(), it.tabListOrder, it.tabListDisplayName?.string ?: "") }
	}

	private fun parse(rows: List<TabRow>): TabSnapshot {
		val snapshot = TabParser.parse(rows)
		snapshot.unknownLines.forEach { ShapeLog.SHARED.once("tab", it) }
		return snapshot
	}
}
