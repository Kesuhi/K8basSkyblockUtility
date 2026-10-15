package com.k8bas.skyblockutility.ui.render

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * The anti-aliased masks as textures (REQ-UI-18, T2.9d, R32; REQ-UI-30, T3.18): white, with the [CornerMask] coverage as
 * alpha, a corner mask per radius and thickness and a disc mask per diameter, all in screen pixels, so one texel is one
 * screen pixel and the colour comes from the tint. Uploaded through the game's texture API (no direct GL, REQ-PORT-12), as
 * the colour wheel is.
 */
internal object CornerTextures {
	private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/ui")
	private val cache = MaskCache({ key -> bake(key) }, { id -> Minecraft.getInstance().textureManager.release(id) },
		{ message, cause -> LOGGER.warn(message, cause) })

	/** The registered mask for this [MaskKey] at this GUI scale, or null if it cannot be had (the shape uses fill spans then). */
	@JvmStatic
	fun mask(scale: Int, key: Int): Identifier? = cache.get(scale, key)

	private fun bake(key: Int): Identifier {
		val disc = MaskKey.isDisc(key)
		val radius = MaskKey.radius(key)
		val thickness = MaskKey.thickness(key)
		val size = if (disc) MaskKey.diameter(key) else 2 * radius
		val alpha = when {
			disc -> CornerMask.bakeDisc(size)
			thickness == 0 -> CornerMask.bakeFill(radius)
			else -> CornerMask.bakeRing(radius, thickness)
		}
		val name = if (disc) "ui_disc_$size" else "ui_corner_${radius}_$thickness"
		val id = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "dynamic/$name")
		val image = NativeImage(size, size, false)
		try {
			for (y in 0 until size) {
				for (x in 0 until size) {
					image.setPixelABGR(x, y, alpha[y * size + x] shl 24 or 0xFFFFFF)
				}
			}
			// Once registered, the texture owns the image and closes it when released.
			Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "k8bas mask $name" }, image))
		} catch (e: RuntimeException) {
			image.close()
			throw e
		}
		return id
	}
}
