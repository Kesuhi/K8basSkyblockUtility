package com.k8bas.skyblockutility.ui.render

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * The icon kit as textures (REQ-UI-32, T3.20): white, with the [IconShapes] coverage as alpha, one per icon and size in
 * screen pixels, so it is drawn 1:1 and tinted with the icon's colour. Made at the current GUI scale and released at the next
 * scale change, at most [MAX_ICONS] at a time (REQ-UI-47); an icon beyond them, or in a session where one could not be made,
 * is drawn from fill spans, kept per scale like the textures. Uploaded through the game's texture API (no direct GL,
 * REQ-PORT-12).
 */
internal object IconTextures {
	/** The most icon textures kept at one GUI scale (REQ-UI-47). */
	const val MAX_ICONS: Int = 16

	private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/ui")
	private val icons = IconShapes.Icon.entries
	private val cache = cache({ key -> bake(key) }, { id -> Minecraft.getInstance().textureManager.release(id) },
		{ message, cause -> LOGGER.warn(message, cause) })
	private val spans = Int2ObjectOpenHashMap<IntArray>()
	private var spansScale = 0

	/** The icon cache's bound and names; the cache's own tests build it here too. */
	@JvmStatic
	fun <V : Any> cache(baker: MaskCache.Baker<V>, releaser: MaskCache.Releaser<V>, logger: MaskCache.Logger): MaskCache<V> =
		MaskCache(baker, releaser, logger, MAX_ICONS, "icon", "icons")

	/** The cache key of an icon px screen pixels wide: distinct for every icon and size up to 65535 px. */
	@JvmStatic
	fun key(icon: IconShapes.Icon, px: Int): Int = icon.ordinal shl 16 or px

	/** The registered icon, px × px screen pixels at this GUI scale, or null if it cannot be had (draw [spans] then). */
	@JvmStatic
	fun texture(scale: Int, icon: IconShapes.Icon, px: Int): Identifier? = cache.get(scale, key(icon, px))

	/** For gametests (AC-UI-48): the GUI scale the icon textures are for, how many there are, and how many of them another scale's. */
	@JvmStatic
	fun cachedForTests(): IntArray = intArrayOf(cache.scaleForTests(), cache.sizeForTests(), cache.staleForTests())

	/** The icon's fill spans for the fallback, made once per icon and size at this GUI scale. */
	@JvmStatic
	fun spans(scale: Int, icon: IconShapes.Icon, px: Int): IntArray {
		if (scale != spansScale) {
			spans.clear()
			spansScale = scale
		}
		return spans.computeIfAbsent(key(icon, px)) { IconShapes.spans(icon, px) }
	}

	private fun bake(key: Int): Identifier {
		val icon = icons[key shr 16]
		val px = key and 0xFFFF
		val alpha = IconShapes.bake(icon, px)
		val name = "ui_icon_${icon.name.lowercase()}_$px"
		val id = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "dynamic/$name")
		val texture = DynamicTexture({ "k8bas icon $name" }, px, px, false)
		try {
			val pixels = texture.pixels
			for (y in 0 until px) {
				for (x in 0 until px) {
					pixels.setPixelABGR(x, y, alpha[y * px + x] shl 24 or 0xFFFFFF)
				}
			}
			texture.upload()
			Minecraft.getInstance().textureManager.register(id, texture)
		} catch (e: RuntimeException) {
			// Once made, close() frees the image and the GPU texture alike.
			texture.close()
			throw e
		}
		return id
	}
}
