package com.k8bas.skyblockutility.module.npcsearch

import com.k8bas.skyblockutility.highlight.GlowBehaviourText
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.ColorOption
import com.k8bas.skyblockutility.ui.option.DatabaseOption
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.RuleDatabase
import com.k8bas.skyblockutility.ui.option.RuleGroup
import com.k8bas.skyblockutility.ui.option.Toggle
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * NPC Search's declarations (T2.1), in Waypoints (REQ-UI-04). The module and its distance line are
 * AMBER in PLAN §3, so their tooltips state the restriction (REQ-UI-19). Its rules are cards of their own
 * (NpcRuleCards, T2.5a). The island beam colours are a section card after it (T3.4b, REQ-NPCWP-06).
 */
object NpcSearchOptions {
	/** The restriction of the AMBER NPC waypoints, ahead of the glow changes in the card's tooltip. */
	const val WAYPOINT_RESTRICTION: String = "Waypoints mark only the fixed NPC positions from your list: their name and distance show " +
		"through blocks, and terrain hides their beams like a beacon's. Moving NPCs are only outlined while you can see them; " +
		"blocks hide the outline."

	/**
	 * Without a running module (tests, the catalog) only [config] and [setEnabled] are given: the display
	 * toggles then only store their value.
	 *
	 * @param setEnabled     applies the module switch (NpcSearchModule.setEnabled)
	 * @param displayChanged applies a changed beam, distance or colour setting to the waypoints at once (REQ-UI-15)
	 * @param rules          the rule cards (NpcRuleCards::groups); none by default
	 * @param database       the NPC Database for "Add from database", or null for none
	 */
	@JvmStatic
	@JvmOverloads
	fun cards(
		config: NpcSearchConfig,
		setEnabled: Consumer<Boolean>,
		displayChanged: Runnable = Runnable { },
		rules: Supplier<List<RuleGroup>> = Supplier { java.util.List.of() },
		database: RuleDatabase? = null,
	): List<Card> {
		val enabled = Toggle.of(
			"npc_search.enabled", "modules.npc_search.enabled",
			OptionText(
				"NPC Search", "Outlines NPCs from your list and marks fixed NPC positions with waypoints.",
				WAYPOINT_RESTRICTION + "\n" + GlowBehaviourText.tooltip(), java.util.List.of("npc", "waypoint", "find", "glow", "outline"),
			),
			true, Binding.of({ config.enabled }, setEnabled),
		).asAmber()
		val options: List<Option> = java.util.List.of(
			Toggle.of(
				"npc_search.found_title", "modules.npc_search.foundTitleEnabled",
				OptionText(
					"\"You found\" title", "A title when you first see one of five rare NPCs, once per run.",
					"Shows \"You found <NPC>\" for Trinity, Tomioka, Duncan, Xalx and Pete the first time you have a clear " +
						"line of sight to them, once per run.",
					java.util.List.of("trinity", "tomioka", "duncan", "xalx", "pete", "alert"),
				),
				true, Binding.of({ config.foundTitleEnabled }, { value -> config.foundTitleEnabled = value }),
			),
			Toggle.of(
				"npc_search.show_beams", "modules.npc_search.showBeams",
				OptionText(
					"Show beacon beams", "A beacon beam rises from each waypoint, in its island's colour.",
					"A beacon beam rises from each waypoint, in the NPC's own beam colour if you gave it one, otherwise in " +
						"its island's colour (green for waypoints shown on every island). Off: only the label.",
					java.util.List.of("beam", "beacon"),
				),
				true,
				Binding.of({ config.showBeams }, { value ->
					config.showBeams = value
					displayChanged.run()
				}),
			),
			Toggle.of(
				"npc_search.show_distance", "modules.npc_search.showDistance",
				OptionText(
					"Show distance", "A second line under each waypoint label with your distance in metres.",
					"A second line under each waypoint label with your distance to it in metres. Only for waypoints at " +
						"fixed NPC positions, never for moving NPCs.",
					java.util.List.of("distance", "metres", "meters"),
				),
				true,
				Binding.of({ config.showDistance }, { value ->
					config.showDistance = value
					displayChanged.run()
				}),
			).asAmber(),
		)
		val all = ArrayList<Option>(options)
		if (database != null) {
			all.add(
				DatabaseOption(
					"npc_search.add_from_database",
					OptionText(
						"Add from database", "Pick an NPC from the NPC Database; its rule joins the list below.", "",
						java.util.List.of("add", "database", "npc", "rule", "new"),
					),
					"Add from database", database,
				),
			)
		}
		return java.util.List.of(Card("npc_search", Category.WAYPOINTS, "NPC Search", enabled, all, rules), islandColors(config, displayChanged))
	}

	/** The id of the island beam colour section. */
	const val ISLAND_COLORS_CARD: String = "npc_search.island_colors"

	/**
	 * One swatch per island (REQ-NPCWP-05, REQ-NPCWP-06): picker and hex input, starting at the documented default.
	 * Only a colour that differs from the default is stored, so a later change of the default reaches everyone who
	 * kept it.
	 */
	private fun islandColors(config: NpcSearchConfig, displayChanged: Runnable): Card {
		val swatches = WaypointColors.ISLAND_DEFAULTS.map { (island, default) ->
			ColorOption.of(
				"npc_search.island_color." + island.lowercase().replace(Regex("[^a-z0-9]+"), "_"),
				"modules.npc_search.islandBeamColors.$island",
				OptionText(
					island, "The beam colour of waypoints on $island.",
					"Waypoints on $island use this colour unless the NPC has its own beam colour.",
					java.util.List.of("beam", "colour", "color", "island"),
				),
				default, false,
				Binding.of({ WaypointColors.islandColor(island, config.islandBeamColors) }, { value ->
					val rgb = value and 0xFFFFFF
					if (rgb == default) config.islandBeamColors.remove(island) else config.islandBeamColors[island] = rgb
					displayChanged.run()
				}),
			)
		}
		return Card(ISLAND_COLORS_CARD, Category.WAYPOINTS, "Island beam colours", null, swatches)
	}
}
