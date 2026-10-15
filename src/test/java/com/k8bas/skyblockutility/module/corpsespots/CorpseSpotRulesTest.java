package com.k8bas.skyblockutility.module.corpsespots;

import com.k8bas.skyblockutility.skyblock.Corpse;
import com.k8bas.skyblockutility.skyblock.CorpseList;
import com.k8bas.skyblockutility.skyblock.MineshaftSnapshot;
import com.k8bas.skyblockutility.skyblock.WidgetPresence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The possible corpse spots' rules without a game (T3.3, R40): which spots show (REQ-CORPSE-03, REQ-CORPSE-04,
 * REQ-CORPSE-05: AC-CORPSE-04's "same set whatever the entities", AC-CORPSE-05's table) and the `/ksu corpses` reply
 * (REQ-CORPSE-06, AC-CORPSE-06).
 */
class CorpseSpotRulesTest {
	private static final CorpseSpots SPOTS = new CorpseSpots(Map.of(
			"OPAL_1", List.of(new CorpseSpot(1, -100, 10, -150), new CorpseSpot(2, -120, 10, -150), new CorpseSpot(3, -110, 10, -150))),
			Set.of("TOPA_2"));

	private static MineshaftSnapshot shaft(String code, CorpseList corpses) {
		return new MineshaftSnapshot(true, code, code == null ? null : "Opal 1", code == null, corpses);
	}

	private static CorpseList widget(boolean... looted) {
		return new CorpseList(WidgetPresence.PRESENT, java.util.stream.IntStream.range(0, looted.length)
				.mapToObj(i -> new Corpse("Lapis", looted[i])).toList());
	}

	private static CorpseSpotsConfig config(boolean enabled, boolean hide) {
		CorpseSpotsConfig config = new CorpseSpotsConfig();
		config.enabled = enabled;
		config.hideWhenLooted = hide;
		return config;
	}

	@Test
	void everySpotOfTheShaftShows() {
		assertEquals(SPOTS.spots("OPAL_1"), CorpseSpotRules.visible(config(true, true), shaft("OPAL_1", widget(false, false)), SPOTS));
	}

	@Test
	void nothingShowsOffOutsideWithoutACodeOrWithoutData() {
		assertEquals(List.of(), CorpseSpotRules.visible(config(false, true), shaft("OPAL_1", CorpseList.ABSENT), SPOTS));
		assertEquals(List.of(), CorpseSpotRules.visible(config(true, true), MineshaftSnapshot.NONE, SPOTS));
		assertEquals(List.of(), CorpseSpotRules.visible(config(true, true), shaft(null, CorpseList.ABSENT), SPOTS));
		assertEquals(List.of(), CorpseSpotRules.visible(config(true, true), shaft("TOPA_2", CorpseList.ABSENT), SPOTS));
		assertEquals(List.of(), CorpseSpotRules.visible(config(true, true), shaft("OPAL_1", CorpseList.ABSENT), null));
	}

	@Test
	void hideWhenAllLootedFollowsTheWidgetAndItsToggle() {
		// AC-CORPSE-05.
		List<CorpseSpot> all = SPOTS.spots("OPAL_1");
		assertEquals(all, CorpseSpotRules.visible(config(true, true), shaft("OPAL_1", widget(false, false)), SPOTS));
		assertEquals(List.of(), CorpseSpotRules.visible(config(true, true), shaft("OPAL_1", widget(true, true)), SPOTS));
		assertEquals(all, CorpseSpotRules.visible(config(true, false), shaft("OPAL_1", widget(true, true)), SPOTS));
		assertEquals(all, CorpseSpotRules.visible(config(true, true), shaft("OPAL_1", CorpseList.ABSENT), SPOTS), "no widget: they stay");
		assertEquals(all, CorpseSpotRules.visible(config(true, true), shaft("OPAL_1", widget(true, false)), SPOTS), "one not looted again");
		// A list cut off or with an unknown line is not "all looted" (REQ-GS-10).
		CorpseList partial = new CorpseList(WidgetPresence.PARTIAL, List.of(new Corpse("Lapis", true)));
		assertEquals(all, CorpseSpotRules.visible(config(true, true), shaft("OPAL_1", partial), SPOTS));
	}

