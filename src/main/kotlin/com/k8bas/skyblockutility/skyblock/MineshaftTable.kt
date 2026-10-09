package com.k8bas.skyblockutility.skyblock

import com.k8bas.skyblockutility.data.TableSpec

/**
 * The bundled `mining_shafts` table (T3.0m, REQ-GS-17): each of the 34 known shaft codes and its display name
 * (`OPAL_1` -> "Opal 1", `RUBY_C` -> "Ruby Crystal", `FAIR_1` -> "Vanguard", `LITT_L` -> "Littlefoot's Den").
 * CC0, built from MiningQOL's CC0 list and cross-checked as facts against four other mods.
 */
object MineshaftTable {
	@JvmField
	val SPEC: TableSpec<Map<String, String>> = TableSpec("mining_shafts", 1) { table ->
		val rows = table.rows(table.body.getAsJsonArray("rows"), { it.get("code").asString }) { row ->
			row.get("code").asString to row.get("name").asString
		}
		java.util.Map.copyOf(rows.toMap())
	}
}
