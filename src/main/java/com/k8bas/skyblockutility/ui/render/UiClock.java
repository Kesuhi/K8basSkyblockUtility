package com.k8bas.skyblockutility.ui.render;

import net.minecraft.util.Util;

import java.util.function.LongSupplier;

/** The clock UI animations run on: the game's millisecond time. */
public final class UiClock {
	public static final LongSupplier MILLIS = Util::getMillis;
	/**
	 * Whether the settings screen's open and category-switch motion plays (T2.9). Off only for the gametests
	 * (-Dk8bas.ui.motion=off), which click at the settled layout; their motion test turns it on for its own screen.
	 */
	public static final boolean MOTION = !"off".equals(System.getProperty("k8bas.ui.motion"));

	private UiClock() {
	}
}
