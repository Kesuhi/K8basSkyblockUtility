package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * REQ-GS-07 / AC-GS-07 [A]: counts by SkyBlock id over the player's own 37 slots (36 main and hotbar, the
 * offhand). The ids `TUNGSTEN_KEY` and `UMBER_KEY` are SPEC AC-GS-07's; the G1 container dumps show the same
 * custom-data `id` field (`sbid=`, e.g. `SKELETON_KEY`) but not these keys (UNVERIFIED until the G3 mineshaft
 * check). Which slots are read at all is checked in-game (InventoryReaderGameTest).
 */
class InventoryCountsTest {
	private static List<SlotItem> slots(SlotItem... filled) {
		List<SlotItem> slots = new ArrayList<>(Collections.nCopies(InventoryCounts.SLOTS, SlotItem.EMPTY));
		for (int i = 0; i < filled.length; i++) {
			slots.set(i * 9, filled[i]);
		}
		return slots;
	}

	@Test
	void countsAreSummedByIdAcrossSlotsAndStacks() {
		InventoryCounts counts = InventoryCounts.of(slots(new SlotItem("TUNGSTEN_KEY", 1), new SlotItem("TUNGSTEN_KEY", 1),
				new SlotItem("ENCHANTED_COAL", 64), new SlotItem("ENCHANTED_COAL", 3)));
		assertEquals(2, counts.count("TUNGSTEN_KEY"));
		assertEquals(67, counts.count("ENCHANTED_COAL"));
		assertEquals(0, counts.count("UMBER_KEY"), "an id not held counts 0");
		assertEquals(Map.of("TUNGSTEN_KEY", 2, "ENCHANTED_COAL", 67), counts.getCounts());
	}

	@Test
	void itemsWithoutASkyBlockIdAreNotCounted() {
		InventoryCounts counts = InventoryCounts.of(slots(new SlotItem(null, 5), new SlotItem("", 2), SlotItem.EMPTY));
		assertEquals(InventoryCounts.EMPTY, counts);
	}

	@Test
	void anEmptyInventoryIsTheEmptyCount() {
		assertEquals(InventoryCounts.EMPTY, InventoryCounts.of(slots()));
		assertEquals(0, InventoryCounts.EMPTY.count("TUNGSTEN_KEY"));
	}

	@Test
	void theCountsCannotBeChanged() {
		InventoryCounts counts = InventoryCounts.of(slots(new SlotItem("TUNGSTEN_KEY", 1)));
		assertThrows(UnsupportedOperationException.class, () -> counts.getCounts().put("TUNGSTEN_KEY", 9));
	}

	@Test
	void onlyTheThirtySevenOwnSlotsAreAccepted() {
		// 36 main and hotbar slots plus the offhand; anything else is a reading mistake, not more items.
		assertEquals(37, InventoryCounts.SLOTS);
		assertThrows(IllegalArgumentException.class, () -> InventoryCounts.of(List.of(new SlotItem("TUNGSTEN_KEY", 1))));
	}
}
