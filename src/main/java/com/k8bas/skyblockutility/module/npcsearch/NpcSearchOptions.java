package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.highlight.GlowBehaviourText;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.option.Toggle;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * NPC Search's declarations (T2.1), in Waypoints (REQ-UI-04). The module and its distance line are
 * AMBER in PLAN §3, so their tooltips state the restriction (REQ-UI-19). Its rules are cards of their own
 * (NpcRuleCards, T2.5a); the beam colours follow in T3.4b.
 */
public final class NpcSearchOptions {
	/** The restriction of the AMBER NPC waypoints, ahead of the glow changes in the card's tooltip. */
	static final String WAYPOINT_RESTRICTION = "Waypoints mark only the fixed NPC positions from your list: their name and distance show "
			+ "through blocks, and terrain hides their beams like a beacon's. Moving NPCs are only outlined while you can see them; "
			+ "blocks hide the outline.";

	private NpcSearchOptions() {
	}

	/** The declarations without a running module (tests, the catalog): the display toggles only store their value. */
	public static List<Card> cards(NpcSearchConfig config, Consumer<Boolean> setEnabled) {
		return cards(config, setEnabled, () -> { });
	}

	/**
	 * @param setEnabled     applies the module switch (NpcSearchModule.setEnabled)
	 * @param displayChanged applies a changed beam, distance or label setting to the waypoints at once (REQ-UI-15)
	 */
	public static List<Card> cards(NpcSearchConfig config, Consumer<Boolean> setEnabled, Runnable displayChanged) {
		return cards(config, setEnabled, displayChanged, List::of);
	}

	/** @param rules the rule cards (NpcRuleCards::groups) */
	public static List<Card> cards(NpcSearchConfig config, Consumer<Boolean> setEnabled, Runnable displayChanged, Supplier<List<RuleGroup>> rules) {
		Toggle enabled = Toggle.of("npc_search.enabled", "modules.npc_search.enabled",
				new OptionText("NPC Search", "Outlines NPCs from your list and marks fixed NPC positions with waypoints.",
						WAYPOINT_RESTRICTION + "\n" + GlowBehaviourText.tooltip(), List.of("npc", "waypoint", "find", "glow", "outline")),
				true, Binding.of(() -> config.enabled, setEnabled)).asAmber();
		List<Option> options = List.of(
				Toggle.of("npc_search.found_title", "modules.npc_search.foundTitleEnabled",
						new OptionText("\"You found\" title", "A title when you first see one of five rare NPCs, once per run.",
								"Shows \"You found <NPC>\" for Trinity, Tomioka, Duncan, Xalx and Pete the first time you have a clear "
										+ "line of sight to them, once per run.",
								List.of("trinity", "tomioka", "duncan", "xalx", "pete", "alert")),
						true, Binding.of(() -> config.foundTitleEnabled, value -> config.foundTitleEnabled = value)),
				Toggle.of("npc_search.show_beams", "modules.npc_search.showBeams",
						new OptionText("Show beacon beams", "A beacon beam rises from each waypoint, in its island's colour.",
								"A beacon beam rises from each waypoint, in its island's colour (green for waypoints shown on every "
										+ "island). Off: only the label.",
								List.of("beam", "beacon")),
						true, Binding.of(() -> config.showBeams, value -> {
							config.showBeams = value;
							displayChanged.run();
						})),
				Toggle.of("npc_search.show_distance", "modules.npc_search.showDistance",
						new OptionText("Show distance", "A second line under each waypoint label with your distance in metres.",
								"A second line under each waypoint label with your distance to it in metres. Only for waypoints at "
										+ "fixed NPC positions, never for moving NPCs.",
								List.of("distance", "metres", "meters")),
						true, Binding.of(() -> config.showDistance, value -> {
							config.showDistance = value;
							displayChanged.run();
						})).asAmber(),
				Toggle.of("npc_search.white_labels", "modules.npc_search.whiteWaypointLabels",
						new OptionText("White waypoint labels", "White labels with a yellow distance line, or each NPC's own colour.",
								"On: a white label with a yellow distance line. Off: both in the NPC's own colour.",
								List.of("label", "colour", "color")),
						true, Binding.of(() -> config.whiteWaypointLabels, value -> {
							config.whiteWaypointLabels = value;
							displayChanged.run();
						})));
		return List.of(new Card("npc_search", Category.WAYPOINTS, "NPC Search", enabled, options, rules));
	}
}
