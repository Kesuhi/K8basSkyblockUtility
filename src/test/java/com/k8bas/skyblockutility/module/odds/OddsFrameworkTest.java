package com.k8bas.skyblockutility.module.odds;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Rare Drop Odds framework (T3.9h): the case gate (REQ-ODDS-01/03/11, AC-ODDS-09) and the counters (AC-ODDS-10). */
class OddsFrameworkTest {
	@Test
	void aCaseRunsOnlyWithTheMasterItsToggleAndItsTables() {
		assertTrue(OddsGate.active(true, true, OddsCase.CROESUS, table -> true));
		assertFalse(OddsGate.active(false, true, OddsCase.CROESUS, table -> true), "the master toggle off (REQ-ODDS-01)");
		assertFalse(OddsGate.active(true, false, OddsCase.CROESUS, table -> true), "its own toggle off (REQ-ODDS-03)");
	}

	@Test
	void aMissingTableTurnsOffOnlyItsOwnCases() {
		// AC-ODDS-09: the Vanguard table could not be read.
		java.util.function.Predicate<String> noVanguard = table -> !table.equals(OddsTables.VANGUARD);
		assertFalse(OddsGate.active(true, true, OddsCase.VANGUARD, noVanguard));
		for (OddsCase other : List.of(OddsCase.SCATHA, OddsCase.CROESUS, OddsCase.CORPSES, OddsCase.SLAYER)) {
			assertTrue(OddsGate.active(true, true, other, noVanguard), other + " still works");
		}
		assertEquals("No data, off: Vanguard corpses", OddsGate.notice(noVanguard));
		assertNull(OddsGate.notice(table -> true));
		assertEquals("No data, off: Dungeon reward chests, Slayer RNG meter",
				OddsGate.notice(table -> !table.equals(OddsTables.RNG_SCORE)));
	}

	@Test
	void onlyATableThatFailedIsUnreadable() {
		// AC-ODDS-09's real path: the registry's states.
		assertFalse(OddsTables.readable(new com.k8bas.skyblockutility.data.TableState.Unavailable("missing")));
		assertTrue(OddsTables.readable(com.k8bas.skyblockutility.data.TableState.Loading.INSTANCE), "still loading: its case waits");
	}

	@Test
	void anUnknownNameIsLoggedOncePerSession() {
		// AC-ODDS-09 [A]: exactly one log line per unknown name.
		List<String> lines = new java.util.ArrayList<>();
		OnceLog log = new OnceLog(lines::add);
		log.log("croesus:DIRT", "no data for DIRT");
		log.log("croesus:DIRT", "no data for DIRT");
		log.log("slayer:DIRT", "no data for DIRT (slayer)");
		assertEquals(List.of("no data for DIRT", "no data for DIRT (slayer)"), lines);
	}

	@Test
	void everyCaseNamesTablesThatExist() {
		Set<String> tables = Set.of(OddsTables.RNG_SCORE, OddsTables.VANGUARD, OddsTables.SUPPLIED);
		assertEquals(5, OddsCase.values().length, "exactly the five cases of v1 (D-3)");
		for (OddsCase c : OddsCase.values()) {
			assertTrue(tables.containsAll(c.getTables()), c.name());
		}
	}

	@Test
	void theCardIsInOddsAndTrackersOffWithFiveCasesOn() {
		// AC-ODDS-01 [A].
		OddsConfig config = new OddsConfig();
		var cards = RareDropOddsModule.cards(config, value -> config.enabled = value);
		assertEquals(1, cards.size());
		var card = cards.getFirst();
		assertEquals(com.k8bas.skyblockutility.ui.option.Category.ODDS_TRACKERS, card.category());
		assertEquals("Rare Drop Odds", card.toggle().text().title());
		assertFalse(card.toggle().binding().get(), "the master toggle is OFF (D-6)");
		List<String> titles = card.options().stream().map(option -> option.text().title()).toList();
		assertEquals(List.of("Scatha", "Dungeon reward chests", "Vanguard corpses", "Lapis, Umber and Tungsten corpses", "Slayer RNG meter",
				"Chat lines", "Rare threshold", "Reset odds counters"), titles);
		for (int i = 0; i < 6; i++) {
			assertTrue(((com.k8bas.skyblockutility.ui.option.Toggle) card.options().get(i)).binding().get(), titles.get(i) + " ON");
		}
		var threshold = (com.k8bas.skyblockutility.ui.option.Choice<?>) card.options().get(6);
		assertEquals(0.01, (Double) threshold.values().getFirst(), 1e-12);
		assertEquals(10.0, (Double) threshold.values().getLast(), 1e-12);
		assertEquals(2.0, (Double) threshold.binding().get(), 1e-12);
		// "Gambling" only as a hidden keyword (REQ-ODDS-02, P7): never in a title, description or tooltip.
		for (var option : card.all()) {
			String shown = (option.text().title() + " " + option.text().description() + " " + option.text().tooltip()).toLowerCase();
			assertFalse(shown.contains("gambl"), option.id());
		}
		assertTrue(card.toggle().text().keywords().contains("gambling"));
		var index = com.k8bas.skyblockutility.ui.option.SearchIndex.of(cards);
		assertTrue(index.search("Gambling").hits().stream().anyMatch(hit -> hit.cardId().equals("rare_drop_odds")), "the brief's name finds the card");
	}

	@Test
	void theResetButtonRunsTheGivenReset() {
		int[] resets = {0};
		var card = RareDropOddsModule.cards(new OddsConfig(), value -> { }, () -> com.k8bas.skyblockutility.ui.option.OptionStatus.OK, () -> { },
				() -> resets[0]++).getFirst();
		((com.k8bas.skyblockutility.ui.option.ActionOption) card.options().getLast()).action().run();
		assertEquals(1, resets[0]);
	}

