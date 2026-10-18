package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.hud.HudAnchor
import com.k8bas.skyblockutility.hud.HudContent
import com.k8bas.skyblockutility.hud.HudElement
import com.k8bas.skyblockutility.hud.HudPosition
import com.k8bas.skyblockutility.hud.HudText
import com.k8bas.skyblockutility.skyblock.SbNumber

/**
 * The "Bestiary Tracker" HUD element (T5.3, REQ-BEST-01): the selected families' lines, or the widget hint. The module
 * works the lines out each tick, so drawing only reads them (REQ-HUD-11). Moved and scaled 0.5–3.0 in the HUD editor like
 * any element; while no live lines exist the editor shows the preview, drawn with the player's lines, order and numerals.
 */
object BestiaryHud : HudElement {
	const val ID = "bestiary"

	/** At the left edge, halfway down, away from the corpse ding at the top (REQ-HUD-13). */
	private val DEFAULT = HudPosition(HudAnchor.CENTER_LEFT, 4, 0, 1.0)

	/** The preview of a fresh config, until the module has worked out the player's own. */
	private val DEFAULT_PREVIEW = HudText(previewLines(BestiaryLayout.DEFAULT, null))

	/** The preview's data: Ghost at 12,449 kills, 100 of them this session at 1,200/h, milestone 262 with 7 tiers to go. */
	@JvmStatic
	fun previewLines(layout: BestiaryLayout, families: BestiaryFamilies?): List<String> {
		val ghost = families?.family("ghost")
		val toMax = ghost?.let { (it.cap - 12_449).toLong() }
		val view = FamilyView("ghost", "Ghost", ghost, SbNumber(12_449.0, false), 15, 12_500, false, 51, toMax, BestiarySource.WIDGET, 1, 100,
			BestiarySource.WIDGET, 1, 1, 3)
		val rate = RateView(1_200.0, 153_000, toMax?.let { Math.round(it / 1_200.0 * 3_600_000) })
		return BestiaryHudLines.lines(listOf(view), { rate }, SessionView(100, 300_000, 1_200.0), MilestoneView(262, 7, BestiarySource.CHAT, 1), layout, 1)
	}

	override fun id(): String = ID

	override fun displayName(): String = "Bestiary Tracker"

	override fun enabled(): Boolean = BestiaryModule.hudEnabled()

	override fun content(): HudContent? = BestiaryModule.hudText()

	override fun preview(): HudContent = BestiaryModule.previewText() ?: DEFAULT_PREVIEW

	override fun defaultPosition(): HudPosition = DEFAULT
}
