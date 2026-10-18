package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.skyblock.GameText
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * Maps the family names the widget, the Bestiary menu and chat show to table families (T5.1b, REQ-BEST-16). A name
 * only one family has resolves anywhere, since the widget lists families of other areas too (Bal, a Crystal Hollows
 * family, in the Dwarven Mines). A name that several families share (Bat, Enderman) is decided by the next tier's
 * threshold when the widget shows one, since the widget may still list the End's Endermen on the player's island, and
 * otherwise by the island or the menu area. An unknown or still ambiguous name resolves to null, so the caller shows
 * only the server's values, and is warned about once per name for the resolver's life: one resolver per loaded table,
 * so once per game session.
 */
class FamilyResolver(families: BestiaryFamilies, private val warn: Consumer<String>) {
	private val byName: Map<String, List<BestiaryFamily>> = families.families().groupBy { normalise(it.name) }

	/** Category keys by normalised name: the table's top-level names and the menu's own names for some areas. */
	private val areas: Map<String, String>

	/** Subcategory keys (`<category>/<subcategory>`) by normalised parent name, then by normalised name. */
	private val subareas: Map<String, Map<String, String>>

	private val keys: Set<String> = families.categoryNames().keys
	private val warned: MutableSet<String> = ConcurrentHashMap.newKeySet()

	init {
		val names = families.categoryNames()
		val top = names.filterKeys { '/' !in it }
		areas = top.entries.associate { (key, name) -> normalise(name) to key } +
			MENU_AREAS.filterValues { it in top }.mapKeys { normalise(it.key) }
		subareas = names.filterKeys { '/' in it }.entries
			.groupBy { (key, _) -> normalise(top[key.substringBefore('/')] ?: "") }
			.mapValues { (_, subs) -> subs.associate { (key, name) -> normalise(name) to key } }
	}

	/**
	 * The family [name] means on [island] (an island name from the location tracker; null while unknown), with the next
	 * tier's cumulative threshold [next] when the widget shows one.
	 */
	@JvmOverloads
	fun onIsland(name: String, island: String?, next: Int? = null): BestiaryFamily? = pick(name, islandArea(island), next)

	/**
	 * The family [name] means on the menu page "[parent] ➜ [area]" ("Bestiary ➜ Your Island", "Fishing ➜ Lava"); the
	 * game cuts long titles, so [area] may be the start of the name. A name only one family has resolves to it even on
	 * another area's page, as on the widget: the table may predate a family's move (EC-BEST-08).
	 */
	fun inMenu(name: String, parent: String, area: String): BestiaryFamily? = pick(name, menuArea(parent, area), null)

	/** The bestiary area of [island]: a category key or `<category>/<subcategory>`; null for none or an unknown island. */
	fun islandArea(island: String?): String? {
		island ?: return null
		ISLAND_AREAS[island]?.let { return it.takeIf { key -> key in keys } }
		return areas[normalise(island)]
	}

	/** The bestiary area of the menu page "[parent] ➜ [area]"; null if the page is no Bestiary area page this table knows. */
	fun menuArea(parent: String, area: String): String? {
		val wanted = normalise(parent)
		val names = if (wanted == BESTIARY) areas else subareas[wanted] ?: return null
		return startOf(names, normalise(area))
	}

	/** The family of [name]: [next] decides between several first, then [area]. */
	private fun pick(name: String, area: String?, next: Int?): BestiaryFamily? {
		val key = normalise(name)
		val candidates = byName[key]
		if (candidates == null) {
			once(key) { "Bestiary family name '${GameText.strip(name).trim()}' is not in the table; its values show without a family" }
			return null
		}
		if (candidates.size == 1) {
			return candidates[0]
		}
		// A threshold the table no longer has (a rebalance, REQ-BEST-03) decides nothing.
		val byNext = candidates.filter { next != null && next in it.thresholds }.ifEmpty { candidates }
		if (byNext.size == 1) {
			return byNext[0]
		}
		val inArea = byNext.filter { area != null && (it.category == area || "${it.category}/${it.subcategory}" == area) }
		if (inArea.size == 1) {
			return inArea[0]
		}
		once(key) { "Bestiary family name '${GameText.strip(name).trim()}' is ambiguous (${candidates.joinToString { it.id }}); its values show without a family" }
		return null
	}

	private fun once(key: String, message: () -> String) {
		if (warned.add(key)) {
			warn.accept(message())
		}
	}

	private companion object {
		const val BESTIARY = "bestiary"
		val SPACES = Regex("\\s+")

		/** The Bestiary menu's names where they differ from NEU's (the G1 capture of the menu, 2026-10-01). */
		val MENU_AREAS = mapOf("Your Island" to "dynamic", "The Garden" to "garden", "The Catacombs" to "catacombs")

		/**
		 * Islands whose families are in another area: a Mineshaft's mobs are Dwarven Mines families, and the Workshop's
		 * and the Bayou's are sea creatures. The `jerry` category is the Jerrys of the Season of Jerry, met on any island.
		 */
		val ISLAND_AREAS = mapOf(
			"Glacite Mineshafts" to "mining_3",
			"Jerry" to "fishing/winter",
			"Backwater Bayou" to "fishing/backwater_bayou",
		)

		fun normalise(text: String): String = GameText.strip(text).trim().replace(SPACES, " ").lowercase(Locale.ROOT)

		/** The value of [area] in [names], or of the one name that starts with it (a cut title); null if none or several. */
		fun startOf(names: Map<String, String>, area: String): String? {
			names[area]?.let { return it }
			if (area.isEmpty()) {
				return null
			}
			return names.filterKeys { it.startsWith(area) }.values.singleOrNull()
		}
	}
}
