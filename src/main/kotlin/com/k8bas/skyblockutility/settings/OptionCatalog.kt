package com.k8bas.skyblockutility.settings

import com.k8bas.skyblockutility.update.Updates
import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.config.GeneralConfig
import com.k8bas.skyblockutility.module.ModuleManager
import com.k8bas.skyblockutility.module.bestiary.BestiaryConfig
import com.k8bas.skyblockutility.module.bestiary.BestiaryModule
import com.k8bas.skyblockutility.module.bobberfix.BobberFixConfig
import com.k8bas.skyblockutility.module.bobberfix.BobberFixModule
import com.k8bas.skyblockutility.module.corpsespots.CorpseSpotsConfig
import com.k8bas.skyblockutility.module.corpsespots.CorpseSpotsModule
import com.k8bas.skyblockutility.module.hotspot.HotspotConfig
import com.k8bas.skyblockutility.module.hotspot.HotspotModule
import com.k8bas.skyblockutility.module.mineshaftalert.MineshaftAlertConfig
import com.k8bas.skyblockutility.module.mineshaftalert.MineshaftAlertModule
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterOptions
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchOptions
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.screen.HudEditorScreen
import java.util.function.Consumer

/** Every card of the settings screen, in category order: the source of the cards and of the search index (REQ-UI-07). */
object OptionCatalog {
	/** The catalog over the given configs, without any module running (for tests). */
	@JvmStatic
	@JvmOverloads
	fun build(
		general: GeneralConfig,
		mobHighlighter: MobHighlighterConfig,
		npcSearch: NpcSearchConfig,
		setMobHighlighterEnabled: Consumer<Boolean>,
		setNpcSearchEnabled: Consumer<Boolean>,
		mineshaftAlert: MineshaftAlertConfig = MineshaftAlertConfig(),
		setMineshaftAlertEnabled: Consumer<Boolean> = Consumer { mineshaftAlert.enabled = it },
		bobberFix: BobberFixConfig = BobberFixConfig(),
		hotspots: HotspotConfig = HotspotConfig(),
		corpseSpots: CorpseSpotsConfig = CorpseSpotsConfig(),
		bestiary: BestiaryConfig = BestiaryConfig(),
	): List<Card> = sorted(
		GeneralOptions.cards(general) +
			MobHighlighterOptions.cards(mobHighlighter, general, setMobHighlighterEnabled) +
			NpcSearchOptions.cards(npcSearch, setNpcSearchEnabled) +
			MineshaftAlertModule.cards(mineshaftAlert, setMineshaftAlertEnabled) +
			CorpseSpotsModule.cards(corpseSpots, Consumer { corpseSpots.enabled = it }) +
			BobberFixModule.cards(bobberFix, Consumer { bobberFix.enabled = it }) +
			HotspotModule.cards(hotspots, Consumer { hotspots.enabled = it }) +
			BestiaryModule.cards(bestiary, Consumer { bestiary.enabled = it }),
	)

	/** The catalog of the running game: General plus each registered module's cards. */
	@JvmStatic
	fun live(): List<Card> = sorted(
		GeneralOptions.cards(ConfigManager.general(), Runnable { HudEditorScreen.openEditor(null) }, Updates.panel()) +
			ModuleManager.modules().flatMap { it.cards() },
	)

	/** Category order; a stable sort keeps the declaration order inside a category. */
	private fun sorted(cards: List<Card>): List<Card> = java.util.List.copyOf(cards.sortedBy { it.category })
}
