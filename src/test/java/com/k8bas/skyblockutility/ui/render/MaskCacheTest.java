package com.k8bas.skyblockutility.ui.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-UI-18 (T2.9d, R32) and REQ-UI-47 (T3.18): the masks are made once per key and released when the GUI scale
 * changes (EC-UI-18); at most {@link MaskCache#MAX_MASKS} per scale, and a mask beyond them or one that cannot be made
 * falls back to fill spans with one line logged (AC-UI-48).
 */
class MaskCacheTest {
	private final List<String> baked = new ArrayList<>();
	private final List<String> released = new ArrayList<>();
	private final List<String> logged = new ArrayList<>();
	private final MaskCache<String> cache = new MaskCache<>(key -> {
		String mask = key + "#" + baked.size();
		baked.add(mask);
		return mask;
	}, released::add, (message, cause) -> logged.add(message));

	@Test
	void sameKeyBakesOnce() {
		String first = cache.get(2, MaskKey.corner(12, 0));
		assertSame(first, cache.get(2, MaskKey.corner(12, 0)));
		assertEquals(1, baked.size());
	}

	/** Each key bakes its own mask, and with all of them made each key still gets its own back. */
	@Test
	void fillRingAndDiscKeysDiffer() {
		int[] keys = {MaskKey.corner(12, 0), MaskKey.corner(12, 2), MaskKey.corner(8, 2), MaskKey.disc(24), MaskKey.disc(23)};
		List<String> made = new ArrayList<>();
		for (int key : keys) {
			made.add(cache.get(2, key));
		}
		assertEquals(List.of(keys[0] + "#0", keys[1] + "#1", keys[2] + "#2", keys[3] + "#3", keys[4] + "#4"), made, "each bake gets its key");
		for (int i = keys.length - 1; i >= 0; i--) {
			assertSame(made.get(i), cache.get(2, keys[i]), "key " + keys[i] + " gets its own mask back");
		}
		assertEquals(5, baked.size());
	}

	@Test
	void keysAreDistinctAndDecode() {
		Set<Integer> keys = new HashSet<>();
		for (int r = 1; r <= CornerMask.MAX_RADIUS; r++) {
			for (int t = 0; t <= r; t++) {
				int key = MaskKey.corner(r, t);
				assertTrue(keys.add(key), r + "/" + t);
				assertFalse(MaskKey.isDisc(key));
				assertEquals(r, MaskKey.radius(key));
				assertEquals(t, MaskKey.thickness(key));
			}
		}
		for (int d = 1; d <= 2 * CornerMask.MAX_RADIUS; d++) {
			int key = MaskKey.disc(d);
			assertTrue(keys.add(key), "diameter " + d);
			assertTrue(MaskKey.isDisc(key));
			assertEquals(d, MaskKey.diameter(key));
		}
	}

	@Test
	void scaleChangeReleasesEverything() {
		cache.get(2, MaskKey.corner(12, 0));
		cache.get(2, MaskKey.corner(12, 2));
		cache.get(3, MaskKey.corner(12, 0));
		assertEquals(3, baked.size(), "baked again for the new scale");
		assertEquals(List.of(baked.get(0), baked.get(1)), released.stream().sorted().toList(), "each once");
		cache.get(3, MaskKey.corner(12, 0));
		assertEquals(2, released.size(), "the same scale releases nothing");
		cache.clear();
		assertEquals(List.of(baked.get(2)), released.subList(2, released.size()));
		cache.get(3, MaskKey.corner(12, 0));
		assertEquals(4, baked.size(), "baked again after a clear");
	}

	/** EC-UI-18: a disc mask too is made again for the new scale before a frame draws with it, and the old one is freed. */
	@Test
	void discMasksAreRebakedAtANewScale() {
		String atTwo = cache.get(2, MaskKey.disc(20));
		String atThree = cache.get(3, MaskKey.disc(20));
		assertEquals(2, baked.size(), "the same key is made again at the new scale");
		assertEquals(List.of(atTwo), released, "the old scale's mask is freed");
		assertSame(atThree, cache.get(3, MaskKey.disc(20)), "and the new one kept");
	}

	/**
	 * AC-UI-48 [A]: 65 distinct masks at one scale: the 65th is not made, so its shape is drawn from fill spans, and one line
	 * is logged. The 64 made stay in use, the cache has not failed, and a new scale starts a new count.
	 */
	@Test
	void theSixtyFifthMaskAtOneScaleIsNotMade() {
		for (int d = 1; d <= MaskCache.MAX_MASKS; d++) {
			assertNotNull(cache.get(2, MaskKey.disc(d)), "mask " + d);
		}
		assertEquals(64, MaskCache.MAX_MASKS);
		assertNull(cache.get(2, MaskKey.disc(65)), "the 65th: drawn from fill spans");
		assertEquals(64, baked.size(), "no 65th texture is made");
		assertEquals(1, logged.size(), "one line is logged: " + logged);
		assertFalse(cache.getFailed(), "not a failure: the other shapes keep their masks");
		assertNotNull(cache.get(2, MaskKey.disc(1)), "a mask already made is still served");
		assertNull(cache.get(2, MaskKey.corner(5, 0)));
		assertEquals(64, baked.size());
		assertEquals(1, logged.size(), "logged once per session");
		assertNotNull(cache.get(3, MaskKey.disc(65)), "a new scale counts from zero");
		assertEquals(65, baked.size());
		for (int d = 66; d <= 64 + 65; d++) {
			cache.get(3, MaskKey.disc(d));
		}
		assertEquals(64 + 64, baked.size(), "64 at the new scale");
		assertEquals(1, logged.size(), "still once per session");
	}

	/** AC-UI-48 [A]: with texture creation failing, every shape falls back and one line is logged, with the cause. */
	@Test
	void aThrowingBakerDisablesTheCacheForGood() {
		int[] calls = {0};
		IllegalStateException cause = new IllegalStateException("no texture");
		List<Throwable> causes = new ArrayList<>();
		MaskCache<String> failing = new MaskCache<>(key -> {
			calls[0]++;
			throw cause;
		}, released::add, (message, thrown) -> {
			logged.add(message);
			causes.add(thrown);
		});
		assertNull(failing.get(2, MaskKey.corner(12, 0)));
		assertTrue(failing.getFailed());
		assertSame(cause, failing.getFailure(), "the cause is kept");
		assertNull(failing.get(2, MaskKey.corner(12, 0)));
		assertNull(failing.get(3, MaskKey.corner(6, 1)));
		assertNull(failing.get(4, MaskKey.disc(9)));
		assertEquals(1, calls[0], "no bake while failed");
		assertEquals(1, logged.size(), "one line is logged: " + logged);
		assertEquals(List.of(cause), causes, "with the cause");
	}

	@Test
	void aFailureReleasesWhatWasBakedAtTheNextScale() {
		boolean[] fail = {false};
		MaskCache<String> flaky = new MaskCache<>(key -> {
			if (fail[0]) {
				throw new IllegalStateException("no texture");
			}
			return "mask " + key;
		}, released::add, (message, cause) -> logged.add(message));
		String made = flaky.get(2, MaskKey.corner(12, 0));
		fail[0] = true;
		assertNull(flaky.get(2, MaskKey.corner(6, 0)));
		assertNull(flaky.get(2, MaskKey.corner(12, 0)), "failed: every shape falls back, so one session never mixes both looks");
		assertEquals(List.of(), released, "not at once: shapes drawn earlier in the frame may still use it");
		assertNull(flaky.get(3, MaskKey.corner(12, 0)));
		assertEquals(List.of(made), released, "released at the next scale, as when not failed");
		assertNull(flaky.get(4, MaskKey.corner(12, 0)));
		assertEquals(List.of(made), released, "each once");
		assertEquals(1, logged.size());
	}

	/**
	 * REQ-UI-47 (T3.20): the icon cache, as IconTextures builds it, holds at most 16 icons per scale; the 17th is drawn from
	 * fill spans, logged once.
	 */
	@Test
	void theIconCacheHoldsSixteenPerScale() {
		assertEquals(16, IconTextures.MAX_ICONS);
		MaskCache<String> icons = IconTextures.cache(key -> "icon " + key, released::add, (message, cause) -> logged.add(message));
		assertEquals(16, icons.getLimit());
		for (int key = 0; key < 16; key++) {
			assertNotNull(icons.get(2, key), "icon " + key);
		}
		assertNull(icons.get(2, 16), "the 17th");
		assertNull(icons.get(2, 17));
		assertEquals(1, logged.size(), "once: " + logged);
		assertTrue(logged.get(0).contains("16 icon textures"), logged.get(0));
		assertNotNull(icons.get(3, 16), "a new scale counts from zero");
	}

	/** Every icon at every size the UI can ask for (up to 64 GUI px at scale 6) has its own key, and the key gives both back. */
	@Test
	void iconKeysAreDistinctAndDecode() {
		Set<Integer> keys = new HashSet<>();
		for (IconShapes.Icon icon : IconShapes.Icon.values()) {
			for (int px = 1; px <= 64 * 6; px++) {
				int key = IconTextures.key(icon, px);
				assertTrue(keys.add(key), icon + " at " + px);
				assertEquals(icon.ordinal(), key >> 16);
				assertEquals(px, key & 0xFFFF);
			}
		}
	}
}
