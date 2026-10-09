package com.k8bas.skyblockutility.module.odds

/**
 * The five Rare Drop Odds cases of v1 (REQ-ODDS-03, D-3), each behind its own toggle, and the bundled tables it needs.
 * A case whose table could not be read is off, and the card says so (REQ-ODDS-11); the others keep working.
 */
enum class OddsCase(val id: String, val title: String, val description: String, val tables: List<String>) {
	SCATHA("scatha", "Scatha", "A Crystal Hollows panel with your Scatha pet chances and kills.", listOf(OddsTables.SUPPLIED)),
	CROESUS("croesus", "Dungeon reward chests", "How rare each RNG meter item in a reward chest is, in its tooltip.", listOf(OddsTables.RNG_SCORE)),
	VANGUARD("vanguard", "Vanguard corpses", "A mineshaft panel with the rarest Vanguard corpse loot.", listOf(OddsTables.VANGUARD, OddsTables.SUPPLIED)),
	CORPSES("corpses", "Lapis, Umber and Tungsten corpses", "The same panel for the other frozen corpses.", listOf(OddsTables.SUPPLIED)),
	SLAYER("slayer", "Slayer RNG meter", "A panel with your slayer RNG meter item's chance and the bosses left.", listOf(OddsTables.RNG_SCORE)),
}

/** Whether a case runs now (REQ-ODDS-01, -03, -11), without game types. */
object OddsGate {
	/** On: the master toggle, the case's own toggle, and every table it needs read ([readable] is false only for a failed one). */
	@JvmStatic
	fun active(master: Boolean, caseOn: Boolean, case: OddsCase, readable: java.util.function.Predicate<String>): Boolean =
		master && caseOn && case.tables.all { readable.test(it) }

	/** The card's notice for the cases a table failure turned off, or null when every table could be read. */
	@JvmStatic
	fun notice(readable: java.util.function.Predicate<String>): String? {
		val off = OddsCase.entries.filter { case -> case.tables.any { !readable.test(it) } }
		return if (off.isEmpty()) null else "No data, off: " + off.joinToString(", ") { it.title }
	}
}
