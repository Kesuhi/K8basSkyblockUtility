package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.skyblock.GameText
import com.k8bas.skyblockutility.skyblock.MenuSlot
import com.k8bas.skyblockutility.skyblock.MenuSnapshot
import com.k8bas.skyblockutility.skyblock.SbNumber
import com.k8bas.skyblockutility.skyblock.SbNumbers

/**
 * One family as an area page of the Bestiary menu shows it: [tier] from the item name, [kills] from `Kills:`,
 * [nextTierPercent] from `Progress to Tier XV: 57.1%` (null when maxed or not shown), and the cap from the Overall
 * Progress bar while the player shows it ([cap] null when hidden). [max] is true once the Overall Progress says MAX,
 * false while a progress line shows a next tier, and null when the page does not tell (Overall Progress hidden, EC-BEST-14).
 */
@JvmRecord
data class MenuFamily(val name: String, val tier: Int, val kills: SbNumber?, val nextTierPercent: Double?, val cap: SbNumber?, val max: Boolean?)

/** The Bestiary Milestone item: the [milestone] reached and the family tiers still needed for the next one. */
@JvmRecord
data class MenuMilestone(val milestone: Int, val tiersToNext: Int?, val totalTiers: Int?)

/**
 * One Bestiary menu page: its [parent] (`Bestiary`, `Fishing`, `Critter Safari`) and [area] (empty on the top page),
 * the families it lists and the milestone item when it shows one.
 */
@JvmRecord
data class BestiaryPage(val parent: String, val area: String, val families: List<MenuFamily>, val milestone: MenuMilestone?)

/**
 * The Bestiary menu parser (T5.2b, REQ-BEST-10 source 2): the pages the player opens are a snapshot of every family
 * shown there, and the milestone item gives the global milestone (REQ-BEST-05). It only reads what the menu reader hands
 * it; it never opens /be, clicks or pages (REQ-BEST-13). The leveling menus share the `Fishing ➜` titles, so a page
 * counts as a Bestiary page only by its contents: the Bestiary's own items (Search Bestiary, the milestone item) or a
 * family's lore (`Progress to Tier`, `Deaths:`). The SkyBlock XP Optimizer reads Bestiary pages only through this object
 * and skips them in its own menus by [isBestiaryPage] (T6.5, T6.5b).
 *
 * The top page is captured (G1); the area and family lore are not yet, so their shapes follow the research and stay
 * UNVERIFIED until a capture (REQ-GS-14). A tier is read from the item name, checked against the progress line where
 * there is one, since family names may end in a numeral ("Barbarian Duke X V").
 */
object BestiaryMenu {
	private const val BESTIARY = "Bestiary"

	/** `(1/2) <parent> ➜ <area>`; the game cuts long titles. */
	private val TITLE = Regex("^(?:\\(\\d+/\\d+\\) )?([^➜]+?) ➜ (.+)$")
	private val PARENTS = setOf(BESTIARY, "Fishing", "Critter Safari")
	private val NAME = Regex("^(.+) (\\S+)$")
	/** `Kills: 1,234`, also with words after the number (the game's strings hold "Kills: {n} MAXED OUT!"). */
	private val KILLS = Regex("^Kills: (\\S+)(?: .*)?$")
	private const val LOCKED = "You haven't unlocked this Family yet!"
	private val NEXT_TIER = Regex("^Progress to Tier (\\S+): ([\\d.]+)%$")
	private val OVERALL = Regex("^Overall Progress: ([\\d.]+)%( \\(MAX!\\))?$")
	private val BAR = Regex("^(\\S+)/(\\S+)$")
	private val MILESTONE_NAME = Regex("^Bestiary Milestone (\\S+)$")
	private val TOTAL_TIERS = Regex("^Total Tiers Unlocked: (\\S+)$")
	private val NEXT_MILESTONE = Regex("^Progress to Milestone \\S+: [\\d.]+%$")

	/** Whether a menu with [title] may be a Bestiary page, so the reader reads its contents. */
	@JvmStatic
	fun matchesTitle(title: String): Boolean = titleParts(title) != null

	/** Whether [menu] is a Bestiary page, not one of the leveling menus that share its `Fishing ➜` titles. */
	@JvmStatic
	fun isBestiaryPage(menu: MenuSnapshot): Boolean {
		if (titleParts(menu.title) == null) {
			return false
		}
		return menu.slots.any { slot ->
			val name = text(slot.name)
			name == "Search Bestiary" || MILESTONE_NAME.matches(name) ||
				slot.lore.any { line -> text(line).let { it.startsWith("Deaths: ") || NEXT_TIER.matches(it) } }
		}
	}

