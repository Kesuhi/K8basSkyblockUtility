package com.k8bas.skyblockutility.module.corpsespots

import com.k8bas.skyblockutility.data.BundledData
import com.k8bas.skyblockutility.data.TableHandle
import com.k8bas.skyblockutility.data.TableSpec
import com.k8bas.skyblockutility.data.TableState

/** One possible corpse spot: its number in game (its 1-based place in the code's list) and its block. */
@JvmRecord
data class CorpseSpot(val number: Int, val x: Int, val y: Int, val z: Int)

/** Every shaft code's possible spots, and the known codes no source has spots for. */
class CorpseSpots(private val byCode: Map<String, List<CorpseSpot>>, val noData: Set<String>) {
	/** The spots of [code]; empty for a code without data or one the table does not know. */
	fun spots(code: String?): List<CorpseSpot> = code?.let { byCode[it] } ?: listOf()

	/** The codes with spots. */
	fun codes(): Set<String> = byCode.keys

	fun knows(code: String?): Boolean = code != null && (code in byCode || code in noData)
}

/**
 * The possible corpse spot table (T3.3a, REQ-CORPSE-01, R40): SkyHanni-REPO PR #759, meowdding-repo and ShaftUtils
 * merged into one frame (the block above the corpse's armor stand), bundled and read once through the data registry;
 * nothing is ever fetched (REQ-CORPSE-02). A spot that is missing a coordinate is skipped with one warning.
 */
object CorpseSpotTable {
	const val ID = "mining.corpse_spots"

	@JvmField
	val SPEC: TableSpec<CorpseSpots> = TableSpec(ID, 1) { table ->
		val byCode = LinkedHashMap<String, List<CorpseSpot>>()
		for (row in table.rows(table.body.getAsJsonArray("rows"), { it.get("code")?.asString }) { it }) {
			val code = row.get("code").asString
			byCode[code] = table.rows(code, row.getAsJsonArray("spots"), { null }) { spot ->
				val x = spot.get("x")?.asInt ?: throw IllegalArgumentException("no x")
				val y = spot.get("y")?.asInt ?: throw IllegalArgumentException("no y")
				val z = spot.get("z")?.asInt ?: throw IllegalArgumentException("no z")
				Triple(x, y, z)
			}.mapIndexed { i, (x, y, z) -> CorpseSpot(i + 1, x, y, z) }
		}
		val noData = table.body.getAsJsonArray("noData")?.map { it.asString }?.toSet() ?: setOf()
		CorpseSpots(byCode, noData)
	}

	private val handle: TableHandle<CorpseSpots> by lazy { BundledData.table(SPEC) }

	/** Registers the table; called once from the module, before the registry starts. */
	@JvmStatic
	fun register() {
		handle
	}

	/** The spots, or null while loading or when the table could not be read (EC-CORPSE-09). */
	@JvmStatic
	fun current(): CorpseSpots? = handle.value()

	/** Whether the table failed to load (not while it is still loading). */
	@JvmStatic
	fun unavailable(): Boolean = handle.state() is TableState.Unavailable
}
