package com.k8bas.skyblockutility.skyblock

import com.k8bas.skyblockutility.data.BundledData
import com.k8bas.skyblockutility.location.IslandTracker
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/**
 * The mineshaft state the mineshaft alert and the possible corpse spots read (T3.0m, REQ-GS-17); those features never
 * parse the shaft code or the corpse list themselves. It works only while one of its consumers is active
 * and the player is in a Glacite Mineshaft: only then are the tab and sidebar readers wanted, so with every
 * consumer off nothing is read or stored (REQ-GS-16). The code's display name comes from the bundled
 * `mining_shafts` table (CC0); until it has loaded, the code itself is shown. Client thread only.
 */
object MineshaftState {
	/** The island IslandTracker names for the inside of a mineshaft. */
	const val ISLAND = "Glacite Mineshafts"

	private class Subscriber(val active: BooleanSupplier)

	private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/skyblock")
	private val subscribers = CopyOnWriteArrayList<Subscriber>()
	private val listeners = CopyOnWriteArrayList<Consumer<MineshaftSnapshot>>()

	/** code -> display name, from the bundled table. */
	private val names = BundledData.table(MineshaftTable.SPEC)

	private val tracker = MineshaftTracker({ code -> names.value()?.get(code) }, { line -> ShapeLog.SHARED.once("corpse", line) })

	@Volatile
	private var snapshot = MineshaftSnapshot.NONE

	/** Wants the mineshaft state while [active] is true (e.g. the consuming feature is enabled). */
	@JvmStatic
	fun subscribe(owner: String, active: BooleanSupplier) {
		subscribers.add(Subscriber(active))
	}

	@JvmStatic
	fun snapshot(): MineshaftSnapshot = snapshot

	/** Called on the client thread with each new snapshot. */
	@JvmStatic
	fun onChange(listener: Consumer<MineshaftSnapshot>) {
		listeners.add(listener)
	}

	/** Dev only (`/ksu debug shaft`): pretend to be in a shaft with [code] and these corpses. */
	@JvmStatic
	fun simulate(code: String, corpses: List<String>) {
		tracker.simulate(code, corpses, System.currentTimeMillis())
		publish()
	}

	/** Dev only: end a simulated shaft. */
	@JvmStatic
	fun clearSimulation() {
		tracker.leave()
		publish()
	}

	private fun wanted(): Boolean = subscribers.any { it.active.asBoolean }

	private fun inShaft(): Boolean = IslandTracker.getCurrentIsland() == ISLAND

	internal fun register() {
		// The readers are wanted only for a shaft someone asks about.
		val reading = BooleanSupplier { wanted() && inShaft() }
		TabReader.subscribe("mineshaft", reading)
		SidebarReader.subscribe("mineshaft", reading)
		TabReader.onChange { tab ->
			if (reading.asBoolean) {
				tracker.tab(tab)
				publish()
			}
		}
		SidebarReader.onChange { sidebar ->
			if (reading.asBoolean) {
				tracker.sidebar(sidebar.lines, System.currentTimeMillis())
				publish()
			}
		}
	}

	/** Each client tick: enter or leave as the location and the consumers say, and end a search after 15 s. */
	internal fun tick() {
		val now = System.currentTimeMillis()
		val current = tracker.snapshot()
		if (!wanted()) {
			// A simulated shaft (dev) has no consumer to keep it either: nothing is stored while unwanted.
			if (current.inShaft) {
				tracker.leave()
			}
		} else if (inShaft() && !current.inShaft) {
			tracker.enter(now)
			TabReader.snapshot().takeIf { it != TabSnapshot.EMPTY }?.let { tracker.tab(it) }
			tracker.sidebar(SidebarReader.snapshot().lines, now)
		} else if (!inShaft() && current.inShaft && !tracker.isSimulated()) {
			// A simulated shaft (dev) is not ended by being outside a real one; a location change still ends it,
			// like any shaft (GameStateReaders clears on every change).
			tracker.leave()
		}
		tracker.tick(now)
		publish()
	}

	/** The disconnect and server-switch reset (REQ-GS-15). */
	internal fun clear() {
		tracker.leave()
		publish()
	}

	private fun publish() {
		val next = tracker.snapshot()
		if (next == snapshot) {
			return
		}
		snapshot = next
		// One failing consumer must not crash the tick or keep the others from the update.
		for (listener in listeners) {
			try {
				listener.accept(next)
			} catch (e: RuntimeException) {
				LOGGER.error("A mineshaft state listener failed", e)
			}
		}
	}
}
