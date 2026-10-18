package com.k8bas.skyblockutility.module.corpsespots

import com.k8bas.skyblockutility.hud.HudAnchor
import com.k8bas.skyblockutility.hud.HudContent
import com.k8bas.skyblockutility.hud.HudElement
import com.k8bas.skyblockutility.hud.HudPosition
import com.k8bas.skyblockutility.hud.HudText

/**
 * The "Corpse ding" HUD element (T3.3d, REQ-CORPSE-11, R43 (b)): while the Organ Donor dings in a Glacite Mineshaft,
 * "Corpse ding: far|close|very close" from the last ding's pitch; nothing 2 s after the last one, nor once every corpse
 * is looted (R57). The module works the line out each tick, so drawing only reads it (REQ-HUD-11). Moved and scaled in
 * the HUD editor like any element.
 */
object CorpseDingHud : HudElement {
	const val ID = "corpse_ding"

	/** Top centre, below the boss bar. */
	private val DEFAULT = HudPosition(HudAnchor.TOP_CENTER, 0, 40, 1.0)
	private val PREVIEW = HudText(listOf(DingHint.hudLine(DingHint.Warmth.VERY_CLOSE)))

	override fun id(): String = ID

	override fun displayName(): String = "Corpse ding"

	override fun enabled(): Boolean = CorpseSpotsModule.dingHudEnabled()

	override fun content(): HudContent? = CorpseSpotsModule.dingHudText()

	override fun preview(): HudContent = PREVIEW

	override fun defaultPosition(): HudPosition = DEFAULT
}
