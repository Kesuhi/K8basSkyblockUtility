package com.k8bas.skyblockutility.data

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * Layered values over a bundled table (T3.0k, REQ-DATA-10): a user override wins over a live value, which wins
 * over the bundled one, and each answer says which layer won and when that layer's value was made. A live
 * layer exists only while its consumer has turned on an opt-in feed ([useLive], e.g. the SBXP live Bazaar
 * prices, D-22); a feed that fails or has no value for a key falls back to the bundled value silently. The
 * bundled values are only read, never replaced or deleted (REQ-DATA-01). Overrides live in memory here; the
 * consumer keeps them where they belong (per profile, T6.5c). Safe on any thread.
 *
 * @param bundled the bundled value of a key, or null
 * @param bundledAt when the bundled values were made (the table's generatedAt)
 */
class LayeredValues<K : Any, V : Any>(private val bundled: Function<K, V?>, private val bundledAt: Instant?) {
	enum class Layer { BUNDLED, LIVE, OVERRIDE }

	/** A value, the layer it came from, and that layer's timestamp. */
	data class Resolved<V>(val value: V, val layer: Layer, val at: Instant?)

	/** An opt-in live feed; [value] may throw or return null, which falls back to the bundled value. */
	interface LiveFeed<K, V> {
		fun value(key: K): V?

		/** When the feed's values were fetched. */
		fun fetchedAt(): Instant?
	}

	private class Stamped<V>(val value: V, val at: Instant)

	@Volatile
	private var live: LiveFeed<K, V>? = null
	private val overrides = ConcurrentHashMap<K, Stamped<V>>()
	private val failures = AtomicInteger()

	/** Turns the live layer on with [feed]. */
	fun useLive(feed: LiveFeed<K, V>) {
		live = feed
	}

	/** Turns the live layer off; the bundled values answer again. */
	fun stopLive() {
		live = null
	}

	fun setOverride(key: K, value: V, at: Instant) {
		overrides[key] = Stamped(value, at)
	}

	fun removeOverride(key: K) {
		overrides.remove(key)
	}

	/** The winning value of [key], or null if no layer has one. */
	fun get(key: K): Resolved<V>? {
		overrides[key]?.let { return Resolved(it.value, Layer.OVERRIDE, it.at) }
		live?.let { feed ->
			val value = try {
				feed.value(key)
			} catch (e: Exception) {
				// No user-visible error: the bundled value answers (AC-DATA-08). Exception, not only RuntimeException:
				// a Kotlin feed may throw a checked one (an IOException reading its cache) without declaring it.
				failures.incrementAndGet()
				null
			}
			if (value != null) {
				return Resolved(value, Layer.LIVE, feed.fetchedAt())
			}
		}
		return bundled.apply(key)?.let { Resolved(it, Layer.BUNDLED, bundledAt) }
	}

	/** The bundled value alone, whatever the other layers say. */
	fun bundled(key: K): V? = bundled.apply(key)

	/** How often the live layer failed, for diagnostics. */
	fun liveFailures(): Int = failures.get()
}
