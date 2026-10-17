package com.k8bas.skyblockutility.ui.widget

import com.k8bas.skyblockutility.ui.render.MaskCache

/**
 * The colour wheel's texture (REQ-UI-31, REQ-UI-47, T3.19): one at a time, sized for the GUI scale and baked at full
 * brightness, as a lower brightness is drawn by tinting it (REQ-UI-48, T3.41). Another size, after a GUI scale change,
 * makes a new one before it is drawn and frees the old (EC-UI-18). If a texture cannot be made, it is freed and the wheel
 * falls back for the rest of the session, logged once; the logger adds what the fallback is.
 */
class WheelCache<T : Any>(private val textures: Textures<T>, private val logger: MaskCache.Logger) {
	/** Makes and frees the textures: the game's texture manager, or a test double. */
	interface Textures<T> {
		/** A new texture of size × size pixels at full brightness. */
		fun make(size: Int): T

		fun release(texture: T)
	}

	private var texture: T? = null
	private var size = 0

	/** Whether a texture could not be made; the wheel falls back for the rest of the session then. */
	var failed: Boolean = false
		private set

	/** For gametests (AC-UI-48): the size of the texture it holds, or 0 for none. */
	fun sizeForTests(): Int = if (texture == null) 0 else size

	/** For gametests (AC-UI-51): how many textures were baked since this cache was created. */
	var bakesForTests: Int = 0
		private set

	/** The texture for this size, made as needed; null once failed. */
	fun get(size: Int): T? {
		if (failed) {
			return null
		}
		val current = texture
		if (current != null && size == this.size) {
			return current
		}
		try {
			val made = textures.make(size)
			bakesForTests++
			texture = made
			this.size = size
			current?.let(textures::release)
			return made
		} catch (e: RuntimeException) {
			failed = true
			texture?.let(textures::release)
			texture = null
			logger.log("The colour wheel's texture could not be made", e)
			return null
		}
	}

	/** Frees the texture and forgets it, without counting as a failure; the next [get] makes a new one. */
	fun clear() {
		texture?.let(textures::release)
		texture = null
		size = 0
	}
}
