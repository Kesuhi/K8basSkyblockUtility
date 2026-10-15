package com.k8bas.skyblockutility.skyblock

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer

/**
 * Logs each unrecognised line shape once per session (REQ-GS-10, AC-GS-10). Only the [GameText.shape] goes
 * out, never the raw line, because the log is written to disk (REQ-GS-12).
 */
class ShapeLog(private val sink: BiConsumer<String, String>) {
	private val seen: MutableSet<String> = ConcurrentHashMap.newKeySet()

	/** Reports the shape of [text] for this [kind] of line, the first time it is seen; true if it was new. */
	fun once(kind: String, text: String): Boolean {
		val shape = GameText.shape(text)
		val fresh = seen.add(kind + '\u0000' + shape)
		if (fresh) {
			sink.accept(kind, shape)
		}
		return fresh
	}

	companion object {
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/skyblock")

		/** The readers' log: one WARN per new shape. */
		@JvmField
		val SHARED = ShapeLog { kind, shape ->
			LOGGER.warn("Unrecognised {} line shape '{}' (logged once per session; letters shown as a, digits as 9)", kind, shape)
		}
	}
}