	@Test
	void theFeatureSendsNothingAndChangesNoPacketOrMenu() throws java.io.IOException {
		// AC-ODDS-02 [R]: no chat or command send, no click, no screen open, no cancelling or changing a message or packet.
		java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("k8bas.projectDir", ".")).resolve("src/main/kotlin/com/k8bas/skyblockutility/module/odds");
		List<String> hits = new java.util.ArrayList<>();
		try (var files = java.nio.file.Files.list(dir)) {
			for (var file : files.toList()) {
				String code = java.nio.file.Files.readString(file);
				for (String forbidden : List.of("sendChat", "sendCommand", "connection.send", "handleInventoryMouseClick", "handleContainerInput",
						"setScreen", "openScreen", ".cancel(", "cancellable", "setMessage", "Packet")) {
					if (code.contains(forbidden)) {
						hits.add(file.getFileName() + ": " + forbidden);
					}
				}
			}
		}
		assertEquals(List.of(), hits);
	}

	/** Two profiles' saved states, the way the profile service keeps them (nothing is read or written while not storable). */
	private final Map<String, String> store = new HashMap<>();
	private String profile = "A";
	private boolean storable = true;

	private OddsCounters counters() {
		return new OddsCounters(() -> storable ? store.get(profile) : null, content -> {
			if (storable) {
				store.put(profile, content);
			}
		}, () -> LocalDate.of(2026, 10, 9));
	}

	private void load(OddsCounters counters) {
		counters.load(profile, storable);
	}

	@Test
	void countersArePerProfileAndSurviveARestart() {
		// AC-ODDS-10: 312 kills since the last pet on profile A; B has its own; back on A, 312 again.
		OddsCounters counters = counters();
		load(counters);
		for (int i = 0; i < 312; i++) {
			counters.scathaKill();
		}
		assertEquals(312L, counters.scathaSincePet());
		assertEquals("2026-10-09", counters.since(), "counted by K8bas since this day");
		profile = "B";
		load(counters);
		assertEquals(0L, counters.scathaSincePet(), "B shows its own counter");
		counters.scathaKill();
		profile = "A";
		load(counters);
		assertEquals(312L, counters.scathaSincePet());
		assertEquals(0, counters.sessionScathaKills(), "a profile switch starts the session over");

		OddsCounters restarted = counters();
		load(restarted);
		assertEquals(312L, restarted.scathaSincePet(), "the value survives a restart");
		assertEquals(312L, restarted.scathaKills());
	}

	@Test
	void anUnknownOrUnsettledProfileShowsNoNumberAndKeepsItsCounts() {
		OddsCounters counters = counters();
		load(counters);
		for (int i = 0; i < 312; i++) {
			counters.scathaKill();
		}
		// Reconnected: the tab names A again, but its id is not settled yet.
		storable = false;
		load(counters);
		assertNull(counters.scathaSincePet(), "no false zero while A cannot be read");
		counters.scathaKill();
		counters.scathaKill();
		counters.corpseLooted("VANGUARD");
		storable = true;
		load(counters);
		assertEquals(314L, counters.scathaSincePet(), "the kills made meanwhile are added to A");
		assertEquals(1L, counters.corpses("VANGUARD"));
		// A pet while unsettled: since-last-pet is the kills after it.
		storable = false;
		load(counters);
		counters.scathaKill();
		counters.scathaPet();
		counters.scathaKill();
		storable = true;
		load(counters);
		assertEquals(1L, counters.scathaSincePet());
		assertEquals(316L, counters.scathaKills());
		// Counted while no profile was named at all: not kept.
		counters.load(null, false);
		counters.scathaKill();
		load(counters);
		assertEquals(316L, counters.scathaKills());
	}

	@Test
	void theSessionStartsOverOnlyWhenAnotherProfileArrives() {
		OddsCounters counters = counters();
		load(counters);
		counters.scathaKill();
		counters.load(null, false);
		load(counters);
		assertEquals(1, counters.sessionScathaKills(), "a reconnect to the same profile keeps the session");
		storable = false;
		load(counters);
		storable = true;
		load(counters);
		assertEquals(1, counters.sessionScathaKills(), "the same profile getting its id keeps it too");
		profile = "B";
		load(counters);
		assertEquals(0, counters.sessionScathaKills(), "another profile starts it over");
	}

	@Test
	void theResetButtonZeroesOnlyTheSessionCounters() {
		OddsCounters counters = counters();
		load(counters);
		counters.scathaKill();
		counters.scathaKill();
		counters.corpseLooted("VANGUARD");
		assertEquals(2, counters.sessionScathaKills());
		assertEquals(1, counters.sessionCorpses("VANGUARD"));
		counters.resetSession();
		assertEquals(0, counters.sessionScathaKills());
		assertEquals(0, counters.sessionCorpses("VANGUARD"));
		assertEquals(2L, counters.scathaKills(), "the per-profile counters stay");
		assertEquals(2L, counters.scathaSincePet());
		assertEquals(1L, counters.corpses("VANGUARD"));
	}

	@Test
	void aPetStartsSinceLastPetAgainAndADamagedFileStartsOver() {
		OddsCounters counters = counters();
		load(counters);
		counters.scathaKill();
		counters.scathaPet();
		assertEquals(0L, counters.scathaSincePet());
		assertEquals(1L, counters.scathaKills());
		store.put(profile, "{ not json");
		load(counters);
		assertEquals(0L, counters.scathaKills());
		assertNull(counters.since());
	}
}
