package com.k8bas.skyblockutility.hud

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.config.HudConfig
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.abs

/**
 * The elements' positions in `general.hud.positions` (REQ-HUD-03). Reading never writes: an element with
 * no entry uses its default, and a malformed entry its default place with its scale held to the range,
 * logged once and left in the file until the player saves a new position (EC-HUD-05). Entries for ids no
 * element has are kept as they are (EC-HUD-04). A parsed entry is reused until it changes, so the draw path
 * only looks it up. Nothing here writes the file; the editor saves.
 *
 * @param read  the section as it is (null while absent)
 * @param write the section, created when absent
 */
class HudPositions(private val read: Supplier<HudConfig?>, private val write: Supplier<HudConfig>, private val log: Consumer<String>) {
	private data class Parsed(val raw: JsonElement, val fallback: HudPosition, val position: HudPosition)

	private val parsed = HashMap<String, Parsed>()
	private val logged = HashSet<String>()

	/** The section's entries, or null while the section or its map is absent. */
	private fun entries(): MutableMap<String, JsonElement>? = read.get()?.positions

	/** Where element [id] is; [fallback] (its default) when it has no usable entry. */
	fun get(id: String, fallback: HudPosition): HudPosition {
		val raw = entries()?.get(id) ?: return fallback
		val last = parsed[id]
		if (last != null && last.raw === raw && last.fallback == fallback) {
			return last.position
		}
		val position = parse(id, raw, fallback)
		parsed[id] = Parsed(raw, fallback, position)
		return position
	}

	/** Element [id]'s entry as written, or null. */
	fun raw(id: String): JsonElement? = entries()?.get(id)

	/** Puts back an entry as it was written ([raw]), or removes it when it was null. */
	fun restore(id: String, raw: JsonElement?) {
		if (raw != null) {
			writable()[id] = raw
		} else {
			entries()?.remove(id)
		}
	}

	/** Whether element [id] has an entry in the config, usable or not. */
	fun has(id: String): Boolean = entries()?.containsKey(id) == true

	/** Stores element [id]'s position in the config; the caller saves. */
	fun set(id: String, position: HudPosition) {
		writable()[id] = JsonObject().apply {
			addProperty("anchor", position.anchor.name)
			addProperty("x", position.x)
			addProperty("y", position.y)
			addProperty("scale", position.scale)
		}
	}

	/** The section's entries, the section and its map created when absent. */
	private fun writable(): MutableMap<String, JsonElement> {
		val config = write.get()
		return config.positions ?: LinkedHashMap<String, JsonElement>().also { config.positions = it }
	}

	private fun parse(id: String, raw: JsonElement, fallback: HudPosition): HudPosition {
		if (!raw.isJsonObject) {
			malformed(id, raw)
			return fallback
		}
		val entry = raw.asJsonObject
		val anchor = (entry["anchor"] as? JsonPrimitive)?.takeIf { it.isString }?.let { HudAnchor.named(it.asString) }
		val x = integer(entry["x"])
		val y = integer(entry["y"])
		val scale = number(entry["scale"])
		if (anchor != null && x != null && y != null && HudScale.inRange(scale)) {
			return HudPosition(anchor, x, y, scale)
		}
		malformed(id, raw)
		return fallback.withScale(if (scale.isFinite()) scale else fallback.scale)
	}

	private fun malformed(id: String, raw: JsonElement) {
		if (logged.add("$id|$raw")) {
			log.accept("HUD position of '$id' is malformed ($raw); using its default place until a new one is saved")
		}
	}

	companion object {
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/hud")

		/**
		 * The live positions, in the config file's general section. A malformed entry is logged with the next frame's
		 * tasks, not from the draw path that read it (REQ-HUD-11).
		 */
		@JvmField
		val LIVE = HudPositions(
			{ ConfigManager.general().hud },
			{ ConfigManager.general().let { general -> general.hud ?: HudConfig().also { general.hud = it } } },
			{ message -> Minecraft.getInstance().schedule(Runnable { LOGGER.warn("{}", message) }) },
		)

		private fun integer(value: JsonElement?): Int? {
			val number = number(value)
			return if (number.isFinite() && number == Math.rint(number) && abs(number) < 100_000) number.toInt() else null
		}

		/** A JSON number (or numeric string), or NaN. */
		private fun number(value: JsonElement?): Double {
			val primitive = value as? JsonPrimitive ?: return Double.NaN
			if (primitive.isBoolean) {
				return Double.NaN
			}
			return try {
				primitive.asDouble
			} catch (e: NumberFormatException) {
				Double.NaN
			}
		}
	}
}
