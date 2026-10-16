package com.k8bas.skyblockutility.ui.widget

import com.k8bas.skyblockutility.ui.color.WheelImage
import com.k8bas.skyblockutility.ui.render.MaskCache
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * The colour picker's hue/saturation wheel as a texture (REQ-UI-13, REQ-UI-31, T3.19): baked at the wheel's drawn size in
 * screen pixels ([WheelImage]), so it is drawn 1:1, and rebaked when the GUI scale or the brightness changes, one at a time
 * ([WheelCache]); the picked colour is a marker drawn on top. If it cannot be made, the wheel is drawn the 1.2.1 way, one
 * 128 px texture scaled onto it (REQ-UI-47). Uploaded through the game's texture API (no direct GL, REQ-PORT-12).
 */
internal object WheelTexture {
	/** The size of the 1.2.1 wheel's texture, which the fallback scales onto the wheel. */
	const val LEGACY_SIZE: Int = 128

	private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/ui")
	private val scaled = WheelCache(GameTextures("ui_color_wheel_"), MaskCache.Logger { message, cause ->
		LOGGER.warn("{}: the picker falls back to the 1.2.1 wheel for this session", message, cause)
	})
	private val fallback = WheelCache(GameTextures("ui_color_wheel_legacy_"), MaskCache.Logger { _, cause ->
		LOGGER.warn("The 1.2.1 colour wheel's texture could not be made or rebaked either: the picker shows no wheel for this session", cause)
	})

	/**
	 * For gametests: while set, [get] gives null as if the wheel could not be made, so the fallback is drawn (REQ-UI-47).
	 * Cleared, the fallback's texture is freed again, so the client keeps one wheel texture as in play.
	 */
	@JvmStatic
	var fallbackForTests: Boolean = false
		set(value) {
			field = value
			if (!value) {
				fallback.clear()
			}
		}

	/** The wheel, size × size screen pixels at this brightness; null if it cannot be made (draw [legacy] then). */
	@JvmStatic
	fun get(size: Int, value: Float): Identifier? = if (fallbackForTests) null else scaled.get(size, value)?.id

	/** The 1.2.1 wheel, [LEGACY_SIZE] px at this brightness; null if that cannot be made either. */
	@JvmStatic
	fun legacy(value: Float): Identifier? = fallback.get(LEGACY_SIZE, value)?.id

	/** Frees the wheel's textures when the colour picker closes; the next one makes them again for its GUI scale. */
	@JvmStatic
	fun release() {
		scaled.clear()
		fallback.clear()
	}

	/** For gametests (AC-UI-48): how many wheel textures there are, and the scaled one's size in screen pixels (0 for none). */
	@JvmStatic
	fun cachedForTests(): IntArray =
		intArrayOf((if (scaled.sizeForTests() > 0) 1 else 0) + (if (fallback.sizeForTests() > 0) 1 else 0), scaled.sizeForTests())

	private class Baked(val id: Identifier, val image: WheelImage, val texture: DynamicTexture)

	/** Each size has its own id, so a new size is registered before the old one is released. */
	private class GameTextures(private val prefix: String) : WheelCache.Textures<Baked> {
		override fun make(size: Int, value: Float): Baked {
			val image = WheelImage.of(size)
			val id = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "dynamic/$prefix$size")
			val texture = DynamicTexture({ "k8bas colour wheel $size" }, size, size, false)
			try {
				fill(texture.pixels, image, value)
				texture.upload()
				Minecraft.getInstance().textureManager.register(id, texture)
			} catch (e: RuntimeException) {
				// Once made, close() frees the image and the GPU texture alike. A throw inside the game's constructor is its
				// own failure, at most once a session, since the cache then stays failed.
				texture.close()
				throw e
			}
			return Baked(id, image, texture)
		}

		override fun refill(texture: Baked, value: Float) {
			fill(texture.texture.pixels, texture.image, value)
			texture.texture.upload()
		}

		override fun release(texture: Baked) {
			Minecraft.getInstance().textureManager.release(texture.id)
		}

		private fun fill(pixels: NativeImage, image: WheelImage, value: Float) {
			val size = image.size()
			val argb = image.argb(value)
			for (y in 0 until size) {
				for (x in 0 until size) {
					pixels.setPixel(x, y, argb[y * size + x])
				}
			}
		}
	}
}
