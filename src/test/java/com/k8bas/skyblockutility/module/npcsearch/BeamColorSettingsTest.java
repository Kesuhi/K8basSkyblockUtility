package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.option.SearchIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The beam colour settings (T3.4b, REQ-NPCWP-05, REQ-NPCWP-06, REQ-NPCWP-07): the island swatches in Waypoints
 * and a fixed NPC's own beam colour with "Use island colour". AC-NPCWP-01 (the "Use island colour" row),
 * AC-NPCWP-07 and AC-NPCWP-08 [A]; the in-world parts are the gametest's.
 */
class BeamColorSettingsTest {
	private final NpcSearchConfig config = new NpcSearchConfig();
	private int displayChanges;
	private int ruleChanges;

	private List<Card> cards() {
		return NpcSearchOptions.cards(config, enabled -> { }, () -> displayChanges++);
	}

	private ColorOption swatch(String island) {
		Card card = cards().stream().filter(c -> c.id().equals(NpcSearchOptions.ISLAND_COLORS_CARD)).findFirst().orElseThrow();
		return (ColorOption) card.options().stream().filter(option -> option.text().title().equals(island)).findFirst().orElseThrow();
	}

	private static NpcRule fixed(String label, String island) {
		NpcRule rule = new NpcRule();
		rule.label = label;
		rule.island = island;
		rule.fixed = true;
		return rule;
	}

	private static <T extends Option> T field(RuleGroup group, Class<T> type, String suffix) {
		return type.cast(group.fields().stream().filter(option -> option.id().endsWith(suffix)).findFirst().orElseThrow());
	}

	@Test
	void waypointsHasOneSwatchPerIslandAtItsDefault() {
		List<Card> cards = cards();
		assertEquals(List.of("npc_search", NpcSearchOptions.ISLAND_COLORS_CARD), cards.stream().map(Card::id).toList());
		Card islands = cards.get(1);
		assertEquals(Category.WAYPOINTS, islands.category());
		assertNull(islands.toggle(), "a section: the colours belong to NPC Search's waypoints");
		assertEquals(List.copyOf(WaypointColors.ISLAND_DEFAULTS.keySet()), islands.options().stream().map(option -> option.text().title()).toList());
		for (Option option : islands.options()) {
			ColorOption swatch = (ColorOption) option;
			assertFalse(swatch.storesAlpha());
			assertEquals(WaypointColors.ISLAND_DEFAULTS.get(swatch.text().title()), swatch.binding().get());
			assertEquals(swatch.defaultValue(), swatch.binding().get());
		}
		assertEquals("npc_search.island_color.spider_s_den", swatch("Spider's Den").id());
	}

	@Test
	void anIslandColourIsStoredOnlyWhenItDiffersAndAppliesAtOnce() {
		// AC-NPCWP-07 [A part]: the change reaches the waypoints at once (the module rebuilds them on displayChanged).
		ColorOption hub = swatch("Hub");
		hub.binding().set(0x123456);
		assertEquals(Map.of("Hub", 0x123456), config.islandBeamColors);
		assertEquals(1, displayChanges);
		assertEquals(0x123456, hub.binding().get());
		hub.binding().set(WaypointColors.ISLAND_DEFAULTS.get("Hub"));
		assertTrue(config.islandBeamColors.isEmpty(), "back on the default: nothing stored, so a new default reaches it");
		assertEquals(2, displayChanges);
		NpcWaypointMarkers.Settings settings = NpcSearchModule.waypointSettings(config);
		hub.binding().set(0xFF000000 | 0x00FF00);
		assertEquals(0x00FF00, config.islandBeamColors.get("Hub"), "the swatch is 0xRRGGBB");
		assertFalse(settings.equals(NpcSearchModule.waypointSettings(config)), "the markers see a new colour");
	}

	@Test
	void aFixedRulesBeamColourAndUseIslandColour() {
		NpcRule udel = fixed("Udel", "Crimson Isle");
		config.rules.add(udel);
		NpcRuleCards rules = new NpcRuleCards(config, rule -> null, rule -> "0, 0, 0", () -> ruleChanges++);
		RuleGroup group = rules.groups().get(0);
		ColorOption beam = field(group, ColorOption.class, ".beam_color");
		ActionOption useIsland = field(group, ActionOption.class, ".use_island_color");
		assertEquals("modules.npc_search.rules[].beamColor", beam.storageKey());
		assertFalse(useIsland.destructive());
		int crimson = WaypointColors.ISLAND_DEFAULTS.get("Crimson Isle");
		assertEquals(crimson, beam.binding().get(), "it shows the colour the beam has now");

		beam.binding().set(crimson);
		assertNull(udel.beamColor, "saving the island's colour unchanged keeps following the island");
		assertEquals(0, ruleChanges);

		beam.binding().set(0xFF0000);
		assertEquals(0xFF0000, udel.beamColor);
		assertEquals(1, ruleChanges, "stored and applied at once");
		swatch("Crimson Isle").binding().set(0x00FF00);
		assertEquals(0xFF0000, WaypointColors.beamColor(udel, config.islandBeamColors), "an own colour wins over the island's");

		useIsland.action().run();
		assertNull(udel.beamColor);
		assertEquals(2, ruleChanges);
		assertEquals(0x00FF00, WaypointColors.beamColor(udel, config.islandBeamColors), "AC-NPCWP-01: the island colour again");
		assertEquals(0x00FF00, beam.binding().get());
	}

	@Test
	void beamColoursNeverTouchAGlowColour() {
		// AC-NPCWP-08: moving rules keep their stored colour; their outline is white anyway (R31).
		NpcRule trinity = new NpcRule();
		trinity.label = "Trinity";
		trinity.island = "Catacombs";
		trinity.color = 0x0AA351;
		NpcRule mort = fixed("Mort", "Dungeon Hub");
		config.rules.add(trinity);
		config.rules.add(mort);
		NpcRuleCards rules = new NpcRuleCards(config, rule -> null, rule -> "0, 0, 0", () -> ruleChanges++);
		swatch("Catacombs").binding().set(0x112233);
		swatch("Dungeon Hub").binding().set(0x445566);
		field(rules.groups().get(1), ColorOption.class, ".beam_color").binding().set(0x778899);
		assertEquals(0x0AA351, trinity.color);
		assertNull(trinity.beamColor);
		assertTrue(rules.groups().get(0).fields().stream().noneMatch(option -> option.id().endsWith(".beam_color")), "a moving NPC has no beam");
		assertEquals(0xFFFFFF, NpcSearchModule.toHighlightRule(trinity).color, "its outline stays white");
		// And the other way: a stored glow colour never moves a beam.
		int before = WaypointColors.beamColor(mort, config.islandBeamColors);
		mort.color = 0xABCDEF;
		assertEquals(before, WaypointColors.beamColor(mort, config.islandBeamColors));
	}

	@Test
	void theSwatchesAreFoundBySearch() {
		SearchIndex index = SearchIndex.of(cards());
		assertTrue(index.search("crimson isle").hits().stream().anyMatch(hit -> hit.id().equals("npc_search.island_color.crimson_isle")));
		assertTrue(index.search("beam").hits().size() >= WaypointColors.ISLAND_DEFAULTS.size());
	}
}
