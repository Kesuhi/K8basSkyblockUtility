package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-UI-31 and REQ-UI-47 (T3.19): one wheel texture at a time, sized for the GUI scale. Another size (a new GUI scale) makes
 * a new one and frees the old (EC-UI-18). If a texture cannot be made, the wheel falls back to the 1.2.1 wheel for the
 * session, logged once. REQ-UI-48 (T3.41): it is baked at full brightness and has no brightness of its own, so a brightness
 * drag bakes nothing; the drawn tint makes the brightness (WheelImageTest).
 */
class WheelCacheTest {
	private final List<String> events = new ArrayList<>();
	private final List<String> logged = new ArrayList<>();
	private final List<Throwable> causes = new ArrayList<>();
	private int failMakeAt = -1;
	private final WheelCache<String> cache = new WheelCache<>(new WheelCache.Textures<>() {
		private int made;

		@Override
		public String make(int size) {
			if (size == failMakeAt) {
				throw new IllegalStateException("no texture");
			}
			events.add("make " + size);
			return "wheel " + made++ + " (" + size + ")";
		}

		@Override
		public void release(String texture) {
			events.add("release " + texture);
		}
	}, (message, cause) -> {
		logged.add(message);
		causes.add(cause);
	});

	/** The same size bakes once, however often it is drawn: a brightness drag draws it many times and bakes nothing. */
	@Test
	void theSameSizeBakesOnce() {
		String first = cache.get(160);
		for (int frame = 0; frame < 60; frame++) {
			assertSame(first, cache.get(160));
		}
		assertEquals(List.of("make 160"), events);
		assertEquals(1, cache.getBakesForTests());
	}

	/** A cleared cache frees its texture once and makes a new one on the next draw; it has not failed. */
	@Test
	void clearFreesTheTextureWithoutFailing() {
		String first = cache.get(160);
		cache.clear();
		cache.clear();
		String again = cache.get(160);
		assertFalse(first.equals(again));
		assertEquals(List.of("make 160", "release " + first, "make 160"), events);
		assertFalse(cache.getFailed());
		assertTrue(logged.isEmpty());
		assertEquals(2, cache.getBakesForTests());
	}

	/** EC-UI-18: a new size makes a new texture before it is drawn and frees the old one, so at most one is kept. */
	@Test
	void aNewScaleMakesANewTextureAndFreesTheOld() {
		String atTwo = cache.get(160);
		String atThree = cache.get(240);
		assertFalse(atTwo.equals(atThree));
		assertEquals(List.of("make 160", "make 240", "release " + atTwo), events);
		assertSame(atThree, cache.get(240));
		assertEquals(3, events.size(), "nothing more at the same scale");
		assertTrue(logged.isEmpty());
	}

	/** REQ-UI-47: a texture that cannot be made falls back to the 1.2.1 wheel for the session, logged once with the cause. */
	@Test
	void aTextureThatCannotBeMadeFallsBackForTheSession() {
		String atTwo = cache.get(160);
		failMakeAt = 240;
		assertNull(cache.get(240));
		assertTrue(cache.getFailed());
		assertEquals(List.of("make 160", "release " + atTwo), events, "the old scale's texture is freed, nothing is kept");
		failMakeAt = -1;
		assertNull(cache.get(160), "no new attempt this session");
		assertNull(cache.get(320));
		assertEquals(2, events.size());
		assertEquals(1, logged.size(), "one line: " + logged);
		assertTrue(causes.get(0) instanceof IllegalStateException, "with the cause");
	}
}
