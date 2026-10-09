package com.k8bas.skyblockutility.skyblock

import com.k8bas.skyblockutility.location.IslandTracker
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/**
 * The profile service and per-profile store (T3.0d, REQ-GS-08, REQ-GS-09), for the odds, the bestiary and the
 * SkyBlock XP optimizer. It reads the tab `Profile:` widget and the profile chat lines (also ones another mod
 * hides, through the chat reader) only while one of its consumers is active (REQ-GS-16). The profile stays
 * known across islands and servers and becomes unknown on a disconnect. State goes to
 * `config/k8bas_skyblock_utility/profiles/<account>/<profile>/<consumer>.json`. Client thread only.
 */
object ProfileService {
	private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/skyblock")
	private const val RIFT = "The Rift"

	private class Subscriber(val active: BooleanSupplier)

	private val subscribers = CopyOnWriteArrayList<Subscriber>()
	private val listeners = CopyOnWriteArrayList<Consumer<Profile?>>()
	private val tracker = ProfileTracker { Minecraft.getInstance().user?.profileId }
	private val store by lazy {
		ProfileStore(FabricLoader.getInstance().configDir.resolve("k8bas_skyblock_utility").resolve("profiles"), { LOGGER.warn(it) })
	}

	/** Wants the profile while [active] is true (e.g. the consuming feature is enabled). */
	@JvmStatic
	fun subscribe(owner: String, active: BooleanSupplier) {
		subscribers.add(Subscriber(active))
	}

	/** The current profile, or null while it is unknown. */
	@JvmStatic
	fun current(): Profile? = tracker.current()

	/** Called with each new profile (null when it becomes unknown), on the client thread. */
	@JvmStatic
	fun onChange(listener: Consumer<Profile?>) {
		listeners.add(listener)
	}

	/** The consumer's saved state for the current profile, or null. */
	@JvmStatic
	fun read(consumer: String): String? = store.read(consumer)

	/** Saves the consumer's state for the current profile; nothing while the profile is unknown. */
	@JvmStatic
	fun write(consumer: String, content: String) = store.write(consumer, content)

	private fun wanted(): Boolean = subscribers.any { it.active.asBoolean }

	internal fun register() {
		val wanted = BooleanSupplier { wanted() }
		TabReader.subscribe("profile", wanted)
		TabReader.onChange { tab ->
			if (wanted()) {
				// Before the island is known the tab may be the Rift's, whose name reads backwards (EC-GS-10).
				val island = IslandTracker.getCurrentIsland()
				tracker.tab(tab, island == null || island == RIFT)
			}
		}
		ChatReader.subscribe("profile", wanted) { message ->
			if (!message.overlay) {
				tracker.chat(message.text)
			}
		}
		tracker.onChange { profile ->
			store.use(profile)
			for (listener in listeners) {
				try {
					listener.accept(profile)
				} catch (e: RuntimeException) {
					LOGGER.error("A profile listener failed", e)
				}
			}
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register { store.flush() }
	}

	/**
	 * Each client tick: with no consumer active the profile is not followed, so it becomes unknown rather than
	 * stay a profile the player may since have left.
	 */
	internal fun tick() {
		if (tracker.current() != null && !wanted()) {
			tracker.reset()
		}
	}

	/** Disconnected: the next server says which profile it is. */
	internal fun reset() {
		tracker.reset()
	}
}
