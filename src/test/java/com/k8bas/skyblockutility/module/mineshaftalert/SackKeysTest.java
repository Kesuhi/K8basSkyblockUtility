package com.k8bas.skyblockutility.module.mineshaftalert;

import com.k8bas.skyblockutility.skyblock.MenuSlot;
import com.k8bas.skyblockutility.skyblock.MenuSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corpse keys in the Dwarven Mines Sack (T3.2b, REQ-MSA-05's sack reminder, R41 (a)), from the maintainer's G3
 * capture: the menu "Dwarven Mines Sack" (36 slots) shows each key as a slot with its SkyBlock id and the lore line
 * "Stored: 1/22.2k"; a key with none stored has no slot. "Moved N <Item> from your Sacks to your inventory." takes
 * some out.
 */
class SackKeysTest {
	private static MenuSlot key(int index, String id, String name, String stored) {
		return new MenuSlot(index, "minecraft:paper", 1, name, List.of("Dwarven Mines Sack", "", "Stored: " + stored, "", "Right-Click for stack!",
				"Click to pickup!"), id);
	}

	private static MenuSnapshot sack(MenuSlot... slots) {
		return new MenuSnapshot("Dwarven Mines Sack", 58, 36, List.of(slots));
	}

	@Test
	void nothingIsKnownBeforeTheSackIsSeen() {
		SackKeys keys = new SackKeys();
		assertNull(keys.amount("TUNGSTEN_KEY"));
		assertNull(keys.amount("UMBER_KEY"));
	}

	@Test
	void theSackMenuGivesBothAmounts() {
		SackKeys keys = new SackKeys();
		assertTrue(keys.menu(sack(key(22, "TUNGSTEN_KEY", "Tungsten Key", "1/22.2k"), key(23, "UMBER_KEY", "Umber Key", "1,204/22.2k"))));
		assertEquals(1, keys.amount("TUNGSTEN_KEY"));
		assertEquals(1204, keys.amount("UMBER_KEY"));
		assertFalse(keys.menu(sack(key(22, "TUNGSTEN_KEY", "Tungsten Key", "1/22.2k"), key(23, "UMBER_KEY", "Umber Key", "1,204/22.2k"))),
				"the same contents again change nothing");
	}

	@Test
	void aKeyWithoutASlotHasNoneStored() {
		// The capture at 14:51:44: the Tungsten Key taken out, its slot gone.
		SackKeys keys = new SackKeys();
		keys.menu(sack(key(23, "UMBER_KEY", "Umber Key", "1/22.2k")));
		assertEquals(0, keys.amount("TUNGSTEN_KEY"));
		assertEquals(1, keys.amount("UMBER_KEY"));
	}

	@Test
	void otherMenusAndEmptyContentsAreIgnored() {
		SackKeys keys = new SackKeys();
		assertFalse(keys.menu(new MenuSnapshot("Mining Sack", 58, 36, List.of(key(22, "TUNGSTEN_KEY", "Tungsten Key", "5/22.2k")))));
		assertFalse(keys.menu(new MenuSnapshot("Dwarven Mines Sack", 0, 36, List.of())), "not loaded yet");
		assertNull(keys.amount("TUNGSTEN_KEY"));
	}

	@Test
	void movingKeysToTheInventoryTakesThemOut() {
		SackKeys keys = new SackKeys();
		assertFalse(keys.chat("Moved 1 Tungsten Key from your Sacks to your inventory."), "unknown amount: nothing to take from");
		keys.menu(sack(key(22, "TUNGSTEN_KEY", "Tungsten Key", "3/22.2k")));
		assertTrue(keys.chat("Moved 2 Tungsten Key from your Sacks to your inventory."));
		assertEquals(1, keys.amount("TUNGSTEN_KEY"));
		assertTrue(keys.chat("Moved 5 Tungsten Key from your Sacks to your inventory."));
		assertEquals(0, keys.amount("TUNGSTEN_KEY"), "never below 0");
		assertFalse(keys.chat("Moved 64 Enchanted Melon from your Sacks to your inventory."));
	}

	@Test
	void theAmountsRoundTripThroughTheProfileStore() {
		SackKeys keys = new SackKeys();
		keys.menu(sack(key(22, "TUNGSTEN_KEY", "Tungsten Key", "2/22.2k")));
		SackKeys read = SackKeys.read(keys.write());
		assertEquals(2, read.amount("TUNGSTEN_KEY"));
		assertEquals(0, read.amount("UMBER_KEY"));
		assertNull(SackKeys.read(null).amount("TUNGSTEN_KEY"));
		assertNull(SackKeys.read("not json").amount("TUNGSTEN_KEY"));
	}
}
