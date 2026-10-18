package com.k8bas.skyblockutility.module.bestiary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T5.2d, the module's use of the profile store [A] (REQ-BEST-14, AC-BEST-13): nothing is read or written while the profile
 * is unknown or its store not verified; a profile's values are read once when it becomes known, and again after a switch;
 * changed values are written at most every 2 s; profile B's file never gets profile A's values, and a file this version
 * cannot read is never overwritten.
 */
class BestiaryProfileStoreTest {
	private final Map<String, String> files = new HashMap<>();
	private final AtomicReference<String> profile = new AtomicReference<>();
	private final AtomicBoolean storable = new AtomicBoolean(false);
	private final List<String> reads = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();
	private final BestiaryProfileStore store = new BestiaryProfileStore(
			() -> {
				reads.add(profile.get());
				return files.get(profile.get());
			},
			content -> files.put(profile.get(), content),
			storable::get, profile::get, warnings::add);
	private final BestiaryTracker tracker = new BestiaryTracker(BestiaryTables.shipped(), line -> { });

	private void ghost(String kills, long now) {
		tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 15: " + kills + "/12,500")), "Dwarven Mines", now);
	}

	@Test
	void nothingIsReadOrWrittenWhileTheProfileIsUnknown() {
		ghost("12,449", 0);
		for (long now = 0; now <= 60_000; now += 50) {
			store.tick(tracker, now);
		}
		profile.set("apple");
		for (long now = 60_000; now <= 120_000; now += 50) {
			store.tick(tracker, now);
		}
		assertEquals(List.of(), reads, "known but not verified is not storable either");
		assertTrue(files.isEmpty(), files.toString());
	}

	@Test
	void aProfileIsReadOnceAndWrittenAtMostEveryTwoSeconds() {
		files.put("apple", stored("12,400", 500));
		profile.set("apple");
		storable.set(true);
		store.tick(tracker, 1_000);
		store.tick(tracker, 1_050);
		assertEquals(List.of("apple"), reads, "read once");
		assertNotNull(tracker.family("ghost"), "its values show");
		String before = files.get("apple");
		ghost("12,449", 2_000);
		store.tick(tracker, 2_000);
		assertEquals(before, files.get("apple"), "not within 2 s of the read");
		store.tick(tracker, 4_000);
		assertTrue(files.get("apple").contains("12449"), files.get("apple"));
		// Unchanged values are not written again.
		files.put("apple", "untouched");
		store.tick(tracker, 30_000);
		assertEquals("untouched", files.get("apple"));
	}

	@Test
	void anotherProfilesFileNeverGetsTheFirstOnesValues() {
		profile.set("apple");
		storable.set(true);
		tracker.profile("account", "apple", null);
		store.tick(tracker, 0);
		ghost("12,449", 1_000);
		store.tick(tracker, 3_000);
		assertTrue(files.get("apple").contains("12449"));
		// The switch: the tracker forgets apple's values, the store reads banana's (none).
		profile.set("banana");
		tracker.profile("account", "banana", null);
		store.tick(tracker, 12_000);
		assertEquals(List.of("apple", "banana"), reads);
		for (long now = 12_000; now <= 40_000; now += 1_000) {
			store.tick(tracker, now);
		}
		String banana = files.get("banana");
		assertFalse(banana != null && banana.contains("12449"), banana);
		assertNull(tracker.family("ghost"));
	}

	@Test
	void aProfileLeftAndEnteredAgainIsReadAgain() {
		// Read while ON; the tracker forgets the profile twice (a switch away and back) before the store ticks again.
		files.put("apple", stored("12,400", 500));
		profile.set("apple");
		storable.set(true);
		tracker.profile("account", "apple", null);
		store.tick(tracker, 1_000);
		tracker.profile("account", "banana", null);
		tracker.profile("account", "apple", null);
		store.tick(tracker, 2_000);
		assertEquals(List.of("apple", "apple"), reads);
		assertNotNull(tracker.family("ghost"), "apple's values are back, so its file is not cut down to what follows");
	}

	@Test
	void aFileThisVersionCannotReadIsNeverOverwritten() {
		files.put("apple", "{\"version\": 2, \"families\": \"newer\"}");
		profile.set("apple");
		storable.set(true);
		store.tick(tracker, 0);
		ghost("12,449", 1_000);
		for (long now = 1_000; now <= 30_000; now += 1_000) {
			store.tick(tracker, now);
		}
		assertEquals("{\"version\": 2, \"families\": \"newer\"}", files.get("apple"));
		assertEquals(1, warnings.size(), warnings.toString());
	}

	@Test
	void valuesSeenBeforeTheReadAreWritten() {
		ghost("12,449", 0);
		profile.set("apple");
		storable.set(true);
		store.tick(tracker, 1_000);
		store.tick(tracker, 4_000);
		assertNotNull(files.get("apple"));
		assertTrue(files.get("apple").contains("12449"), files.get("apple"));
	}

	@Test
	void withoutTheFamilyTableTheFileIsLeftAlone() {
		// G5: a tracker whose table failed to load cannot place the stored rows, so writing would drop them.
		String before = stored("12,400", 500);
		files.put("apple", before);
		profile.set("apple");
		storable.set(true);
		BestiaryTracker bare = new BestiaryTracker(null, line -> { });
		for (long now = 1_000; now <= 30_000; now += 50) {
			if (now % 1_000 == 0) {
				bare.widget(List.of(BestiaryWidget.parseLine("Ghost 15: " + String.format(java.util.Locale.ROOT, "%,d", 12_400 + now / 1_000) + "/12,500")), "Dwarven Mines", now);
			}
			store.tick(bare, now);
		}
		assertEquals(before, files.get("apple"));
		assertEquals(List.of(), reads, "nothing to place the values in");
	}

	private String stored(String kills, long at) {
		BestiaryTracker other = new BestiaryTracker(BestiaryTables.shipped(), line -> { });
		other.widget(List.of(BestiaryWidget.parseLine("Ghost 15: " + kills + "/12,500")), "Dwarven Mines", at);
		return other.snapshot();
	}
}