	/** The page [menu] shows, or null when it is no Bestiary page. Reads only; never clicks or pages. */
	@JvmStatic
	fun parse(menu: MenuSnapshot): BestiaryPage? {
		if (!isBestiaryPage(menu)) {
			return null
		}
		val (parent, area) = titleParts(menu.title) ?: return null
		val families = menu.slots.sortedBy { it.index }.mapNotNull { family(it) }
		val milestone = menu.slots.firstNotNullOfOrNull { milestone(it) }
		return BestiaryPage(parent, area, families, milestone)
	}

	/** The family [slot] shows, or null when it is none (a locked family, a navigation item). */
	@JvmStatic
	fun family(slot: MenuSlot): MenuFamily? {
		val lore = slot.lore.map { text(it) }
		if (LOCKED in lore) {
			return null
		}
		val kills = lore.firstNotNullOfOrNull { KILLS.matchEntire(it) }?.let { SbNumbers.parse(it.groupValues[1]) } ?: return null
		val next = lore.firstNotNullOfOrNull { NEXT_TIER.matchEntire(it) }
		val nextTier = next?.let { SbNumbers.parseLevel(it.groupValues[1]) }
		val overall = lore.indexOfFirst { OVERALL.matches(it) }
		val overallMax = lore.getOrNull(overall)?.let { OVERALL.matchEntire(it) }?.groups?.get(2) != null
		val max = when {
			overallMax -> true
			next != null -> false
			else -> null
		}
		val cap = lore.getOrNull(overall + 1)?.takeIf { overall >= 0 }?.let { BAR.matchEntire(it) }?.let { SbNumbers.parse(it.groupValues[2]) }
		val (name, tier) = nameAndTier(text(slot.name), nextTier?.minus(1)) ?: return null
		return MenuFamily(name, tier, kills, next?.groupValues?.get(2)?.toDoubleOrNull(), cap, max)
	}

	/** The milestone item [slot] shows, or null when it is none. */
	@JvmStatic
	fun milestone(slot: MenuSlot): MenuMilestone? {
		val match = MILESTONE_NAME.matchEntire(text(slot.name)) ?: return null
		val milestone = SbNumbers.parseLevel(match.groupValues[1]) ?: return null
		val lore = slot.lore.map { text(it) }
		val total = lore.firstNotNullOfOrNull { TOTAL_TIERS.matchEntire(it) }?.let { SbNumbers.parse(it.groupValues[1])?.exact()?.toInt() }
		// The bar under "Progress to Milestone …" counts the tiers since the last milestone, of 10.
		val bar = lore.indexOfFirst { NEXT_MILESTONE.matches(it) }.takeIf { it >= 0 }?.let { lore.getOrNull(it + 1) }?.let { BAR.matchEntire(it) }
		val done = bar?.let { SbNumbers.parse(it.groupValues[1])?.exact() }
		val needed = bar?.let { SbNumbers.parse(it.groupValues[2])?.exact() }
		val toNext = if (done != null && needed != null && needed >= done) (needed - done).toInt() else null
		return MenuMilestone(milestone, toNext, total)
	}

	/** The parent and the area of a Bestiary title (`Bestiary` alone is the top page, area ""); null for other menus. */
	private fun titleParts(title: String): Pair<String, String>? {
		val clean = text(title)
		if (clean == BESTIARY) {
			return BESTIARY to ""
		}
		val match = TITLE.matchEntire(clean) ?: return null
		val parent = match.groupValues[1].trim()
		return if (parent in PARENTS) parent to match.groupValues[2].trim() else null
	}

	/**
	 * The name and tier of a family item: the last word is the tier. With the tier the progress line implies, a name
	 * whose last word is no numeral, or a different one, keeps that word ("Barbarian Duke X" at tier 0).
	 */
	private fun nameAndTier(itemName: String, impliedTier: Int?): Pair<String, Int>? {
		val match = NAME.matchEntire(itemName)
		val last = match?.let { SbNumbers.parseLevel(it.groupValues[2]) }
		return when {
			match != null && last != null && (impliedTier == null || last == impliedTier) -> match.groupValues[1].trim() to last
			impliedTier != null -> itemName to impliedTier
			else -> null
		}
	}

	private fun text(raw: String): String = GameText.strip(raw).trim()
}