	@Test
	void theListIsSortedByDistanceWithHalfUpMetres() {
		// To each block centre: the player 2.5 blocks from #3, 7.5 from #1 and 12.5 from #2: #3, #1, #2, half-up.
		assertEquals(List.of("Opal 1: 3 possible corpse spots", "#3 -110, 10, -150 · 3 m", "#1 -100, 10, -150 · 8 m", "#2 -120, 10, -150 · 13 m"),
				CorpseSpotRules.list(shaft("OPAL_1", CorpseList.ABSENT), SPOTS, false, new double[] {-107.0, 10.5, -149.5}));
	}

	@Test
	void theSpecialReplies() {
		assertEquals(List.of("Not in a Glacite Mineshaft"), CorpseSpotRules.list(MineshaftSnapshot.NONE, SPOTS, false, null));
		assertEquals(List.of("Shaft not identified yet"), CorpseSpotRules.list(shaft(null, CorpseList.ABSENT), SPOTS, false, null));
		assertEquals(List.of("No spot data for TOPA_2"), CorpseSpotRules.list(shaft("TOPA_2", CorpseList.ABSENT), SPOTS, false, null));
		assertEquals(List.of("No spot data for NOPE_1"), CorpseSpotRules.list(shaft("NOPE_1", CorpseList.ABSENT), SPOTS, false, null));
		assertEquals(List.of("Corpse spot data unavailable"), CorpseSpotRules.list(shaft("OPAL_1", CorpseList.ABSENT), null, true, null));
		// While the table still loads, the same "unavailable" reply, so the command never says something wrong.
		assertEquals(List.of("Corpse spot data unavailable"), CorpseSpotRules.list(shaft("OPAL_1", CorpseList.ABSENT), null, false, null));
	}

	@Test
	void aRequestWaitsForTheShaftToBeRead() {
		// Review finding (G3): with both shaft features off, the state starts only after the command, so it waits.
		CorpseListRequest request = new CorpseListRequest(1_000);
		assertEquals(true, request.ready(MineshaftSnapshot.NONE, false, 1_000), "outside a shaft: at once");
		assertEquals(false, request.ready(MineshaftSnapshot.NONE, true, 1_050), "in a shaft, not read yet: wait");
		assertEquals(false, request.ready(shaft(null, CorpseList.ABSENT), true, 2_000), "code search running: wait");
		assertEquals(true, request.ready(shaft("OPAL_1", CorpseList.ABSENT), true, 2_000), "code known: answer");
		MineshaftSnapshot unknown = new MineshaftSnapshot(true, null, "Unknown shaft", false, CorpseList.ABSENT);
		assertEquals(true, request.ready(unknown, true, 16_000), "search over without a code: answer");
		assertEquals(true, request.ready(MineshaftSnapshot.NONE, true, 17_000), "after 16 s whatever the state");
	}

	@Test
	void aDingMarksTheSpotsWithinTwentyBlocks() {
		// REQ-CORPSE-11 (R41 (c)): to each block centre, #1 is 7.5 blocks from [near]; from [far], #1 is 18.8 blocks away
		// (within the 20-block reach) and #2 20.8 (outside it).
		double[] near = {-107.0, 10.5, -149.5};
		double[] far = {-97.0 - 10.5, 10.5, -149.5 + 17};
		CorpseSpot first = SPOTS.spots("OPAL_1").get(0);
		assertEquals("Possible corpse #1", CorpseSpotRules.label(first, near, null).text());
		assertEquals("Possible corpse #1 · ding nearby: close", CorpseSpotRules.label(first, near, DingHint.Warmth.CLOSE).text());
		assertEquals("Possible corpse #1 · ding nearby: very close", CorpseSpotRules.label(first, near, DingHint.Warmth.VERY_CLOSE).text());
		CorpseSpot second = SPOTS.spots("OPAL_1").get(1);
		assertEquals("Possible corpse #1 · ding nearby: far", CorpseSpotRules.label(first, far, DingHint.Warmth.FAR).text(), "18.8 blocks: within the reach");
		assertEquals("Possible corpse #2", CorpseSpotRules.label(second, far, DingHint.Warmth.FAR).text(), "20.8 blocks: outside it");
		assertEquals("Possible corpse #1", CorpseSpotRules.label(first, null, DingHint.Warmth.FAR).text(), "no player, no hint");
	}

	@Test
	void withoutAPlayerTheListKeepsTheDataOrder() {
		assertEquals(List.of("Opal 1: 3 possible corpse spots", "#1 -100, 10, -150", "#2 -120, 10, -150", "#3 -110, 10, -150"),
				CorpseSpotRules.list(shaft("OPAL_1", CorpseList.ABSENT), SPOTS, false, null));
	}
}
