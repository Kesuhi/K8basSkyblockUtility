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
 * REQ-UI-31 and REQ-UI-47 (T3.19): one wheel texture at a time, sized for the GUI scale. Another brightness rebakes it in
 * place; another size (a new GUI scale) makes a new one and frees the old (EC-UI-18). If a texture cannot be made, the
 * wheel falls back to the 1.2.1 wheel for the session, logged once.
 */
class WheelCacheTest {
	private final List<String> events = new ArrayList<>();
	private final List<String> logged = new ArrayList<>();
	private final List<Throwable> causes = new ArrayList<>();
	private int failMakeAt = -1;
	private boolean failRefill;
	private final WheelCache<String> cache = new WheelCache<>(new WheelCache.Textures<>() {
		private int made;

		@Override
		public String make(int size, float value) {
			if (size == failMakeAt) {
				throw new IllegalStateException("no texture");
			}
			events.add("make " + size + " at " + value);
			return "wheel " + made++ + " (" + size + ")";
		}

		@Override
		public void refill(String texture, float value) {
			if (failRefill) {
				throw new IllegalStateException("no upload");
			}
			events.add("refill " + texture + " at " + value);
		}

		@Override
		public void release(String texture) {
			events.add("release " + texture);
		}
	}, (message, cause) -> {
		logged.add(message);
		causes.add(cause);
	});

	@Test
	void theSameSizeAndBrightnessBakeOnce() {
		String first = cache.get(160, 1F);
		assertSame(first, cache.get(160, 1F));
		assertEquals(List.of("make 160 at 1.0"), events);
	}

	/**
	 * Any brightness change rebakes the texture in place, also one smaller than an 8-bit step: the colours round per channel,
	 * and a hex step (1/255 as a float) can come out a hair below 1/255.
	 */
	@Test
	void anotherBrightnessRefillsTheSameTexture() {
		String wheel = cache.get(160, 1F);
		// #FFFFFF to #FEFEFE, the brightness as Hsv.fromRgb gives it: 1 - 254/255 comes out below 1/255 as a float.
		float hexStep = (float) (254 / 255.0);
		assertTrue(1F - hexStep < 1F / 255F, "the case a 1/255 threshold skipped");
		assertSame(wheel, cache.get(160, hexStep));
		assertSame(wheel, cache.get(160, 0.5F));
		assertSame(wheel, cache.get(160, 0.5F));
		assertSame(wheel, cache.get(160, 0.5F + 0.001F));
		assertEquals(List.of("make 160 at 1.0", "refill " + wheel + " at " + hexStep, "refill " + wheel + " at 0.5",
				"refill " + wheel + " at " + (0.5F + 0.001F)), events);
	}

	/** A cleared cache frees its texture once and makes a new one on the next draw; it has not failed. */
	@Test
	void clearFreesTheTextureWithoutFailing() {
		String first = cache.get(160, 1F);
		cache.clear();
		cache.clear();
		String again = cache.get(160, 1F);
		assertFalse(first.equals(again));
		assertEquals(List.of("make 160 at 1.0", "release " + first, "make 160 at 1.0"), events);
		assertFalse(cache.getFailed());
		assertTrue(logged.isEmpty());
	}

	/** A scale change during a brightness drag bakes the new texture at the new brightness. */
	@Test
	void aNewScaleAndBrightnessTogetherBakeAtTheNewBrightness() {
		String atTwo = cache.get(160, 1F);
		String atThree = cache.get(240, 0.5F);
		assertEquals(List.of("make 160 at 1.0", "make 240 at 0.5", "release " + atTwo), events);
		assertSame(atThree, cache.get(240, 0.5F));
		assertEquals(3, events.size(), "the new texture already has this brightness");
	}

	/** EC-UI-18: a new size makes a new texture before it is drawn and frees the old one, so at most one is kept. */
	@Test
	void aNewScaleMakesANewTextureAndFreesTheOld() {
		String atTwo = cache.get(160, 1F);
		String atThree = cache.get(240, 1F);
		assertFalse(atTwo.equals(atThree));
		assertEquals(List.of("make 160 at 1.0", "make 240 at 1.0", "release " + atTwo), events);
		assertSame(atThree, cache.get(240, 1F));
		assertEquals(3, events.size(), "nothing more at the same scale");
		assertTrue(logged.isEmpty());
	}

	/** REQ-UI-47: a texture that cannot be made falls back to the 1.2.1 wheel for the session, logged once with the cause. */
	@Test
	void aTextureThatCannotBeMadeFallsBackForTheSession() {
		String atTwo = cache.get(160, 1F);
		failMakeAt = 240;
		assertNull(cache.get(240, 1F));
		assertTrue(cache.getFailed());
		assertEquals(List.of("make 160 at 1.0", "release " + atTwo), events, "the old scale's texture is freed, nothing is kept");
		failMakeAt = -1;
		assertNull(cache.get(160, 1F), "no new attempt this session");
		assertNull(cache.get(320, 0.5F));
		assertEquals(2, events.size());
		assertEquals(1, logged.size(), "one line: " + logged);
		assertTrue(causes.get(0) instanceof IllegalStateException, "with the cause");
	}

	@Test
	void aRebakeThatFailsFallsBackToo() {
		String wheel = cache.get(160, 1F);
		failRefill = true;
		assertNull(cache.get(160, 0.5F));
		assertEquals(List.of("make 160 at 1.0", "release " + wheel), events);
		failRefill = false;
		assertNull(cache.get(160, 1F));
		assertEquals(1, logged.size(), "one line: " + logged);
	}
}
