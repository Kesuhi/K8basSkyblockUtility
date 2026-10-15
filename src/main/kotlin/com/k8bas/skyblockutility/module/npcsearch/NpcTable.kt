package com.k8bas.skyblockutility.module.npcsearch

import com.k8bas.skyblockutility.data.TableSpec
import com.k8bas.skyblockutility.location.Islands
import com.k8bas.skyblockutility.module.TableRows

/** The NPC list as loaded: every entry in table order, and a lookup by id that follows aliases. */
class NpcList(val entries: List<NpcDatabaseEntry>, private val ids: Map<String, NpcDatabaseEntry>) {
	/** The entry with this id or alias, or null. */
	fun byId(id: String?): NpcDatabaseEntry? = if (id == null) null else ids[id]

	companion object {
		@JvmField
		val EMPTY = NpcList(listOf(), mapOf())

		/** A list without aliases, for tests and gametests. */
		@JvmStatic
		fun of(entries: List<NpcDatabaseEntry>): NpcList {
			val ids = LinkedHashMap<String, NpcDatabaseEntry>()
			entries.forEach { ids.putIfAbsent(it.id, it) }
			return NpcList(java.util.List.copyOf(entries), ids)
		}
	}
}

/**
 * The bundled NPC list, table `npcdb.npcs` (T3.8, REQ-NPCDB-03, REQ-NPCDB-06): built from NEU-REPO (MIT) and
 * converted from the frozen 1.0.x gist (D-12). The schema checks every field at build time; here a row that
 * still lacks one is skipped with one warning. A fixed NPC filed under "Catacombs" stands in the Dungeon Hub
 * and is read as "Dungeon Hub" (REQ-LOC-03); a moving one keeps "Catacombs" (D-2).
 */
object NpcTable {
	@JvmField
	val SPEC: TableSpec<NpcList> = TableSpec("npcdb.npcs", 1) { table ->
		val rows = table.rows(table.body.getAsJsonArray("rows"), { it.get("id")?.asString }) { row ->
			val entry = NpcDatabaseEntry()
			entry.id = TableRows.text(row, "id")
			entry.displayName = TableRows.text(row, "displayName")
			entry.island = TableRows.text(row, "island")
			entry.fixed = row.get("fixed")?.asBoolean ?: throw IllegalArgumentException("no fixed")
			if (entry.fixed) {
				entry.x = TableRows.number(row, "x")
				entry.y = TableRows.number(row, "y")
				entry.z = TableRows.number(row, "z")
				if (entry.island == Islands.CATACOMBS) {
					entry.island = Islands.DUNGEON_HUB
				}
			} else {
				entry.matchText = TableRows.text(row, "matchText")
			}
			entry.aliases = java.util.List.copyOf(TableRows.aliases(row))
			entry to entry.aliases
		}
		NpcList(java.util.List.copyOf(rows.map { it.first }), TableRows.index(table, rows) { it.id })
	}
}
