package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.highlight.GlowBehaviourText;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.DatabaseOption;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.option.Toggle;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Mob Highlighter's declarations (T2.1), in Highlights with the mob scan range (REQ-UI-04). Its rules are cards of their own (MobRuleCards, T2.5a). */
public final class MobHighlighterOptions {
	private MobHighlighterOptions() {
	}

	/**
	 * @param setEnabled applies the module switch (MobHighlighterModule.setEnabled)
	 */
	public static List<Card> cards(MobHighlighterConfig config, GeneralConfig general, Consumer<Boolean> setEnabled) {
		return cards(config, general, setEnabled, List::of);
	}

	public static List<Card> cards(MobHighlighterConfig config, GeneralConfig general, Consumer<Boolean> setEnabled, Supplier<List<RuleGroup>> rules) {
		return cards(config, general, setEnabled, rules, null);
	}

	/**
	 * @param rules    the rule cards (MobRuleCards::groups)
	 * @param database the Mob Database for "Add from database", or null for none
	 */
	public static List<Card> cards(MobHighlighterConfig config, GeneralConfig general, Consumer<Boolean> setEnabled, Supplier<List<RuleGroup>> rules,
			RuleDatabase database) {
		Toggle enabled = Toggle.of("mob_highlighter.enabled", "modules.mob_highlighter.enabled",
				new OptionText("Mob Highlighter", "Outlines mobs that match your rules while you can see them.", GlowBehaviourText.tooltip(),
						List.of("glow", "outline", "mob", "highlight")),
				true, Binding.of(() -> config.enabled, setEnabled));
		// The setting stays in the general section of the file (REQ-UI-04: it moves on screen only).
		IntSlider range = IntSlider.of("mob_highlighter.scan_range", "general.mobScanRangeBlocks",
				new OptionText("Mob scan range", "How far around you mobs are checked, in blocks. 0 = unlimited.",
						"Applies to Mob Highlighter and to the moving NPCs of NPC Search.", List.of("distance", "radius", "blocks")),
				64, 0, 128, 1, "blocks", "Unlimited", Binding.of(() -> general.mobScanRangeBlocks, value -> general.mobScanRangeBlocks = value));
		List<Option> options = database == null ? List.of(range) : List.of(range, new DatabaseOption("mob_highlighter.add_from_database",
				new OptionText("Add from database", "Pick a mob from the Mob Database; its rule joins the list below.", "",
						List.of("add", "database", "mob", "rule", "new")), "Add from database", database));
		return List.of(new Card("mob_highlighter", Category.HIGHLIGHTS, "Mob Highlighter", enabled, options, rules));
	}
}
