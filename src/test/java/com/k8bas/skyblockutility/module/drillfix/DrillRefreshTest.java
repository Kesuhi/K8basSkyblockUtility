package com.k8bas.skyblockutility.module.drillfix;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drill re-equip decision (T3.7): AC-DRILL-01 [A], REQ-DRILL-08, EC-DRILL-01/05/07. */
class DrillRefreshTest {
	private static final Set<String> TYPES = Set.of("minecraft:custom_data", "minecraft:lore", "minecraft:custom_name");

	private static final java.util.Map<String, Object> NAME = java.util.Map.of("minecraft:custom_name", "Titanium Drill DR-X555");

	private static DrillRefresh.Stack stack(String item, Set<String> types, String id, String uuid, boolean fuel) {
		return new DrillRefresh.Stack(item, types, id, uuid, fuel, NAME);
	}

	private static DrillRefresh.Stack drill(String uuid) {
		return stack("minecraft:prismarine_shard", TYPES, "TITANIUM_DRILL_2", uuid, true);
	}

	private static boolean skip(DrillRefresh.Stack from, DrillRefresh.Stack to) {
		return instant(from, to, true, true, false);
	}

	private static boolean instant(DrillRefresh.Stack from, DrillRefresh.Stack to, boolean onSkyBlock, boolean enabled, boolean original) {
		return DrillRefresh.instant(() -> from, () -> to, onSkyBlock, enabled, original);
	}

	@Test
	void aRefreshedCopyOfTheSameDrillSkipsTheDip() {
		// Only lore or fuel changed: the views are equal, as fuel and lore are not part of them.
		assertTrue(skip(drill("a-1"), drill("a-1")));
	}

	@Test
	void everythingElseKeepsTheOriginalResult() {
		assertFalse(skip(drill("a-1"), drill("b-2")), "another drill (EC-DRILL-01)");
		assertFalse(skip(drill(""), drill("")), "no uuid on either");
		assertFalse(skip(drill(null), drill(null)), "no uuid at all");
		assertFalse(skip(drill("a-1"), stack("minecraft:prismarine_shard", TYPES, "DIVAN_DRILL", "a-1", true)), "another id");
		assertFalse(skip(drill("a-1"), stack("minecraft:prismarine_shard", Set.of("minecraft:custom_data", "minecraft:lore",
				"minecraft:custom_name", "minecraft:enchantment_glint_override"), "TITANIUM_DRILL_2", "a-1", true)), "a component type added");
		assertFalse(skip(drill("a-1"), stack("minecraft:diamond_pickaxe", TYPES, "TITANIUM_DRILL_2", "a-1", true)), "another item");
		assertFalse(skip(stack("minecraft:player_head", TYPES, "PICKONIMBUS", "p-1", false),
				stack("minecraft:player_head", TYPES, "PICKONIMBUS", "p-1", false)), "not a drill (EC-DRILL-07, REQ-DRILL-08)");
		assertFalse(skip(drill("a-1"), stack("minecraft:prismarine_shard", TYPES, "TITANIUM_DRILL_2", "a-1", false)), "fuel gone on the new stack");
		assertFalse(skip(stack("minecraft:prismarine_shard", TYPES, "TITANIUM_DRILL_2", "a-1", false), drill("a-1")), "fuel only on the new stack");
		assertFalse(skip(drill("a-1"), new DrillRefresh.Stack("minecraft:prismarine_shard", TYPES, "TITANIUM_DRILL_2", "a-1", true,
				java.util.Map.of("minecraft:custom_name", "Renamed"))), "another name: not only lore, fuel or damage changed");
		assertFalse(skip(drill("a-1"), null), "an empty hand");
		assertFalse(skip(null, drill("a-1")));
	}

	@Test
	void itNeverForcesTheDipAndIsVanillaWhenOffOrOffSkyBlock() {
		assertTrue(instant(drill("a-1"), drill("b-2"), true, true, true), "original true stays true");
		assertTrue(instant(null, null, false, false, true));
		assertFalse(instant(drill("a-1"), drill("a-1"), true, false, false), "toggle off: original");
		assertFalse(instant(drill("a-1"), drill("a-1"), false, true, false), "off SkyBlock: original (REQ-DRILL-05)");
		boolean[] read = {false};
		DrillRefresh.instant(() -> {
			read[0] = true;
			return drill("a-1");
		}, () -> drill("a-1"), false, true, false);
		assertFalse(read[0], "off SkyBlock the stacks are not even read");
	}

	@Test
	void theFeatureTouchesOnlyTheHandSwapDecision() throws java.io.IOException {
		// AC-DRILL-02 [R]: no block breaking, game mode or packet; AC-DRILL-06 [R]: no fuel parser, HUD or low-fuel warning.
		java.nio.file.Path project = java.nio.file.Path.of(System.getProperty("k8bas.projectDir", "."));
		java.util.List<java.nio.file.Path> files = new java.util.ArrayList<>(java.util.List.of(
				project.resolve("src/main/java/com/k8bas/skyblockutility/mixin/DrillReequipMixin.java")));
		try (var list = java.nio.file.Files.list(project.resolve("src/main/kotlin/com/k8bas/skyblockutility/module/drillfix"))) {
			list.forEach(files::add);
		}
		java.util.List<String> hits = new java.util.ArrayList<>();
		for (java.nio.file.Path file : files) {
			String code = java.nio.file.Files.readString(file);
			for (String forbidden : java.util.List.of("GameMode", "gameMode", "DestroyBlock", "destroyProgress", "Packet", "connection.", "HudElement",
					"HudRegistry", "Fuel: ", "Regex(", "Pattern.compile", "ChatUtils", "Notices")) {
				if (code.contains(forbidden)) {
					hits.add(file.getFileName() + ": " + forbidden);
				}
			}
		}
		assertEquals(java.util.List.of(), hits);
		String mixin = java.nio.file.Files.readString(files.getFirst());
		assertEquals(java.util.List.of("shouldInstantlyReplaceVisibleItem"), java.util.regex.Pattern.compile("method = \"(\\w+)\\(").matcher(mixin).results()
				.map(match -> match.group(1)).toList(), "its only hook is the hand's item-swap decision");
	}

	@Test
	void theFuelKeyIsHypixelsName() {
		// Pins the key name (from a real drill's custom data; whether a drill at 0 fuel keeps it is checked at G3).
		assertEquals("drill_fuel", DrillRefresh.FUEL_KEY);
	}

	@Test
	void theCardIsInMiningOnByDefaultAndNamesTheOtherMods() {
		// REQ-DRILL-06, REQ-DRILL-07.
		DrillFixConfig config = new DrillFixConfig();
		assertTrue(config.enabled);
		var unavailable = new com.k8bas.skyblockutility.ui.option.OptionStatus(com.k8bas.skyblockutility.ui.option.OptionStatus.State.UNAVAILABLE, "x");
		var cards = DrillFixModule.cards(config, value -> config.enabled = value, () -> unavailable);
		assertEquals(1, cards.size());
		var card = cards.getFirst();
		assertEquals("drill_fix", card.id());
		assertEquals(com.k8bas.skyblockutility.ui.option.Category.MINING, card.category());
		assertEquals("Drill re-equip fix", card.toggle().text().title());
		String tooltip = card.toggle().text().tooltip();
		assertTrue(tooltip.contains("Skyblocker") && tooltip.contains("NoFrills") && tooltip.contains("does not turn theirs off"), tooltip);
		assertEquals(unavailable, card.toggle().status(), "a skipped hook shows on the card");
	}
}
