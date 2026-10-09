package com.k8bas.skyblockutility.module.mineshaftalert

import com.k8bas.skyblockutility.skyblock.Corpse
import com.k8bas.skyblockutility.skyblock.InventoryCounts
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import java.util.function.Function

/**
 * The mineshaft entry alert's lines (T3.2, REQ-MSA-05, REQ-MSA-06, REQ-MSA-09): the shaft, its corpses in the order
 * Lapis, Tungsten, Umber, Vanguard (a type not known yet after them, by its raw name, EC-MSA-07), and the matching
 * keys in the inventory, e.g.
 * `Opal 1 · 1 Lapis, 1 Tungsten, 1 Umber · Tungsten Key: 2 in inventory · Umber Key: 0 in inventory ⚠`. The
 * chat helper adds the gold `[KSU] ` tag; the body is yellow like the mod's other lines. Pure, so it is tested
 * without a game.
 */
object MineshaftAlertText {
	/**
	 * Whether a key held only in the Dwarven Sack opens a corpse (Q-MSA-04). The G1 check could not be run, so D-7
	 * (inventory only) stands and this stays off until the G3 check (R24). A build constant, not a user toggle.
	 */
	const val SACK_KEYS_WORK = false

	private const val SEPARATOR = " · "
	private val ORDER = listOf("Lapis", "Tungsten", "Umber", "Vanguard")

	/** The key each corpse type needs, by name and SkyBlock id; Lapis needs none. */
	private val KEYS = mapOf("Tungsten" to ("Tungsten Key" to "TUNGSTEN_KEY"), "Umber" to ("Umber Key" to "UMBER_KEY"),
		"Vanguard" to ("Skeleton Key" to "SKELETON_KEY"))

	/** The types whose sack keys would count under the fallback (the Dwarven Sack holds these two). */
	private val SACK_TYPES = setOf("Tungsten", "Umber")

	private val HOVER: Style = Style.EMPTY.withHoverEvent(
		HoverEvent.ShowText(Component.literal("Only your inventory and offhand are counted: keys in sacks or storage are not counted.")))

	/**
	 * The alert for [name] with [corpses]; [showKeys] off leaves out the key part (REQ-MSA-11), [sackFallback] is
	 * [SACK_KEYS_WORK] (a parameter, so both answers are tested).
	 */
	@JvmStatic
	fun alert(name: String, corpses: List<Corpse>, keys: InventoryCounts, showKeys: Boolean, sackFallback: Boolean): Component {
		val line = Component.literal(name).withStyle(ChatFormatting.YELLOW)
		val present = ORDER.filter { type -> corpses.any { it.type == type } }
		val counts = (present + unknownTypes(corpses)).joinToString(", ") { type ->
			val ofType = corpses.filter { it.type == type }
			val looted = ofType.count { it.looted }
			"${ofType.size} $type" + if (looted > 0) " ($looted looted)" else ""
		}
		line.append(SEPARATOR).append(counts)
		if (!showKeys) {
			return line
		}
		val keyed = present.filter { KEYS.containsKey(it) }
		if (keyed.isEmpty()) {
			// A type not known yet may need a key, so "no keys needed" is said only of known types (EC-MSA-07).
			return if (unknownTypes(corpses).isEmpty()) line.append(SEPARATOR).append("no keys needed") else line
		}
		for (type in keyed) {
			val (keyName, keyId) = KEYS.getValue(type)
			val needed = corpses.count { it.type == type && !it.looted }
			val held = keys.count(keyId)
			val entry: MutableComponent = Component.empty().withStyle(HOVER).append("$keyName: ")
			when {
				sackFallback && type in SACK_TYPES -> entry.append("$held in inventory (sack keys also work)")
				held < needed -> entry.append(Component.literal("$held in inventory ⚠").withStyle(ChatFormatting.RED))
				else -> entry.append(Component.literal("$held in inventory").withStyle(ChatFormatting.GREEN))
			}
			line.append(SEPARATOR).append(entry)
		}
		return line
	}

	/** The corpse types not in Lapis, Tungsten, Umber, Vanguard, in listed order (EC-MSA-07); they get no key entry. */
	@JvmStatic
	fun unknownTypes(corpses: List<Corpse>): List<String> = corpses.map { it.type }.distinct().filter { it !in ORDER }

	/** The line when no corpse list came within 8 s (REQ-MSA-08), or the widget is shown with no lines (EC-MSA-06). */
	@JvmStatic
	@JvmOverloads
	fun unavailable(name: String, empty: Boolean = false): Component =
		Component.literal("$name$SEPARATOR" + if (empty) "no corpses listed" else "corpse list unavailable").withStyle(ChatFormatting.YELLOW)

	/** The shared widget hint for the Frozen Corpses widget (REQ-GS-18); this module writes no command of its own. */
	@JvmStatic
	fun hint(widgetHint: Function<String, Component>): Component =
		Component.empty().withStyle(ChatFormatting.YELLOW).append(widgetHint.apply("Frozen Corpses"))
}
