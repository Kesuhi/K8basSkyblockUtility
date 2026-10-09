package com.k8bas.skyblockutility.debug

import com.k8bas.skyblockutility.skyblock.MenuSnapshot
import java.util.function.UnaryOperator

/**
 * The lines the armed container dump writes for one menu snapshot (T0.4b format, kept by T3.0e):
 * `[K8BAS-DUMP] container <n> title=<title> slots=<size> state=<id>`, then per non-empty slot
 * `… slot <i> item=<id> sbid=<SkyBlock id or -> count=<n> | <name>` and its `… slot <i> lore <j> | <line>`.
 * [mask] hides real player names in the title, the names and the lore; item and SkyBlock ids stay.
 */
object ContainerDumpFormat {
	@JvmStatic
	fun lines(capture: Int, menu: MenuSnapshot, mask: UnaryOperator<String>): List<String> {
		val prefix = "${DebugCommand.DUMP_TAG} container $capture"
		val lines = ArrayList<String>()
		lines.add("$prefix title=${mask.apply(menu.title)} slots=${menu.size} state=${menu.stateId}")
		for (slot in menu.slots) {
			lines.add("$prefix slot ${slot.index} item=${slot.item} sbid=${slot.skyblockId ?: "-"} count=${slot.count} | ${mask.apply(slot.name)}")
			slot.lore.forEachIndexed { i, line -> lines.add("$prefix slot ${slot.index} lore $i | ${mask.apply(line)}") }
		}
		return lines
	}
}
