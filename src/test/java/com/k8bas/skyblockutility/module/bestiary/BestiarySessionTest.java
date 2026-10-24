package com.k8bas.skyblockutility.module.bestiary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * T5.2c, the session and the rate [A]: AC-BEST-05 (100 kills over 5 min with W = 5 is 1,200/h, 51 left is 2m33s; "—" under
 * 60 s of data; 0/h and no ETA without kills in the window) and AC-BEST-06 (412 session kills: an island change keeps them
 * with the option OFF and resyncs; a profile switch, a manual reset and, with the option ON, an island change give 0),
 * REQ-BEST-06, REQ-BEST-07, EC-BEST-07, EC-BEST-17.
 */
class BestiarySessionTest {
	private static final String DWARVEN = "Dwarven Mines";
	private static final long SECOND = 1_000;
	private static final long MINUTE = 60 * SECOND;

	private final List<String> log = new ArrayList<>();
	private final BestiaryTracker tracker = new BestiaryTracker(BestiaryTables.shipped(), log::add);

	private void ghost(long kills, long now) {
		tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 15: " + String.format(Locale.ROOT, "%,d", kills) + "/12,500")), DWARVEN, now);
	}

	/** 100 kills in ten updates of 10 over 5 minutes, after a baseline of 12,349 at 0: 12,449 at the end. */
	private void hundredKillsOverFiveMinutes() {
		ghost(12_349, 0);
		for (int i = 1; i <= 10; i++) {
			ghost(12_349 + 10L * i, i * 30 * SECOND);
		}
	}

	@Test
	void theRateAndTheEtas() {
		hundredKillsOverFiveMinutes();
		RateView rate = tracker.rate("ghost", 5 * MINUTE);
		assertEquals(1_200.0, rate.perHour(), 1e-9);
		// 51 left to 12,500 at 1,200/h: 153 s, shown as 2m33s; 12,551 to the cap 25,000.
		assertEquals(153 * SECOND, rate.etaNextMs());
		assertEquals(Math.round(12_551 / 1_200.0 * 3_600_000), rate.etaMaxMs());
	}

	@Test
	void underAMinuteOfDataTheRateIsUnknown() {
		ghost(12_349, 0);
		ghost(12_359, 30 * SECOND);
		assertNull(tracker.rate("ghost", 59 * SECOND).perHour());
		assertNull(tracker.rate("ghost", 59 * SECOND).etaNextMs());
		assertNotNull(tracker.rate("ghost", 60 * SECOND).perHour());
	}

	@Test
	void withoutKillsInTheWindowTheRateIsZeroAndThereIsNoEta() {
		// EC-BEST-07: the counts stop (the AFK rule); the rate falls to 0 across the window.
		hundredKillsOverFiveMinutes();
		RateView idle = tracker.rate("ghost", 10 * MINUTE + SECOND);
		assertEquals(0.0, idle.perHour(), 1e-9);
		assertNull(idle.etaNextMs());
		assertNull(idle.etaMaxMs());
	}

	@Test
	void theWindowIsOneToSixtyMinutes() {
		hundredKillsOverFiveMinutes();
		tracker.setWindowMinutes(1);
		// The last minute holds the updates at 4:30 and 5:00: 20 kills in a minute.
		assertEquals(1_200.0, tracker.rate("ghost", 5 * MINUTE).perHour(), 1e-9);
		tracker.setWindowMinutes(0);
		assertEquals(1_200.0, tracker.rate("ghost", 5 * MINUTE).perHour(), 1e-9, "below 1 is 1");
	}

	@Test
	void aWindowAboveSixtyMinutesIsSixty() {
		// 50 kills at 10 min and 10 at 85 min, after a baseline at 0: at 90 min a 60-minute window holds the 10 only.
		ghost(12_000, 0);
		ghost(12_050, 10 * MINUTE);
		ghost(12_060, 85 * MINUTE);
		tracker.setWindowMinutes(600);
		assertEquals(10.0, tracker.rate("ghost", 90 * MINUTE).perHour(), 1e-9);
	}

	@Test
	void anHourOfRisesIsKeptForAWindowRaisedLater() {
		ghost(12_000, 0);
		ghost(12_030, MINUTE);
		tracker.setWindowMinutes(1);
		ghost(12_060, 3 * MINUTE);
		tracker.setWindowMinutes(60);
		// Both rises, over the 3 minutes of data.
		assertEquals(1_200.0, tracker.rate("ghost", 3 * MINUTE).perHour(), 1e-9);
	}

