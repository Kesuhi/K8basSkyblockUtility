package com.k8bas.skyblockutility.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * The inventory counter (T3.0h, REQ-GS-07): items by SkyBlock id across the player's own 36 main and hotbar
 * slots and the offhand, read each client tick while a subscriber is active, so a change shows within a tick
 * or two. Only those slots are read: sacks, the ender chest, backpacks, storage, an open container and the
 * cursor never are [decided D-7]; an item held on the cursor counts again once it is put down (EC-GS-12).
 */
object InventoryReader {
	private const val MAIN_SLOTS = 36

	/** The custom data each slot had at the last read and the id taken from it, so the tag is copied only on change. */
	private val lastData = arrayOfNulls<CustomData>(InventoryCounts.SLOTS)
	private val lastId = arrayOfNulls<String>(InventoryCounts.SLOTS)

	private val reader = PassiveReader("inventory", InventoryCounts.EMPTY, Supplier { slots() }, Function { InventoryCounts.of(it) },
		GameStateReaders.CLIENT_THREAD)

	/** Wants the counts while [active] is true (e.g. the consuming feature is enabled). */
	@JvmStatic
	fun subscribe(owner: String, active: BooleanSupplier) = reader.subscribe(owner, active)

	@JvmStatic
	fun snapshot(): InventoryCounts = reader.snapshot()

	@JvmStatic
	fun onChange(listener: Consumer<InventoryCounts>) = reader.onChange(listener)

	@JvmStatic
	fun parseCount(): Int = reader.parseCount()

	internal fun tick() = reader.tick()

	internal fun clear() = reader.clear()

	private fun slots(): List<SlotItem>? {
		val player = Minecraft.getInstance().player ?: return null
		val inventory = player.inventory
		val slots = ArrayList<SlotItem>(InventoryCounts.SLOTS)
		for (i in 0 until MAIN_SLOTS) {
			slots.add(slot(i, inventory.getItem(i)))
		}
		slots.add(slot(MAIN_SLOTS, player.offhandItem))
		return java.util.List.copyOf(slots)
	}

	private fun slot(index: Int, stack: ItemStack): SlotItem {
		if (stack.isEmpty) {
			return SlotItem.EMPTY
		}
		val data = stack.get(DataComponents.CUSTOM_DATA)
		if (data !== lastData[index]) {
			lastData[index] = data
			lastId[index] = data?.copyTag()?.getStringOr("id", "")?.ifEmpty { null }
		}
		return SlotItem(lastId[index], stack.count)
	}
}
