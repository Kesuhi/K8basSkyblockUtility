package com.k8bas.skyblockutility.module.mineshaftalert

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.k8bas.skyblockutility.skyblock.MenuSnapshot

/**
 * The corpse keys in the Dwarven Mines Sack as last seen (T3.2b, REQ-MSA-05's sack reminder, R41 (a)). A key only in
 * the sack does not open a corpse, so these are never counted as held: they only let the alert say "take them out".
 * Read from the sack's own menu when the player opens it (G3 capture: title "Dwarven Mines Sack", each stored key a
 * slot with its SkyBlock id and the lore line "Stored: n/22.2k", no slot when none is stored), and lowered by
 * "Moved N <key> from your Sacks to your inventory.". The "[Sacks]" lines name their items only in a hover, which the
 * chat reader does not pass on (it delivers the text, REQ-GS-04), so they are not read: a key added to the sack shows
 * once the sack is opened again. An amount is null until the menu has been seen. Kept per
 * SkyBlock profile by the module; pure, so it is tested without a game.
 */
class SackKeys {
	private val amounts = HashMap<String, Int>()

	/** The stored amount of [keyId], or null while the sack has not been seen. */
	fun amount(keyId: String): Int? = amounts[keyId]

	/** The Dwarven Mines Sack menu with its contents: every key's amount. True when an amount changed. */
	fun menu(menu: MenuSnapshot): Boolean {
		if (menu.title != TITLE || !menu.hasContent) {
			return false
		}
		val before = HashMap(amounts)
		for (key in KEYS.values) {
			val slot = menu.slots.firstOrNull { it.skyblockId == key }
			amounts[key] = slot?.lore?.firstNotNullOfOrNull { STORED.matchEntire(it.trim()) }?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
		}
		return amounts != before
	}

	/** A chat line; true when it moved keys of a known amount out of the sack. */
	fun chat(line: String): Boolean {
		val match = MOVED.matchEntire(line.trim()) ?: return false
		val key = KEYS[match.groupValues[2]] ?: return false
		val held = amounts[key] ?: return false
		amounts[key] = maxOf(0, held - (match.groupValues[1].replace(",", "").toIntOrNull() ?: 0))
		return true
	}

	/** The profile store's text: `{"TUNGSTEN_KEY":1,"UMBER_KEY":0}`, or `{}` before the sack was seen. */
	fun write(): String {
		val json = JsonObject()
		amounts.toSortedMap().forEach { (key, amount) -> json.addProperty(key, amount) }
		return json.toString()
	}

	companion object {
		const val TITLE = "Dwarven Mines Sack"

		/** The keys the Dwarven Mines Sack holds, by item name (the Skeleton Key fits no sack). */
		private val KEYS = mapOf("Tungsten Key" to "TUNGSTEN_KEY", "Umber Key" to "UMBER_KEY")
		private val STORED = Regex("Stored: ([\\d,]+)/.*")
		private val MOVED = Regex("Moved ([\\d,]+) (.+) from your Sacks to your inventory\\.")

		/** From the profile store's text; an unreadable text is a sack not seen yet. */
		@JvmStatic
		fun read(text: String?): SackKeys {
			val keys = SackKeys()
			val json = try {
				text?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
			} catch (e: RuntimeException) {
				null
			} ?: return keys
			for (key in KEYS.values) {
				json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let { keys.amounts[key] = maxOf(0, it.asInt) }
			}
			return keys
		}
	}
}
