package com.k8bas.skyblockutility.render.marker;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * For gametests: what markers really cost a frame, not only their submit step (review T3.0n #1).
 * <ul>
 *   <li>The frame time is vanilla's {@link Minecraft#getFrameTimeNs()}: the CPU time of the whole
 *       frame render, from surface acquire to blit. It holds the vertex building (prepareFrame), the
 *       main pass and the always-on-top pass where see-through labels draw. It is CPU time: the
 *       gametest runs one frame per tick, so the GPU never backs up into it (REQ-NPCWP-10, AC-MARK-06
 *       are measured as CPU frame time).</li>
 *   <li>The main pass (START_MAIN to END_MAIN) is logged as a detail; it holds only the draws made there.</li>
 * </ul>
 * Each frame also records how many markers WorldMarkers drew, so a case whose markers silently did
 * not show cannot pass as cheap, and whether translucent terrain had its own target (Improved
 * Transparency really on).
 */
public final class FrameTimer {
	/** Frames per measuring block; the two cases alternate, so drift (JIT, chunk rebuilds) hits both alike. */
	private static final int BLOCK = 100;
	private static boolean registered;
	private static volatile boolean recording;
	private static long passStart;
	private static long frames;
	private static long passNanos;
	private static long frameNanos;
	private static int minDrawn;
	private static int maxDrawn;
	private static volatile boolean ownTranslucentTarget;

	/** Mean milliseconds per frame over {@code frames} frames, and the fewest and most markers drawn in one. */
	public record Means(long frames, double mainPassMillis, double frameMillis, int minDrawn, int maxDrawn) {
	}

	/** Both cases, and how much the markers add to each mean. */
	public record Comparison(Means without, Means with) {
		public double mainPassDelta() {
			return with.mainPassMillis() - without.mainPassMillis();
		}

		public double frameDelta() {
			return with.frameMillis() - without.frameMillis();
		}

		@Override
		public String toString() {
			return String.format("frame %.3f -> %.3f ms (+%.3f), main pass %.3f -> %.3f ms (+%.3f), %d+%d frames, drawn %d..%d without, %d..%d with",
					without.frameMillis(), with.frameMillis(), frameDelta(),
					without.mainPassMillis(), with.mainPassMillis(), mainPassDelta(), without.frames(), with.frames(),
					without.minDrawn(), without.maxDrawn(), with.minDrawn(), with.maxDrawn());
		}
	}

	private FrameTimer() {
	}

	/** Registers the frame hooks; idempotent, call on the client thread before rendering what is checked. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		LevelRenderEvents.START_MAIN.register(context -> {
			// Only exists while a frame renders, so it is sampled here.
			ownTranslucentTarget = context.levelRenderer().translucentTarget() != null;
			if (recording) {
				passStart = System.nanoTime();
			}
		});
		LevelRenderEvents.END_MAIN.register(context -> {
			if (recording && passStart != 0) {
				passNanos += System.nanoTime() - passStart;
				// Vanilla sets the frame time after this pass, so this reads the previous frame's; the
				// two ticks waited after each switch make that one a frame of the same case.
				frameNanos += Minecraft.getInstance().getFrameTimeNs();
				// COLLECT_SUBMITS, which sets the last frame, ran before this pass.
				int drawn = WorldMarkers.lastFrame().submitted();
				minDrawn = Math.min(minDrawn, drawn);
				maxDrawn = Math.max(maxDrawn, drawn);
				frames++;
			}
		});
	}

	/** True if the last rendered frame drew translucent terrain into its own target (Improved Transparency). */
	public static boolean ownTranslucentTarget() {
		return ownTranslucentTarget;
	}

	/**
	 * Measures at least {@code framesEach} frames with the markers shown and as many without, in
	 * alternating blocks, with vsync off and no frame limit. Both runnables run on the client thread.
	 */
	public static Comparison compare(ClientGameTestContext context, Runnable showMarkers, Runnable hideMarkers, int framesEach) {
		boolean[] vsync = new boolean[1];
		int[] limit = new int[1];
		context.runOnClient(client -> {
			register();
			vsync[0] = client.options.enableVsync().get();
			limit[0] = client.options.framerateLimit().get();
			client.options.enableVsync().set(false);
			client.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
		});
		try {
			return measure(context, showMarkers, hideMarkers, framesEach);
		} finally {
			context.runOnClient(client -> {
				client.options.enableVsync().set(vsync[0]);
				client.options.framerateLimit().set(limit[0]);
			});
		}
	}

	private static Comparison measure(ClientGameTestContext context, Runnable showMarkers, Runnable hideMarkers, int framesEach) {
		long[][] totals = new long[2][3];
		int[][] drawn = {{Integer.MAX_VALUE, 0}, {Integer.MAX_VALUE, 0}};
		for (int block = 0; block * BLOCK < framesEach; block++) {
			for (int withMarkers = 0; withMarkers < 2; withMarkers++) {
				Runnable setup = withMarkers == 1 ? showMarkers : hideMarkers;
				context.runOnClient(client -> setup.run());
				context.waitTicks(2);
				context.runOnClient(client -> start());
				context.waitFor(client -> frames >= BLOCK, 20 * 60);
				long[] measured = context.computeOnClient(client -> stop());
				for (int i = 0; i < 3; i++) {
					totals[withMarkers][i] += measured[i];
				}
				drawn[withMarkers][0] = Math.min(drawn[withMarkers][0], (int) measured[3]);
				drawn[withMarkers][1] = Math.max(drawn[withMarkers][1], (int) measured[4]);
			}
		}
		context.runOnClient(client -> hideMarkers.run());
		return new Comparison(means(totals[0], drawn[0]), means(totals[1], drawn[1]));
	}

	private static Means means(long[] totals, int[] drawn) {
		long frames = totals[0];
		return new Means(frames, totals[1] / 1e6 / frames, totals[2] / 1e6 / frames, drawn[0], drawn[1]);
	}

	private static void start() {
		frames = 0;
		passNanos = 0;
		frameNanos = 0;
		passStart = 0;
		minDrawn = Integer.MAX_VALUE;
		maxDrawn = 0;
		recording = true;
	}

	/** Frames, main-pass and frame nanoseconds, and the fewest and most markers drawn, since {@link #start()}. */
	private static long[] stop() {
		recording = false;
		return new long[] {frames, passNanos, frameNanos, minDrawn, maxDrawn};
	}
}
