package com.k8bas.skyblockutility.module.hotspot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hotspot text and radius rules (T3.6): AC-HOT-01 and AC-HOT-02 [A]. */
class HotspotRulesTest {
	@Test
	void hotspotStandsAreRecognisedByTheirWholeName() {
		// AC-HOT-01; whether vanilla displays the name is the scan's check (HotspotScanTest).
		assertTrue(HotspotText.isHotspot("§d§lHOTSPOT"));
		assertTrue(HotspotText.isHotspot(" HOTSPOT "));
		assertFalse(HotspotText.isHotspot("HOTSPOTS"));
		assertFalse(HotspotText.isHotspot("Hotspot Radar"));
		assertFalse(HotspotText.isHotspot(""));
	}

	@Test
	void buffLinesMapToTheirTypeAndUnknownTextIsLoggedOnce() {
		List<String> logged = new ArrayList<>();
		HotspotText text = new HotspotText(logged::add);
		assertEquals(HotspotText.Buff.SEA_CREATURE_CHANCE, text.buff("§3+5α Sea Creature Chance"));
		assertEquals(HotspotText.Buff.FISHING_SPEED, text.buff("+15☂ Fishing Speed"));
		assertEquals(HotspotText.Buff.DOUBLE_HOOK_CHANCE, text.buff("+2⚓ Double Hook Chance"));
		assertEquals(HotspotText.Buff.TREASURE_CHANCE, text.buff("+1⛃ Treasure Chance"));
		assertEquals(HotspotText.Buff.TROPHY_FISH_CHANCE, text.buff("+5♔ Trophy Fish Chance"));
		assertEquals(HotspotText.Buff.SHARD, text.buff("Chance of Lava Shard"));
		assertEquals(HotspotText.Buff.SHARD, text.buff("§bChance of Mossybit Shard"));
		assertEquals(List.of(), logged);
		assertEquals(HotspotText.Buff.UNKNOWN, text.buff("+3 Something New"));
		assertEquals(HotspotText.Buff.UNKNOWN, text.buff("+3 Something New"));
		assertEquals(HotspotText.Buff.UNKNOWN, text.buff("Another"));
		assertEquals(List.of("+3 Something New", "Another"), logged, "each unknown text once");
	}

	@Test
	void eachBuffHasItsDocumentedColour() {
		// REQ-HOT-05: SCC dark aqua, speed aqua, double hook blue, treasure and trophy gold, shard yellow, unknown white.
		assertEquals(0x00AAAA, HotspotText.Buff.SEA_CREATURE_CHANCE.getColor());
		assertEquals(0x55FFFF, HotspotText.Buff.FISHING_SPEED.getColor());
		assertEquals(0x5555FF, HotspotText.Buff.DOUBLE_HOOK_CHANCE.getColor());
		assertEquals(0xFFAA00, HotspotText.Buff.TREASURE_CHANCE.getColor());
		assertEquals(0xFFAA00, HotspotText.Buff.TROPHY_FISH_CHANCE.getColor());
		assertEquals(0xFFFF55, HotspotText.Buff.SHARD.getColor());
		assertEquals(0xFFFFFF, HotspotText.Buff.UNKNOWN.getColor());
	}

	@Test
	void theRadiusIsTheModeInHalfBlocksCappedWithAFallback() {
		// AC-HOT-02: about 10 % outliers do not move the mode.
		List<Double> samples = new ArrayList<>();
		for (int i = 0; i < 45; i++) {
			samples.add(2.4 + (i % 3) * 0.05);
		}
		for (int i = 0; i < 5; i++) {
			samples.add(0.9 + i);
		}
		assertEquals(2.5, HotspotRadius.estimate(samples, 3.0, 3.0));
		assertEquals(3.0, HotspotRadius.estimate(List.of(4.6, 4.5, 4.4, 1.0), 3.0, 3.0), "above the island cap: the cap");
		assertEquals(4.0, HotspotRadius.estimate(List.of(), 5.0, 4.0), "no samples: the fallback");
		assertEquals(2.0, HotspotRadius.estimate(List.of(2.0, 2.0, 3.0, 3.0), 5.0, 5.0), "a tie: the smaller");
		assertEquals(0.5, HotspotRadius.estimate(List.of(0.1, 0.2, 0.0), 3.0, 3.0), "particles at the centre: still a ring");
	}

	@Test
	void theSurfaceIsTheTopOfTheFirstLiquidWithinThreeBlocksBelow() {
		// SPEC's hotspot terms: water at 60..61 (height 0.888 at the top), the stand at 63.0.
		java.util.function.IntFunction<Float> pool = y -> y == 61 ? Float.valueOf(0.888f) : y == 60 ? Float.valueOf(1.0f) : null;
		assertEquals(61.888, HotspotSurface.find(63.0, pool), 1e-6);
		assertEquals(61.888, HotspotSurface.find(64.0, pool), 1e-6, "3 blocks below: still found");
		assertEquals(null, HotspotSurface.find(65.0, pool), "4 blocks below: not this hotspot's");
		assertEquals(61.888, HotspotSurface.find(60.5, pool), 1e-6, "a stand in the liquid: the top of its column");
		assertEquals(null, HotspotSurface.find(63.0, y -> null), "nothing loaded yet");
	}

	@Test
	void capsDependOnTheIsland() {
		// REQ-HOT-04 (UNVERIFIED in game): 5 on the Crimson Isle and Torrhus Canyon, 4 at Jerry's Workshop and Lotus Atoll, 3 elsewhere.
		assertEquals(5.0, HotspotRadius.cap("Crimson Isle"));
		assertEquals(5.0, HotspotRadius.cap("Torrhus Canyon"));
		assertEquals(4.0, HotspotRadius.cap("Jerry"));
		assertEquals(4.0, HotspotRadius.cap("Lotus Atoll"));
		assertEquals(3.0, HotspotRadius.cap("Hub"));
		assertEquals(3.0, HotspotRadius.cap("A New Island"), "EC-HOT-12: the generic cap");
		assertEquals(3.0, HotspotRadius.cap(null));
		assertEquals(HotspotRadius.cap("Crimson Isle"), HotspotRadius.fallback("Crimson Isle"), "the fallback starts at the cap");
	}
}
