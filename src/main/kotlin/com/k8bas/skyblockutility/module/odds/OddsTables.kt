package com.k8bas.skyblockutility.module.odds

import com.google.gson.JsonObject
import com.k8bas.skyblockutility.data.BundledData
import com.k8bas.skyblockutility.data.TableHandle
import com.k8bas.skyblockutility.data.TableSpec
import com.k8bas.skyblockutility.data.TableState
import com.k8bas.skyblockutility.module.TableRows

/** One RNG meter row (odds.rngscore): the score (dungeons) or slayer XP (slayers) the meter needs for an item. */
@JvmRecord
data class RngScore(val case: String, val group: String, val item: String, val displayName: String, val score: Long, val placeholder: Boolean)

/** One Vanguard loot row (odds.vanguard): the item's weight per roll. */
@JvmRecord
data class LootWeight(val item: String, val displayName: String, val weight: Int)

/** The Vanguard table: its rows, their total weight and the roll counts once a page gives them. */
@JvmRecord
data class VanguardLoot(val rows: List<LootWeight>, val totalWeight: Int, val rolls: IntRange?)

/** One row from a page you supplied (odds.supplied): a base chance per kill or per roll, never verified by itself. */
@JvmRecord
data class SuppliedChance(val case: String, val item: String, val displayName: String, val chance: Double, val rarity: String?,
	val rolls: IntRange?, val verified: Boolean, val source: String)

/**
 * The Rare Drop Odds tables (T3.9g, T3.9h): registered with the bundled-data registry, read once at start. A row that
 * is missing a field is skipped with one warning; a table that cannot be read turns off only the cases that need it
 * (OddsGate, REQ-ODDS-11).
 */
object OddsTables {
	const val RNG_SCORE = "odds.rngscore"
	const val VANGUARD = "odds.vanguard"
	const val SUPPLIED = "odds.supplied"

	@JvmField
	val RNG_SPEC: TableSpec<List<RngScore>> = TableSpec(RNG_SCORE, 1) { table ->
		table.rows(table.body.getAsJsonArray("rows"), { it.get("id")?.asString }) { row ->
			RngScore(
				TableRows.text(row, "case"), TableRows.text(row, "group"), TableRows.text(row, "item"), TableRows.text(row, "displayName"),
				row.get("score")?.asLong ?: throw IllegalArgumentException("no score"), row.get("placeholder")?.asBoolean == true,
			)
		}
	}

	@JvmField
	val VANGUARD_SPEC: TableSpec<VanguardLoot> = TableSpec(VANGUARD, 1) { table ->
		val rows = table.rows(table.body.getAsJsonArray("rows"), { it.get("id")?.asString }) { row ->
			LootWeight(TableRows.text(row, "item"), TableRows.text(row, "displayName"), row.get("weight")?.asInt ?: throw IllegalArgumentException("no weight"))
		}
		VanguardLoot(rows, table.body.get("totalWeight")?.asInt ?: rows.sumOf { it.weight }, rolls(table.body.getAsJsonObject("rolls")))
	}

	@JvmField
	val SUPPLIED_SPEC: TableSpec<List<SuppliedChance>> = TableSpec(SUPPLIED, 1) { table ->
		val sources = table.body.getAsJsonObject("sources")
		table.rows(table.body.getAsJsonArray("rows"), { it.get("id")?.asString }) { row ->
			val refs = row.getAsJsonArray("sourceRefs")?.map { it.asString } ?: listOf()
			SuppliedChance(
				TableRows.text(row, "case"), TableRows.text(row, "item"), TableRows.text(row, "displayName"),
				row.get("chance")?.asDouble ?: throw IllegalArgumentException("no chance"), row.get("rarity")?.asString,
				rolls(row.getAsJsonObject("rolls")), row.get("verified")?.asBoolean == true,
				refs.mapNotNull { sources?.getAsJsonObject(it)?.get("title")?.asString }.joinToString("; "),
			)
		}
	}

	private val rng: TableHandle<List<RngScore>> by lazy { BundledData.table(RNG_SPEC) }
	private val vanguard: TableHandle<VanguardLoot> by lazy { BundledData.table(VANGUARD_SPEC) }
	private val supplied: TableHandle<List<SuppliedChance>> by lazy { BundledData.table(SUPPLIED_SPEC) }

	/** Set by tests: tables treated as unreadable (AC-ODDS-09). */
	@Volatile
	private var unreadableForTest: Set<String> = setOf()

	/** Registers the tables; called once from the module, before the registry starts. */
	@JvmStatic
	fun register() {
		rng
		vanguard
		supplied
	}

	/** False only for a table that could not be read; a table still loading counts as readable (its case just waits). */
	@JvmStatic
	fun readable(table: String): Boolean {
		if (table in unreadableForTest) {
			return false
		}
		return readable(
			when (table) {
				RNG_SCORE -> rng.state()
				VANGUARD -> vanguard.state()
				SUPPLIED -> supplied.state()
				else -> return false
			},
		)
	}

	/** A table that failed to load is unreadable; one still loading is not (its case waits for the data). */
	@JvmStatic
	fun readable(state: TableState<*>): Boolean = state !is TableState.Unavailable

	@JvmStatic
	fun rngScores(): List<RngScore> = rng.value() ?: listOf()

	@JvmStatic
	fun vanguardLoot(): VanguardLoot? = vanguard.value()

	@JvmStatic
	fun supplied(): List<SuppliedChance> = supplied.value() ?: listOf()

	/** For gametests: these tables count as unreadable; an empty set goes back to the registry's states. */
	@JvmStatic
	fun treatAsUnreadableForTest(tables: Set<String>) {
		unreadableForTest = java.util.Set.copyOf(tables)
	}

	private fun rolls(node: JsonObject?): IntRange? {
		val min = node?.get("min")?.asInt ?: return null
		val max = node.get("max")?.asInt ?: return null
		return if (min in 1..max) min..max else null
	}
}
