package com.k8bas.skyblockutility.skyblock

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * The base of every game-state reader (T3.0a): it reads only what the server already shows, and only
 * while an enabled consumer wants it (REQ-GS-16). Each [tick], on the client thread, it takes the raw input
 * from [source] and parses it only when it changed, so parsing never runs more than once per tick
 * (REQ-GS-02); consumers get the immutable snapshot and hear each change on the client thread (REQ-GS-11).
 * [clear] forgets everything, for a disconnect, server switch or world change (REQ-GS-15). Nothing is
 * written to disk (REQ-GS-12).
 *
 * @param guard runs at every entry point; in production it asserts the client thread in dev builds
 */
class PassiveReader<R : Any, T : Any>(
	private val name: String,
	private val empty: T,
	private val source: Supplier<R?>,
	private val parser: Function<R, T>,
	private val guard: Runnable,
) {
	private class Subscriber(val owner: String, val active: BooleanSupplier)

	private val subscribers = CopyOnWriteArrayList<Subscriber>()
	private val listeners = CopyOnWriteArrayList<Consumer<T>>()
	private val parseFailureLogged = AtomicBoolean()

	@Volatile
	private var snapshot: T = empty
	private var lastRaw: R? = null

	@Volatile
	private var parses = 0

	/** A consumer that wants this reader while [active] is true, typically its feature's enabled state. */
	fun subscribe(owner: String, active: BooleanSupplier) {
		subscribers.add(Subscriber(owner, active))
	}

	/** Called on the client thread with each new snapshot, the empty one after a clear included. */
	fun onChange(listener: Consumer<T>) {
		listeners.add(listener)
	}

	/** The current snapshot, immutable and safe on any thread. */
	fun snapshot(): T = snapshot

	/** True while at least one subscriber is active. */
	fun isWanted(): Boolean = subscribers.any { it.active.asBoolean }

	/** How often the input was parsed (AC-GS-16 counts it). */
	fun parseCount(): Int = parses

	/** Reads and, if the input changed, parses; with no active subscriber it reads nothing and keeps nothing. */
	fun tick() {
		guard.run()
		if (!isWanted()) {
			drop()
			return
		}
		val raw = source.get()
		if (raw == null) {
			drop()
			return
		}
		if (raw == lastRaw) {
			return
		}
		lastRaw = raw
		parses++
		val parsed = try {
			parser.apply(raw)
		} catch (e: RuntimeException) {
			// Shown as no data until the input changes; never a half-parsed value.
			if (parseFailureLogged.compareAndSet(false, true)) {
				LOGGER.warn("The {} reader could not parse its input; it shows no data until the input changes", name, e)
			}
			empty
		}
		publish(parsed)
	}

	/** Forgets the input and the snapshot; the next tick reads afresh. */
	fun clear() {
		guard.run()
		drop()
	}

	private fun drop() {
		lastRaw = null
		publish(empty)
	}

	private fun publish(value: T) {
		if (value == snapshot) {
			return
		}
		snapshot = value
		for (listener in listeners) {
			try {
				listener.accept(value)
			} catch (e: RuntimeException) {
				LOGGER.error("A {} reader listener failed", name, e)
			}
		}
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/skyblock")
	}
}
