package com.k8bas.skyblockutility.data;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Layered values (T3.0k): REQ-DATA-10 / AC-DATA-08, bundled &lt; live (only with an opt-in feed) &lt; user
 * override, reporting the winning layer and its timestamp; a failed live layer falls back to the bundled
 * value silently and never replaces it (REQ-DATA-01).
 */
class LayeredValuesTest {
	private static final Instant BUNDLED_AT = Instant.parse("2026-10-01T12:00:00Z");
	private static final Instant LIVE_AT = Instant.parse("2026-10-09T10:00:00Z");
	private static final Instant OVERRIDE_AT = Instant.parse("2026-10-09T11:00:00Z");

	private final LayeredValues<String, Integer> prices = new LayeredValues<>(Map.of("COAL", 10, "IRON", 20)::get, BUNDLED_AT);

	/** A live feed with fixed values, or one that fails. */
	private static LayeredValues.LiveFeed<String, Integer> feed(Map<String, Integer> values, boolean fails) {
		return new LayeredValues.LiveFeed<>() {
			@Override
			public Integer value(String key) {
				if (fails) {
					throw new IllegalStateException("Bazaar unreachable");
				}
				return values.get(key);
			}

			@Override
			public Instant fetchedAt() {
				return LIVE_AT;
			}
		};
	}

	@Test
	void theOverrideWinsThenLiveThenBundled() {
		// AC-DATA-08: bundled 10, live 12, override 15.
		prices.useLive(feed(Map.of("COAL", 12), false));
		prices.setOverride("COAL", 15, OVERRIDE_AT);
		assertEquals(new LayeredValues.Resolved<>(15, LayeredValues.Layer.OVERRIDE, OVERRIDE_AT), prices.get("COAL"));
		prices.removeOverride("COAL");
		assertEquals(new LayeredValues.Resolved<>(12, LayeredValues.Layer.LIVE, LIVE_AT), prices.get("COAL"));
		prices.useLive(feed(Map.of(), true));
		assertEquals(new LayeredValues.Resolved<>(10, LayeredValues.Layer.BUNDLED, BUNDLED_AT), prices.get("COAL"),
				"a failed live layer falls back to bundled, with no error");
	}

	@Test
	void withoutAnOptInFeedThereIsNoLiveLayer() {
		assertEquals(LayeredValues.Layer.BUNDLED, prices.get("COAL").getLayer());
		prices.useLive(feed(Map.of("COAL", 12), false));
		assertEquals(12, prices.get("COAL").getValue());
		prices.stopLive();
		assertEquals(10, prices.get("COAL").getValue(), "turning the feed off returns to the bundled value");
	}

	@Test
	void aLiveFeedWithoutTheKeyFallsBackToBundled() {
		prices.useLive(feed(Map.of("COAL", 12), false));
		assertEquals(new LayeredValues.Resolved<>(20, LayeredValues.Layer.BUNDLED, BUNDLED_AT), prices.get("IRON"));
	}

	@Test
	void theLiveLayerNeverChangesTheBundledValues() {
		prices.useLive(feed(Map.of("COAL", 12), false));
		prices.get("COAL");
		assertEquals(10, prices.bundled("COAL"));
	}

	@Test
	void aKeyNoLayerKnowsHasNoValue() {
		assertNull(prices.get("GOLD"));
		prices.setOverride("GOLD", 99, OVERRIDE_AT);
		assertEquals(new LayeredValues.Resolved<>(99, LayeredValues.Layer.OVERRIDE, OVERRIDE_AT), prices.get("GOLD"), "an override alone is enough");
	}

	@Test
	void aCheckedExceptionFromTheFeedFallsBackToo() {
		// A Kotlin feed can throw a checked exception (e.g. reading a deleted cache file) without declaring it.
		prices.useLive(new LayeredValues.LiveFeed<>() {
			@Override
			public Integer value(String key) {
				return LayeredValuesTest.<RuntimeException, Integer>sneaky(new java.io.IOException("cache file gone"));
			}

			@Override
			public Instant fetchedAt() {
				return LIVE_AT;
			}
		});
		assertEquals(new LayeredValues.Resolved<>(10, LayeredValues.Layer.BUNDLED, BUNDLED_AT), prices.get("COAL"));
		assertEquals(1, prices.liveFailures());
	}

	@SuppressWarnings("unchecked")
	private static <E extends Throwable, R> R sneaky(Throwable checked) throws E {
		throw (E) checked;
	}

	@Test
	void liveFailuresAreCountedNotShown() {
		prices.useLive(feed(Map.of(), true));
		prices.get("COAL");
		prices.get("IRON");
		assertEquals(2, prices.liveFailures());
	}
}
