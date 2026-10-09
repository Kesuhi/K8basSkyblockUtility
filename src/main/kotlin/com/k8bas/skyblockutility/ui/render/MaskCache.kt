package com.k8bas.skyblockutility.ui.render

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

/**
 * The corner masks made so far (REQ-UI-18, T2.9d, R32), one per radius and thickness at the current GUI scale.
 * A scale change releases them all, so only the sizes in use are kept. If a mask cannot be made, the cache
 * stays failed for the session and every shape uses the fill-based corners, so one session never mixes both;
 * what was made before is still released at the next scale change.
 */
class MaskCache<V : Any>(private val baker: Baker<V>, private val releaser: Releaser<V>) {
	fun interface Baker<V> {
		fun bake(radius: Int, thickness: Int): V
	}

	fun interface Releaser<V> {
		fun release(value: V)
	}

	private val masks = Int2ObjectOpenHashMap<V>()
	private var scale = 0

	/** Why a mask could not be made, or null; once set, [get] returns null for good. */
	var failure: RuntimeException? = null
		private set

	val failed: Boolean
		get() = failure != null

	/** The mask for this radius and thickness (0 for a fill) at this GUI scale, made on first use; null once failed. */
	fun get(scale: Int, radius: Int, thickness: Int): V? {
		// The scale is checked first, so a failed cache still releases its masks at the next scale change; not at
		// once, since shapes drawn earlier in the frame may still use them. A new scale's first call is a frame's first.
		if (scale != this.scale) {
			clear()
			this.scale = scale
		}
		if (failed) {
			return null
		}
		val key = CornerPieces.key(radius, thickness)
		masks.get(key)?.let { return it }
		val mask = try {
			baker.bake(radius, thickness)
		} catch (e: RuntimeException) {
			failure = e
			return null
		}
		masks.put(key, mask)
		return mask
	}

	/** Releases every mask made so far. */
	fun clear() {
		masks.values.forEach(releaser::release)
		masks.clear()
	}
}
