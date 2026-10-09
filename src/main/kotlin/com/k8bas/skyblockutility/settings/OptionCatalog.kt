package com.k8bas.skyblockutility.settings

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.config.GeneralConfig
import com.k8bas.skyblockutility.module.ModuleManager
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
	fun build(
		general: GeneralConfig,
		mobHighlighter: MobHighlighterConfig,
		npcSearch: NpcSearchConfig,
		setMobHighlighterEnabled: Consumer<Boolean>,
		setNpcSearchEnabled: Consumer<Boolean>,
	): List<Card> = sorted(
		GeneralOptions.cards(general) +
			MobHighlighterOptions.cards(mobHighlighter, general, setMobHighlighterEnabled) +
			NpcSearchOptions.cards(npcSearch, setNpcSearchEnabled),
	)

	/** The catalog of the running game: General plus each registered module's cards. */
	@JvmStatic
	fun live(): List<Card> = sorted(
		GeneralOptions.cards(ConfigManager.general(), Runnable { HudEditorScreen.openEditor(null) }) + ModuleManager.modules().flatMap { it.cards() },
	)

	/** Category order; a stable sort keeps the declaration order inside a category. */
	private fun sorted(cards: List<Card>): List<Card> = java.util.List.copyOf(cards.sortedBy { it.category })
}
