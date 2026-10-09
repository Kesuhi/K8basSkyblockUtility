package com.k8bas.skyblockutility.module.mobhighlighter

import com.k8bas.skyblockutility.data.TableSpec
import com.k8bas.skyblockutility.module.TableRows

/** The mob list as loaded: every entry in table order, and a lookup by id that follows aliases. */
class MobList(val entries: List<MobDatabaseEntry>, private val ids: Map<String, MobDatabaseEntry>) {
	/** The entry with this id or alias, or null. */
	fun byId(id: String?): MobDatabaseEntry? = if (id == null) null else ids[id]

	companion object {
		@JvmField
		val EMPTY = MobList(listOf(), mapOf())

		/** A list without aliases, for tests and gametests. */
		@JvmStatic
		fun of(entries: List<MobDatabaseEntry>): MobList {
			val ids = LinkedHashMap<String, MobDatabaseEntry>()
			entries.forEach { ids.putIfAbsent(it.id, it) }
			return MobList(java.util.List.copyOf(entries), ids)
		}
	}
}

/**
 * The bundled mob list, table `npcdb.mobs` (T3.8, REQ-NPCDB-03, REQ-NPCDB-06): the maintainer's CC0 list,
 * converted from the frozen 1.0.x gist (D-12). An entry sits on a tracker island or in an ungated group. The
 * schema checks every field at build time; here a row that still lacks one is skipped with one warning.
 */
object MobTable {
	/**
	 * Groups whose mobs appear on any island, so rules made from them are not gated (REQ-NPCDB-06). None is named
	 * like an island: the hidden Jerries were "Jerry", which is also Jerry's Workshop (T3.8a audit).
	 */
	@JvmField
	val UNGATED_GROUPS: Set<String> = java.util.Set.of("Fishing", "Mythological Creatures", "Spooky Festival", "Hidden Jerries")

	@JvmField
	val SPEC: TableSpec<MobList> = TableSpec("npcdb.mobs", 1) { table ->
		val rows = table.rows(table.body.getAsJsonArray("rows"), { it.get("id")?.asString }) { row ->
			val entry = MobDatabaseEntry()
			entry.id = TableRows.text(row, "id")
			entry.displayName = TableRows.text(row, "displayName")
			entry.island = TableRows.text(row, "island")
			entry.matchText = TableRows.text(row, "matchText")
			entry.subfolder = TableRows.optionalText(row, "subfolder")
			entry to TableRows.aliases(row)
		}
		MobList(java.util.List.copyOf(rows.map { it.first }), TableRows.index(table, rows) { it.id })
	}
}
