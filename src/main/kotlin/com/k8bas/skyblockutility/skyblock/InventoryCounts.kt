package com.k8bas.skyblockutility.skyblock

/** One of the player's own slots: the SkyBlock id from the item's custom data (null if none) and the stack size. */
@JvmRecord
data class SlotItem(val id: String?, val count: Int) {
	companion object {
		@JvmField
		val EMPTY = SlotItem(null, 0)
	}
}

/**
 * How many of each SkyBlock item the player holds (REQ-GS-07): the 36 main and hotbar slots plus the offhand,
 * nothing else [decided D-7]. Immutable.
 */
data class InventoryCounts(val counts: Map<String, Int>) {
	/** The number held of this SkyBlock id; 0 if none. */
	fun count(id: String): Int = counts[id] ?: 0

	companion object {
		/** The 36 main and hotbar slots plus the offhand. */
		const val SLOTS = 37

		@JvmField
		val EMPTY = InventoryCounts(mapOf())

		/** Sums the stacks of [slots] by SkyBlock id; items without one are not counted. */
		@JvmStatic
		fun of(slots: List<SlotItem>): InventoryCounts {
			require(slots.size == SLOTS) { "Expected the $SLOTS own slots, got ${slots.size}" }
			val counts = LinkedHashMap<String, Int>()
			for (slot in slots) {
				val id = slot.id
				if (!id.isNullOrEmpty() && slot.count > 0) {
					counts.merge(id, slot.count, Int::plus)
				}
			}
			return if (counts.isEmpty()) EMPTY else InventoryCounts(java.util.Map.copyOf(counts))
		}
	}
}
