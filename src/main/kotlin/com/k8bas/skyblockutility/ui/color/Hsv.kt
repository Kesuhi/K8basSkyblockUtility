package com.k8bas.skyblockutility.ui.color

import kotlin.math.abs

/**
 * A colour as hue, saturation and value, each 0..1 (hue wraps). The picker keeps this as its truth, so
 * moving brightness to 0 and back keeps the hue and saturation (REQ-UI-13, AC-UI-12).
 */
@JvmRecord
data class Hsv(val h: Float, val s: Float, val v: Float) {
	/** 0xRRGGBB, each channel rounded, so every 8-bit colour round-trips exactly. */
	fun toRgb(): Int {
		// Float arithmetic first, widened to double where the original did, so every channel rounds the same.
		val hue: Double = ((h % 1f) + 1f) % 1f * 6.0
		val c: Double = (v * s).toDouble()
		val x: Double = c * (1 - abs(hue % 2 - 1))
		val m: Double = v - c
		val r: Double
		val g: Double
		val b: Double
		when (hue.toInt()) {
			0 -> { r = c; g = x; b = 0.0 }
			1 -> { r = x; g = c; b = 0.0 }
			2 -> { r = 0.0; g = c; b = x }
			3 -> { r = 0.0; g = x; b = c }
			4 -> { r = x; g = 0.0; b = c }
			else -> { r = c; g = 0.0; b = x }
		}
		return channel(r + m) shl 16 or (channel(g + m) shl 8) or channel(b + m)
	}

	override fun toString(): String = "Hsv[h=$h, s=$s, v=$v]"

	companion object {
		private fun channel(value: Double): Int = Math.round(value * 255).coerceIn(0L, 255L).toInt()

		/**
		 * The HSV of 0xRRGGBB. What the colour cannot show is kept from [previous]: a grey keeps the
		 * hue, black keeps the hue and saturation.
		 */
		@JvmStatic
		fun fromRgb(rgb: Int, previous: Hsv): Hsv {
			val r = (rgb shr 16 and 0xFF) / 255.0
			val g = (rgb shr 8 and 0xFF) / 255.0
			val b = (rgb and 0xFF) / 255.0
			val max = maxOf(r, g, b)
			val delta = max - minOf(r, g, b)
			return when {
				max == 0.0 -> Hsv(previous.h, previous.s, 0f)
				delta == 0.0 -> Hsv(previous.h, 0f, max.toFloat())
				else -> {
					var hue = when (max) {
						r -> ((g - b) / delta) % 6
						g -> (b - r) / delta + 2
						else -> (r - g) / delta + 4
					} / 6
					if (hue < 0) {
						hue += 1
					}
					Hsv(hue.toFloat(), (delta / max).toFloat(), max.toFloat())
				}
			}
		}
	}
}