	@Test
	void anAbbreviatedCountGivesNoRate() {
		// EC-BEST-17: a family seen only as "12.4k" has no live count, so no rate.
		tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 15: 12.4k/12,500")), DWARVEN, 0);
		assertNull(tracker.rate("ghost", 2 * MINUTE).perHour());
	}

	@Test
	void theSessionEndsOnAResetAProfileSwitchOrWithTheOptionAnIslandChange() {
		// AC-BEST-06: 412 session kills (eight rises of 50 and one of 12).
		tracker.locationChanged(DWARVEN, false);
		long kills = 10_000;
		ghost(kills, 0);
		for (int i = 1; i <= 8; i++) {
			ghost(kills += 50, i * SECOND);
		}
		ghost(kills += 12, 9 * SECOND);
		assertEquals(412, tracker.session().kills());
		// The option OFF: another island keeps the kills and resyncs, so a rise right after it is a new baseline.
		tracker.locationChanged("Hub", false);
		ghost(kills += 30, 11 * SECOND);
		assertEquals(412, tracker.session().kills());
		// The option ON: a transfer (no island yet) and the same island are no change; another island is.
		tracker.locationChanged(null, true);
		tracker.locationChanged("Hub", true);
		assertEquals(412, tracker.session().kills());
		for (long t = 13 * SECOND; t <= 13 * SECOND + MINUTE; t += 50) {
			tracker.tick(true, t);
		}
		tracker.locationChanged(DWARVEN, true);
		assertEquals(0, tracker.session().kills());
		assertEquals(0, tracker.session().durationMs(), "its time too");
		assertEquals(0, tracker.family("ghost").sessionKills());
		assertNotNull(tracker.family("ghost").kills(), "the shown values stay");
		// A manual reset and a profile switch give 0 too.
		ghost(kills += 5, 15 * SECOND);
		ghost(kills += 5, 16 * SECOND);
		assertEquals(5, tracker.session().kills());
		tracker.resetSession();
		assertEquals(0, tracker.session().kills());
		assertNull(tracker.rate("ghost", 10 * MINUTE).perHour(), "the rate's data starts over");
		ghost(kills += 5, 19 * SECOND);
		assertEquals(5, tracker.session().kills());
		for (long t = 20 * SECOND; t <= 20 * SECOND + MINUTE; t += 50) {
			tracker.tick(true, t);
		}
		tracker.profileChanged();
		assertEquals(0, tracker.session().kills());
		assertEquals(0, tracker.session().durationMs(), "its time too");
	}

	@Test
	void theSessionsTimeCountsOnlyOnSkyBlock() {
		// Ticks every 50 ms: two minutes on SkyBlock, one minute off it, and a pause of ten minutes counted as at most a second.
		long now = 0;
		for (; now <= 2 * MINUTE; now += 50) {
			tracker.tick(true, now);
		}
		for (; now <= 3 * MINUTE; now += 50) {
			tracker.tick(false, now);
		}
		tracker.tick(true, now);
		tracker.tick(true, now + 10 * MINUTE);
		long duration = tracker.session().durationMs();
		assertEquals(2 * MINUTE + SECOND, duration, 100.0);
		// The average: the session's kills over its time.
		tracker.resetSession();
		assertNull(tracker.session().averagePerHour(), "under a minute");
		long t = now + 10 * MINUTE;
		ghost(12_000, t);
		for (int i = 1; i <= 1_200; i++) {
			tracker.tick(true, t + i * 50L);
		}
		ghost(12_030, t + 60 * SECOND);
		assertEquals(30 * 60.0, tracker.session().averagePerHour(), 1.0);
	}

	@Test
	void aSavedWindowOutsideOneToSixtyIsRepaired() {
		BestiaryConfig config = new BestiaryConfig();
		assertEquals(5, config.windowMinutes, "5 minutes by default");
		assertEquals(false, config.resetOnIslandChange, "OFF by default (R11)");
		assertEquals(false, config.normalize());
		config.windowMinutes = 0;
		assertEquals(true, config.normalize());
		assertEquals(1, config.windowMinutes);
		config.windowMinutes = 99;
		config.normalize();
		assertEquals(60, config.windowMinutes);
	}
}
