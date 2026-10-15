package com.k8bas.skyblockutility.module

import com.google.gson.JsonObject
import com.k8bas.skyblockutility.data.TableContext

/** Reading the NPC and mob table rows (T3.8): a missing or blank field makes the row skip with one warning. */
internal object TableRows {
	fun text(row: JsonObject, name: String): String =
		row.get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("no $name")

	fun optionalText(row: JsonObject, name: String): String? = row.get(name)?.takeIf { !it.isJsonNull }?.let { text(row, name) }

	fun number(row: JsonObject, name: String): Double =
		row.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: throw IllegalArgumentException("no $name")

	fun aliases(row: JsonObject): List<String> = row.getAsJsonArray("aliases")?.map { it.asString } ?: listOf()

	/**
	 * The picker's search text for an entry (REQ-UI-11): its match text and its aliases as words, so a merged
	 * entry is still found by its old name (searching "Bestiary" finds Bramass Beastslayer).
	 */
	fun searchText(matchText: String?, aliases: List<String>): String? =
		(listOfNotNull(matchText) + aliases.map { it.replace('_', ' ') }).joinToString(" ").ifEmpty { null }

	/**
	 * The entry ids the rules already use, for the picker to hide (REQ-UI-11): a rule linked to a merged entry's old
	 * id uses the entry it was merged into; an id the list no longer has stays as it is.
	 */
	@JvmStatic
	fun usedIds(sourceIds: Iterable<String?>, resolve: (String) -> String?): Set<String> =
		java.util.Set.copyOf(sourceIds.filterNotNull().map { resolve(it) ?: it })

	/**
	 * The entries a rule already covers by its name pattern and island, though it is not linked to them (EC-NPCDB-02,
	 * e.g. Trinity, Tomioka and Duncan rules made by hand): the picker counts them as added. [rules] and [entries]
	 * are (pattern, island) pairs, the island as the rule has it (null: every island); [entries] by id.
	 */
	@JvmStatic
	fun coveredByPattern(rules: Iterable<Pair<String?, String?>>, entries: Map<String, Pair<String?, String?>>): Set<String> {
		val covered = rules.filter { !it.first.isNullOrBlank() }.toSet()
		return java.util.Set.copyOf(entries.filter { (_, entry) -> entry.first != null && covered.contains(entry) }.keys)
	}

	/**
	 * Entries by id and by alias (REQ-NPCDB-07(d)): a merged or renamed entry keeps its old id as an alias, so rules
	 * linked to it still find it. An id always wins over an alias; an alias used twice or equal to an id is ignored
	 * with one warning.
	 */
	fun <E : Any> index(table: TableContext, entries: List<Pair<E, List<String>>>, id: (E) -> String): Map<String, E> {
		val byId = LinkedHashMap<String, E>()
		for ((entry, _) in entries) {
			byId.putIfAbsent(id(entry), entry)
		}
		val byAlias = LinkedHashMap<String, E>()
		for ((entry, aliases) in entries) {
			for (alias in aliases) {
				if (byId.containsKey(alias) || byAlias.putIfAbsent(alias, entry) != null) {
					table.warn("alias $alias of ${id(entry)} ignored: the name is already taken")
				}
			}
		}
		byAlias.putAll(byId)
		return java.util.Map.copyOf(byAlias)
	}
}
