package com.k8bas.skyblockutility.ui.widget

import com.k8bas.skyblockutility.ui.color.Hsv
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * One shared hue/saturation wheel texture for the colour picker (REQ-UI-13), baked at the requested
 * brightness and rebaked only when that changes; the picked colour is a puck drawn on top. 128 px with
 * a soft rim, so it stays smooth at large GUI scales. Uploaded through the game's texture API (no
 * direct GL, REQ-PORT-12).
 */
internal object WheelTexture {
	const val SIZE: Int = 128

	@JvmField
	val ID: Identifier = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "dynamic/ui_color_wheel")
	private var texture: DynamicTexture? = null
	private var bakedValue = -1f

	@JvmStatic
	fun ensureValue(value: Float) {
		val baked = texture
		if (baked == null) {
			texture = DynamicTexture({ "k8bas colour wheel" }, build(value)).also { Minecraft.getInstance().textureManager.register(ID, it) }
			bakedValue = value
			return
		}
		if (abs(bakedValue - value) < 1f / 255f) {
			return
		}
		baked.setPixels(build(value))
		baked.upload()
		bakedValue = value
	}

	private fun build(value: Float): NativeImage {
		val image = NativeImage(SIZE, SIZE, false)
		val radius = SIZE / 2f
		for (y in 0 until SIZE) {
			for (x in 0 until SIZE) {
				val dx = x + 0.5f - radius
				val dy = y + 0.5f - radius
				val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
				// Fully inside up to half a pixel from the rim, then fading over one pixel.
				val coverage = maxOf(0f, minOf(1f, radius - distance + 0.5f))
				if (coverage <= 0f) {
					image.setPixelABGR(x, y, 0)
					continue
				}
				val hue = (atan2(dy.toDouble(), dx.toDouble()) / (Math.PI * 2)).toFloat() + 0.5f
				val rgb = Hsv(hue % 1f, minOf(1f, distance / radius), value).toRgb()
				val alpha = Math.round(coverage * 255)
				image.setPixelABGR(x, y, alpha shl 24 or ((rgb and 0xFF) shl 16) or ((rgb shr 8 and 0xFF) shl 8) or (rgb shr 16 and 0xFF))
			}
		}
		return image
	}
}
