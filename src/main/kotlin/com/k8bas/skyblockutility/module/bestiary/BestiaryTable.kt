package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.data.BundledData
import com.k8bas.skyblockutility.data.TableHandle
import com.k8bas.skyblockutility.data.TableSpec
import com.k8bas.skyblockutility.data.TableState

/**
 * Where a family stands at [kills] (REQ-BEST-03, REQ-BEST-04): its tier, the next tier's cumulative threshold and the
 * kills to it (both null once maxed), and the kills to the cap.
 */
@JvmRecord
data class TierProgress(val kills: Int, val tier: Int, val maxTier: Int, val nextThreshold: Int?, val toNext: Int?, val toMax: Int) {
	/** The cap is reached: the HUD shows "MAX" and hides "to next" (EC-BEST-02). */
	fun isMax(): Boolean = toMax == 0
}

/** One bestiary family: [thresholds] are its cumulative kills per tier, the last one its cap. */
@JvmRecord
data class BestiaryFamily(
	val id: String,
	val name: String,
	val category: String,
	val subcategory: String?,
	val bracket: String,
	val cap: Int,
	val mobs: List<String>,
	val thresholds: List<Int>,
) {
	fun maxTier(): Int = thresholds.size

	fun progress(kills: Int): TierProgress = BestiaryTiers.progress(thresholds, kills)
}

/** The tier math on a bracket: shared by the table and by families the widget reports with its own threshold. */
object BestiaryTiers {
	/** The tiers of a family with [cap] on [bracket]: the thresholds below the cap, then the cap (REQ-BEST-15, EC-BEST-09). */
	@JvmStatic
	fun thresholds(bracket: List<Int>, cap: Int): List<Int> = java.util.List.copyOf(bracket.filter { it < cap } + cap)

	/** [kills] (below 0 counts as 0) on [thresholds]: the tier is the number of thresholds reached. */
	@JvmStatic
	fun progress(thresholds: List<Int>, kills: Int): TierProgress {
		require(thresholds.isNotEmpty()) { "no thresholds: a family has at least its cap" }
		val count = kills.coerceAtLeast(0)
		val tier = thresholds.count { it <= count }
		val next = thresholds.getOrNull(tier)
		return TierProgress(count, tier, thresholds.size, next, next?.minus(count), (thresholds.last() - count).coerceAtLeast(0))
	}
}

/** Every family by id, and the category names the Bestiary menu titles show. */
class BestiaryFamilies(private val byId: Map<String, BestiaryFamily>, private val categoryNames: Map<String, String>) {
	fun family(id: String): BestiaryFamily? = byId[id]

	/** In table order. */
	fun families(): Collection<BestiaryFamily> = byId.values

	/** The menu name of [category], or of its [subcategory] when given ("Dwarven Mines", "Lava"); null if unknown. */
	fun categoryName(category: String, subcategory: String?): String? =
		categoryNames[if (subcategory == null) category else "$category/$subcategory"]

	/** Every category's name by key, a subcategory's under `<category>/<subcategory>`. */
	fun categoryNames(): Map<String, String> = categoryNames
}

/**
 * The bestiary family table (T5.1, REQ-BEST-15, D-5): NEU-REPO's `constants/bestiary.json` at a pinned commit, trimmed
 * to ids, names, categories, brackets, caps and mob ids, and read once through the data registry; nothing is fetched.
 * bestiary-hud owns it, and the SkyBlock XP tables refer to its families as `bestiary:<id>` instead of bundling a copy.
 */
object BestiaryTable {
	const val ID = "bestiary"

	@JvmField
	val SPEC: TableSpec<BestiaryFamilies> = TableSpec(ID, 1) { table ->
		val brackets = table.body.getAsJsonObject("brackets").entrySet().associate { (id, list) -> id to list.asJsonArray.map { it.asInt } }
		// Subcategories as "<category>/<subcategory>"; no NEU key holds a slash.
		val categoryNames = LinkedHashMap<String, String>()
		for ((id, category) in table.body.getAsJsonObject("categories").entrySet()) {
			val body = category.asJsonObject
			categoryNames[id] = body.get("name").asString
			body.getAsJsonObject("subcategories")?.entrySet()?.forEach { (sub, value) -> categoryNames["$id/$sub"] = value.asJsonObject.get("name").asString }
		}
		val families = table.rows(table.body.getAsJsonArray("rows"), { it.get("id")?.asString }) { row ->
			val bracket = row.get("bracket").asString
			val cap = row.get("cap").asInt
			val thresholds = BestiaryTiers.thresholds(brackets[bracket] ?: throw IllegalArgumentException("bracket $bracket is unknown"), cap)
			// The stored count is the validator's cross-check; a row that disagrees would show wrong tiers, so it is left out.
			val maxTiers = row.get("maxTiers").asInt
			require(maxTiers == thresholds.size) { "maxTiers $maxTiers, but bracket $bracket with cap $cap gives ${thresholds.size}" }
			BestiaryFamily(
				row.get("id").asString, row.get("name").asString, row.get("category").asString, row.get("subcategory")?.asString,
				bracket, cap, java.util.List.copyOf(row.getAsJsonArray("mobs").map { it.asString }), thresholds,
			)
		}
		val byId = LinkedHashMap<String, BestiaryFamily>()
		for (family in families) {
			if (byId.putIfAbsent(family.id, family) != null) {
				table.warn("family ${family.id} listed twice; the first is kept")
			}
		}
		BestiaryFamilies(java.util.Collections.unmodifiableMap(byId), java.util.Map.copyOf(categoryNames))
	}

	private val handle: TableHandle<BestiaryFamilies> by lazy { BundledData.table(SPEC) }

	/** Registers the table; called once from the feature, before or after the registry starts. */
	@JvmStatic
	fun register() {
		handle
	}

	/** The families, or null while loading or when the table could not be read. */
	@JvmStatic
	fun current(): BestiaryFamilies? = handle.value()

	/** Whether the table failed to load (not while it is still loading). */
	@JvmStatic
	fun unavailable(): Boolean = handle.state() is TableState.Unavailable
}
