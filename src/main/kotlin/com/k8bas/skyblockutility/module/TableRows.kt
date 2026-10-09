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
