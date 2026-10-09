package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-18 (T2.9d, R32): the corner masks are made once per size and released when the GUI scale changes. */
class MaskCacheTest {
	private final List<String> baked = new ArrayList<>();
	private final List<String> released = new ArrayList<>();
	private final MaskCache<String> cache = new MaskCache<>((radius, thickness) -> {
		String mask = radius + "/" + thickness + "#" + baked.size();
		baked.add(mask);
		return mask;
	}, released::add);

	@Test
	void sameKeyBakesOnce() {
		String first = cache.get(2, 12, 0);
		assertSame(first, cache.get(2, 12, 0));
		assertEquals(List.of("12/0#0"), baked);
	}

	@Test
	void fillAndRingKeysDiffer() {
		assertEquals("12/0#0", cache.get(2, 12, 0));
		assertEquals("12/2#1", cache.get(2, 12, 2));
		assertEquals("8/2#2", cache.get(2, 8, 2));
		assertEquals(3, baked.size());
	}

	@Test
	void scaleChangeReleasesEverything() {
		cache.get(2, 12, 0);
		cache.get(2, 12, 2);
		assertEquals("12/0#2", cache.get(3, 12, 0), "baked again for the new scale");
		assertEquals(List.of("12/0#0", "12/2#1"), released.stream().sorted().toList(), "each once");
		cache.get(3, 12, 0);
		assertEquals(2, released.size(), "the same scale releases nothing");
		cache.clear();
		assertEquals(List.of("12/0#2"), released.subList(2, released.size()));
		assertEquals("12/0#3", cache.get(3, 12, 0), "baked again after a clear");
	}

	@Test
	void aThrowingBakerDisablesTheCacheForGood() {
		int[] calls = {0};
		IllegalStateException cause = new IllegalStateException("no texture");
		MaskCache<String> failing = new MaskCache<>((radius, thickness) -> {
			calls[0]++;
			throw cause;
		}, released::add);
		assertNull(failing.get(2, 12, 0));
		assertTrue(failing.getFailed());
		assertSame(cause, failing.getFailure(), "kept for the log");
		assertNull(failing.get(2, 12, 0));
		assertNull(failing.get(3, 6, 1));
		assertEquals(1, calls[0], "no bake while failed");
	}

	@Test
	void aFailureReleasesWhatWasBakedAtTheNextScale() {
		boolean[] fail = {false};
		MaskCache<String> flaky = new MaskCache<>((radius, thickness) -> {
			if (fail[0]) {
				throw new IllegalStateException("no texture");
			}
			return radius + "/" + thickness;
		}, released::add);
		flaky.get(2, 12, 0);
		fail[0] = true;
		assertNull(flaky.get(2, 6, 0));
		assertNull(flaky.get(2, 12, 0), "failed: every shape falls back, so one session never mixes both looks");
		assertEquals(List.of(), released, "not at once: shapes drawn earlier in the frame may still use it");
		assertNull(flaky.get(3, 12, 0));
		assertEquals(List.of("12/0"), released, "released at the next scale, as when not failed");
		assertNull(flaky.get(4, 12, 0));
		assertEquals(List.of("12/0"), released, "each once");
	}
}
