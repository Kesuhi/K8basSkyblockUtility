package com.k8bas.skyblockutility.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * The read-only menu reader (T3.0e, REQ-GS-06): the open container screen's title and top-container slots,
 * read on the client tick while a consumer whose title pattern matches is active. Lore comes from the
 * server-sent `DataComponents.LORE`, never from the tooltip, so it is read without hovering and tooltip mods
 * do not change it. Player-inventory slots are left out. It never clicks, pages, hovers, opens or closes
 * anything, and writes nothing to disk (REQ-GS-12). Snapshots are dropped on a disconnect, server or world
 * change (REQ-GS-15) and on a profile switch (EC-GS-09).
 */
object MenuReader {
	private val tracker = MenuTracker(GameStateReaders.CLIENT_THREAD)
	private var profile: Pair<Any, String>? = null
	private var reads = 0

	/** The custom data each slot had at the last read and the id taken from it, so a tag is copied only on change. */
	private val lastData = ArrayList<CustomData?>()
	private val lastId = ArrayList<String?>()

	/** Hears the menus whose title [title] accepts while [active] is true, on the client thread. */
	@JvmStatic
	fun subscribe(owner: String, active: BooleanSupplier, title: Predicate<String>, listener: Consumer<MenuEvent>) =
		tracker.subscribe(owner, active, title, listener)

	/** The open menu's stable snapshot while a consumer wants it, else null. */
	@JvmStatic
	fun current(): MenuSnapshot? = tracker.current()

	/** How often a menu's contents were read (the gametest checks none while every consumer is off). */
	@JvmStatic
	fun readCount(): Int = reads

	internal fun register() {
		ProfileService.onChange { next ->
			val key = next?.let { Pair<Any, String>(it.account, it.key) }
			if (key != profile) {
				profile = key
				tracker.clear()
			}
		}
	}

	internal fun tick() = tracker.poll { open() }

	internal fun clear() {
		lastData.clear()
		lastId.clear()
		tracker.clear()
	}

	private fun open(): OpenMenu? {
		val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return null
		return OpenMenu(screen, screen.title.string) { read(screen) }
	}

	private fun read(screen: AbstractContainerScreen<*>): MenuSnapshot {
		reads++
		val menu = screen.menu
		val slots = ArrayList<MenuSlot>()
		var size = 0
		for (slot in menu.slots) {
			if (slot.container is Inventory) {
				continue
			}
			val index = size++
			val stack = slot.item
			if (!stack.isEmpty) {
				slots.add(MenuSlot(index, BuiltInRegistries.ITEM.getKey(stack.item).toString(), stack.count, stack.hoverName.string, lore(stack), skyblockId(index, stack)))
			}
		}
		return MenuSnapshot(screen.title.string, menu.stateId, size, java.util.List.copyOf(slots))
	}

	private fun lore(stack: ItemStack): List<String> {
		val lore = stack.get(DataComponents.LORE) ?: return listOf()
		return java.util.List.copyOf(lore.lines().map { it.string })
	}

	/** SkyBlock's own item id from the custom data, or null for items without one. */
	private fun skyblockId(index: Int, stack: ItemStack): String? {
		while (lastData.size <= index) {
			lastData.add(null)
			lastId.add(null)
		}
		val data = stack.get(DataComponents.CUSTOM_DATA)
		if (data !== lastData[index]) {
			lastData[index] = data
			lastId[index] = data?.copyTag()?.getStringOr("id", "")?.ifEmpty { null }
		}
		return lastId[index]
	}
}
