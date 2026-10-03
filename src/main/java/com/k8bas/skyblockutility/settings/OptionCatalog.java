package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterOptions;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchOptions;
import com.k8bas.skyblockutility.ui.option.Card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Every card of the settings screen, in category order: the source of the cards and of the search index (REQ-UI-07). */
public final class OptionCatalog {
	private OptionCatalog() {
	}

	/** The catalog over the given configs, without any module running (for tests). */
	public static List<Card> build(GeneralConfig general, MobHighlighterConfig mobHighlighter, NpcSearchConfig npcSearch,
			Consumer<Boolean> setMobHighlighterEnabled, Consumer<Boolean> setNpcSearchEnabled) {
		List<Card> cards = new ArrayList<>(GeneralOptions.cards(general));
		cards.addAll(MobHighlighterOptions.cards(mobHighlighter, general, setMobHighlighterEnabled));
		cards.addAll(NpcSearchOptions.cards(npcSearch, setNpcSearchEnabled));
		return sorted(cards);
	}

	/** The catalog of the running game: General plus each registered module's cards. */
	public static List<Card> live() {
		List<Card> cards = new ArrayList<>(GeneralOptions.cards(ConfigManager.general()));
		for (Module module : ModuleManager.modules()) {
			cards.addAll(module.cards());
		}
		return sorted(cards);
	}

	/** Category order; a stable sort keeps the declaration order inside a category. */
	private static List<Card> sorted(List<Card> cards) {
		List<Card> sorted = new ArrayList<>(cards);
		sorted.sort(Comparator.comparing(Card::category));
		return List.copyOf(sorted);
	}
}
