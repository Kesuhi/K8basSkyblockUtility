package com.k8bas.skyblockutility.ui.render

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * The anti-aliased corner masks as textures (REQ-UI-18, T2.9d, R32): white, with the [CornerMask] coverage as
 * alpha, one per radius and thickness in screen pixels, so one texel is one screen pixel and the colour comes
 * from the tint. Uploaded through the game's texture API (no direct GL, REQ-PORT-12), as the colour wheel is.
 */
internal object CornerTextures {
	private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/ui")
	private val cache = MaskCache({ radius, thickness -> bake(radius, thickness) },
		{ id -> Minecraft.getInstance().textureManager.release(id) })
	private var warned = false

	/** A registered mask at this GUI scale, or null if masks cannot be made (the fill-based corners are used then). */
	@JvmStatic
	fun mask(scale: Int, radius: Int, thickness: Int): Identifier? {
		val id = cache.get(scale, radius, thickness)
		if (id == null && !warned) {
			warned = true
			LOGGER.warn("Smooth corners are off for this session: a corner texture could not be made", cache.failure)
		}
		return id
	}

	private fun bake(radius: Int, thickness: Int): Identifier {
		val alpha = if (thickness == 0) CornerMask.bakeFill(radius) else CornerMask.bakeRing(radius, thickness)
		val size = 2 * radius
		val id = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "dynamic/ui_corner_${radius}_$thickness")
		val image = NativeImage(size, size, false)
		try {
			for (y in 0 until size) {
				for (x in 0 until size) {
					image.setPixelABGR(x, y, alpha[y * size + x] shl 24 or 0xFFFFFF)
				}
			}
			// Once registered, the texture owns the image and closes it when released.
			Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "k8bas corner mask $radius/$thickness" }, image))
		} catch (e: RuntimeException) {
			image.close()
			throw e
		}
		return id
	}
}
