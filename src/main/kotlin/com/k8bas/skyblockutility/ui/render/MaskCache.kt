package com.k8bas.skyblockutility.ui.render

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

/**
 * The masks made so far (REQ-UI-18, T2.9d, R32; REQ-UI-47, T3.18), one per [MaskKey] at the current GUI scale, at most
 * [limit] of them ([MAX_MASKS] for masks). A scale change releases them all, so only the sizes in use are kept. A mask beyond the bound is not
 * made: its shape is drawn from fill spans while the masks already made stay in use (REQ-UI-47). If a mask cannot be made,
 * the cache stays failed for the session and every shape uses the fill spans from then on; what was made before is still
 * released at the next scale change. Each of the two is logged once per session. The icon textures use one too, with
 * their own bound ([limit]) and names in the log (T3.20).
 */
class MaskCache<V : Any> @JvmOverloads constructor(
	private val baker: Baker<V>,
	private val releaser: Releaser<V>,
	private val logger: Logger,
	/** The most kept at one GUI scale. */
	val limit: Int = MAX_MASKS,
	/** What is cached, for the log: "mask" or "icon". */
	private val kind: String = "mask",
	/** What is drawn from fill spans once a texture cannot be made, for the log. */
	private val shapes: String = "discs, pills and smooth corners",
) {
	fun interface Baker<V> {
		fun bake(key: Int): V
	}

	fun interface Releaser<V> {
		fun release(value: V)
	}

	fun interface Logger {
		fun log(message: String, cause: Throwable?)
	}

	companion object {
		/** The most masks kept at one GUI scale: corners, discs and shadows together (REQ-UI-47). */
		const val MAX_MASKS = 64
	}

	private val masks = Int2ObjectOpenHashMap<V>()
	/** The keys asked for since the GUI scale last changed, for gametests (AC-UI-48): a mask not among them is another scale's. */
	private val askedAtScale = IntOpenHashSet()
	private var scale = 0
	private var overflowLogged = false

	/** Why a texture could not be made, or null; once set, [get] returns null for good. */
	var failure: RuntimeException? = null
		private set

	val failed: Boolean
		get() = failure != null

	/** The mask for this key at this GUI scale, made on first use; null beyond [limit] and once failed. */
	fun get(scale: Int, key: Int): V? {
		// The scale is checked first, so a failed cache still releases its masks at the next scale change; not at
		// once, since shapes drawn earlier in the frame may still use them. A new scale's first call is a frame's first.
		if (scale != this.scale) {
			clear()
			this.scale = scale
			askedAtScale.clear()
		}
		askedAtScale.add(key)
		if (failed) {
			return null
		}
		masks.get(key)?.let { return it }
		if (masks.size >= limit) {
			if (!overflowLogged) {
				overflowLogged = true
				logger.log("More than $limit $kind textures at one GUI scale: the shapes beyond them are drawn from fill spans (a shadow is left out)", null)
			}
			return null
		}
		val mask = try {
			baker.bake(key)
		} catch (e: RuntimeException) {
			failure = e
			logger.log("Could not make one of the $kind textures: $shapes are drawn from fill spans for this session", e)
			return null
		}
		masks.put(key, mask)
		return mask
	}

	/** For gametests (AC-UI-48): the GUI scale its masks are for, and how many it holds. */
	fun scaleForTests(): Int = scale

	fun sizeForTests(): Int = masks.size

	/** For gametests (AC-UI-48): how many masks it holds that were not asked for at its current scale (left from another). */
	fun staleForTests(): Int {
		var stale = 0
		val keys = masks.keys.iterator()
		while (keys.hasNext()) {
			if (!askedAtScale.contains(keys.nextInt())) {
				stale++
			}
		}
		return stale
	}

	/** Releases every mask made so far. */
	fun clear() {
		masks.values.forEach(releaser::release)
		masks.clear()
	}
}
