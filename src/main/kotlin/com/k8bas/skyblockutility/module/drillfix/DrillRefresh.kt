package com.k8bas.skyblockutility.module.drillfix

import java.util.function.Supplier

/**
 * The drill re-equip decision (T3.7, REQ-DRILL-01, -02, -04, -05, -08), without game types, so it is tested as a
 * table (AC-DRILL-01). The first-person hand swaps to the new stack without lowering and raising only for a refreshed
 * copy of the same SkyBlock drill: the same vanilla item, the same component types, the same SkyBlock id and the same
 * non-empty unique id, both carrying drill fuel, and every other component equal. Lore, custom data (such as fuel) and
 * damage may differ; they are not in [Stack.others].
 */
object DrillRefresh {
	/** The custom-data key that makes a SkyBlock item a drill (REQ-DRILL-08). */
	const val FUEL_KEY = "drill_fuel"

	/**
	 * What the decision looks at in a stack.
	 *
	 * @param others every component's value except custom data, lore and damage, by component type id
	 */
	@JvmRecord
	data class Stack(val item: String, val componentTypes: Set<String>, val id: String?, val uuid: String?, val fuel: Boolean, val others: Map<String, Any?>)

	/**
	 * The hand's "replace without the dip" decision: vanilla's [original], or true for a refreshed copy of the same
	 * drill on SkyBlock with the fix on. Never false where [original] is true (REQ-DRILL-04). The stacks are read only
	 * when the gate is open: this runs every client tick for both hands.
	 */
	@JvmStatic
	fun instant(from: Supplier<Stack?>, to: Supplier<Stack?>, onSkyBlock: Boolean, enabled: Boolean, original: Boolean): Boolean {
		if (original || !onSkyBlock || !enabled) {
			return original
		}
		val shown = from.get() ?: return false
		val next = to.get() ?: return false
		return sameDrill(shown, next)
	}

	private fun sameDrill(from: Stack, to: Stack): Boolean = from.fuel && to.fuel && from.item == to.item &&
		from.componentTypes == to.componentTypes && !from.id.isNullOrEmpty() && from.id == to.id && !from.uuid.isNullOrEmpty() &&
		from.uuid == to.uuid && from.others == to.others
}
