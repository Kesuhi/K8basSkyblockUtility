package com.k8bas.skyblockutility.render.marker

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.Options

/**
 * For gametests: what markers really cost a frame, not only their submit step (review T3.0n #1).
 * - The frame time is vanilla's [Minecraft.getFrameTimeNs]: the CPU time of the whole
 *   frame render, from surface acquire to blit. It holds the vertex building (prepareFrame), the
 *   main pass and the always-on-top pass where see-through labels draw. It is CPU time: the
 *   gametest runs one frame per tick, so the GPU never backs up into it (REQ-NPCWP-10, AC-MARK-06
 *   are measured as CPU frame time).
 * - The main pass (START_MAIN to END_MAIN) is logged as a detail; it holds only the draws made there.
 *
 * Each frame also records how many markers WorldMarkers drew, so a case whose markers silently did
 * not show cannot pass as cheap, and whether translucent terrain had its own target (Improved
 * Transparency really on). The per-frame hooks touch only primitive fields: nothing is boxed or made.
 */
object FrameTimer {
	/** Frames per measuring block; the two cases alternate, so drift (JIT, chunk rebuilds) hits both alike. */
	private const val BLOCK = 100
	private var registered = false

	@Volatile
	private var recording = false
	private var passStart = 0L
	private var frames = 0L
	private var passNanos = 0L
	private var frameNanos = 0L
	private var minDrawn = 0
	private var maxDrawn = 0

	@Volatile
	private var ownTranslucentTarget = false

	/** Mean milliseconds per frame over [frames] frames, and the fewest and most markers drawn in one. */
	@JvmRecord
	data class Means(val frames: Long, val mainPassMillis: Double, val frameMillis: Double, val minDrawn: Int, val maxDrawn: Int) {
		override fun toString(): String =
			"Means[frames=$frames, mainPassMillis=$mainPassMillis, frameMillis=$frameMillis, minDrawn=$minDrawn, maxDrawn=$maxDrawn]"
	}

	/** Both cases, and how much the markers add to each mean. */
	@JvmRecord
	data class Comparison(val without: Means, val with: Means) {
		fun mainPassDelta(): Double = with.mainPassMillis - without.mainPassMillis

		fun frameDelta(): Double = with.frameMillis - without.frameMillis

		/** In the default locale, as Java's String.format(String, ...) wrote it. */
		override fun toString(): String = java.lang.String.format(
			"frame %.3f -> %.3f ms (+%.3f), main pass %.3f -> %.3f ms (+%.3f), %d+%d frames, drawn %d..%d without, %d..%d with",
			without.frameMillis, with.frameMillis, frameDelta(),
			without.mainPassMillis, with.mainPassMillis, mainPassDelta(), without.frames, with.frames,
			without.minDrawn, without.maxDrawn, with.minDrawn, with.maxDrawn,
		)
	}

	/** Registers the frame hooks; idempotent, call on the client thread before rendering what is checked. */
	@JvmStatic
	fun register() {
		if (registered) {
			return
		}
		registered = true
		LevelRenderEvents.START_MAIN.register { context ->
			// Only exists while a frame renders, so it is sampled here.
			ownTranslucentTarget = context.levelRenderer().translucentTarget() != null
			if (recording) {
				passStart = System.nanoTime()
			}
		}
		LevelRenderEvents.END_MAIN.register {
			if (recording && passStart != 0L) {
				passNanos += System.nanoTime() - passStart
				// Vanilla sets the frame time after this pass, so this reads the previous frame's; the
				// two ticks waited after each switch make that one a frame of the same case.
				frameNanos += Minecraft.getInstance().frameTimeNs
				// COLLECT_SUBMITS, which sets the last frame, ran before this pass.
				val drawn = WorldMarkers.lastFrame().submitted()
				minDrawn = Math.min(minDrawn, drawn)
				maxDrawn = Math.max(maxDrawn, drawn)
				frames++
			}
		}
	}

	/** True if the last rendered frame drew translucent terrain into its own target (Improved Transparency). */
	@JvmStatic
	fun ownTranslucentTarget(): Boolean = ownTranslucentTarget

	/**
	 * Measures at least [framesEach] frames with the markers shown and as many without, in
	 * alternating blocks, with vsync off and no frame limit. Both runnables run on the client thread.
	 */
	@JvmStatic
	fun compare(context: ClientGameTestContext, showMarkers: Runnable, hideMarkers: Runnable, framesEach: Int): Comparison {
		var vsync = false
		var limit = 0
		context.runOnClient<RuntimeException> { client ->
			register()
			vsync = client.options.enableVsync().get()
			limit = client.options.framerateLimit().get()
			client.options.enableVsync().set(false)
			client.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF)
		}
		try {
			return measure(context, showMarkers, hideMarkers, framesEach)
		} finally {
			context.runOnClient<RuntimeException> { client ->
				client.options.enableVsync().set(vsync)
				client.options.framerateLimit().set(limit)
			}
		}
	}

	private fun measure(context: ClientGameTestContext, showMarkers: Runnable, hideMarkers: Runnable, framesEach: Int): Comparison {
		val totals = Array(2) { LongArray(3) }
		val drawn = arrayOf(intArrayOf(Int.MAX_VALUE, 0), intArrayOf(Int.MAX_VALUE, 0))
		var block = 0
		while (block * BLOCK < framesEach) {
			for (withMarkers in 0 until 2) {
				val setup = if (withMarkers == 1) showMarkers else hideMarkers
				context.runOnClient<RuntimeException> { setup.run() }
				context.waitTicks(2)
				context.runOnClient<RuntimeException> { start() }
				context.waitFor({ frames >= BLOCK }, 20 * 60)
				val measured = context.computeOnClient<LongArray, RuntimeException> { stop() }
				for (i in 0 until 3) {
					totals[withMarkers][i] += measured[i]
				}
				drawn[withMarkers][0] = Math.min(drawn[withMarkers][0], measured[3].toInt())
				drawn[withMarkers][1] = Math.max(drawn[withMarkers][1], measured[4].toInt())
			}
			block++
		}
		context.runOnClient<RuntimeException> { hideMarkers.run() }
		return Comparison(means(totals[0], drawn[0]), means(totals[1], drawn[1]))
	}

	private fun means(totals: LongArray, drawn: IntArray): Means {
		val frames = totals[0]
		return Means(frames, totals[1] / 1e6 / frames, totals[2] / 1e6 / frames, drawn[0], drawn[1])
	}

	private fun start() {
		frames = 0
		passNanos = 0
		frameNanos = 0
		passStart = 0
		minDrawn = Int.MAX_VALUE
		maxDrawn = 0
		recording = true
	}

	/** Frames, main-pass and frame nanoseconds, and the fewest and most markers drawn, since [start]. */
	private fun stop(): LongArray {
		recording = false
		return longArrayOf(frames, passNanos, frameNanos, minDrawn.toLong(), maxDrawn.toLong())
	}
}
