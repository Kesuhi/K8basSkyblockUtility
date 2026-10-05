package com.k8bas.skyblockutility.module.mobhighlighter

import com.k8bas.skyblockutility.config.GeneralConfig
import com.k8bas.skyblockutility.highlight.GlowBehaviourText
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.DatabaseOption
import com.k8bas.skyblockutility.ui.option.IntSlider
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.RuleDatabase
import com.k8bas.skyblockutility.ui.option.RuleGroup
import com.k8bas.skyblockutility.ui.option.Toggle
import java.util.function.Consumer
import java.util.function.Supplier

/** Mob Highlighter's declarations (T2.1), in Highlights with the mob scan range (REQ-UI-04). Its rules are cards of their own (MobRuleCards, T2.5a). */
object MobHighlighterOptions {
	/**
	 * @param setEnabled applies the module switch (MobHighlighterModule.setEnabled)
	 * @param rules      the rule cards (MobRuleCards::groups); none by default
	 * @param database   the Mob Database for "Add from database", or null for none
	 */
	@JvmStatic
	@JvmOverloads
	fun cards(
		config: MobHighlighterConfig,
		general: GeneralConfig,
		setEnabled: Consumer<Boolean>,
		rules: Supplier<List<RuleGroup>> = Supplier { java.util.List.of() },
		database: RuleDatabase? = null,
	): List<Card> {
		val enabled = Toggle.of(
			"mob_highlighter.enabled", "modules.mob_highlighter.enabled",
			OptionText(
				"Mob Highlighter", "Outlines mobs that match your rules while you can see them.", GlowBehaviourText.tooltip(),
				java.util.List.of("glow", "outline", "mob", "highlight"),
			),
			true, Binding.of({ config.enabled }, setEnabled),
		)
		// The setting stays in the general section of the file (REQ-UI-04: it moves on screen only).
		val range = IntSlider.of(
			"mob_highlighter.scan_range", "general.mobScanRangeBlocks",
			OptionText(
				"Mob scan range", "How far around you mobs are checked, in blocks. 0 = unlimited.",
				"Applies to Mob Highlighter and to the moving NPCs of NPC Search.", java.util.List.of("distance", "radius", "blocks"),
			),
			64, 0, 128, 1, "blocks", "Unlimited", Binding.of({ general.mobScanRangeBlocks }, { value -> general.mobScanRangeBlocks = value }),
		)
		val options: List<Option> = if (database == null) {
			java.util.List.of(range)
		} else {
			java.util.List.of(
				range,
				DatabaseOption(
					"mob_highlighter.add_from_database",
					OptionText(
						"Add from database", "Pick a mob from the Mob Database; its rule joins the list below.", "",
						java.util.List.of("add", "database", "mob", "rule", "new"),
					),
					"Add from database", database,
				),
			)
		}
		return java.util.List.of(Card("mob_highlighter", Category.HIGHLIGHTS, "Mob Highlighter", enabled, options, rules))
	}
}
