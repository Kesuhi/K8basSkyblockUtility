package com.k8bas.skyblockutility.ui.widget

import com.k8bas.skyblockutility.ui.render.MaskCache

/**
 * The colour wheel's texture (REQ-UI-31, REQ-UI-47, T3.19): one at a time, sized for the GUI scale. Another brightness
 * rebakes it in place; another size, after a GUI scale change, makes a new one before it is drawn and frees the old
 * (EC-UI-18). If a texture cannot be made or rebaked, it is freed and the wheel falls back for the rest of the session,
 * logged once; the logger adds what the fallback is.
 */
class WheelCache<T : Any>(private val textures: Textures<T>, private val logger: MaskCache.Logger) {
	/** Makes, rebakes and frees the textures: the game's texture manager, or a test double. */
	interface Textures<T> {
		/** A new texture of size × size pixels at this brightness. */
		fun make(size: Int, value: Float): T

		/** The texture rebaked at another brightness; its size stays. */
		fun refill(texture: T, value: Float)

		fun release(texture: T)
	}

	private var texture: T? = null
	private var size = 0
	private var value = -1f

	/** Whether a texture could not be made or rebaked; the wheel falls back for the rest of the session then. */
	var failed: Boolean = false
		private set

	/** The texture for this size and brightness, made or rebaked as needed; null once failed. */
	fun get(size: Int, value: Float): T? {
		if (failed) {
			return null
		}
		val current = texture
		try {
			if (current == null || size != this.size) {
				val made = textures.make(size, value)
				texture = made
				this.size = size
				this.value = value
				current?.let(textures::release)
				return made
			}
			// Any change: the colours round per channel, so even less than an 8-bit step can change some.
			if (value != this.value) {
				textures.refill(current, value)
				this.value = value
			}
			return current
		} catch (e: RuntimeException) {
			failed = true
			texture?.let(textures::release)
			texture = null
			logger.log("The colour wheel's texture could not be made or rebaked", e)
			return null
		}
	}

	/** Frees the texture and forgets it, without counting as a failure; the next [get] makes a new one. */
	fun clear() {
		texture?.let(textures::release)
		texture = null
		size = 0
		value = -1f
	}
}
