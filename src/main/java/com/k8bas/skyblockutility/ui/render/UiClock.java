package com.k8bas.skyblockutility.ui.render;

import net.minecraft.util.Util;

import java.util.function.LongSupplier;

/** The clock UI animations run on: the game's millisecond time. */
public final class UiClock {
	public static final LongSupplier MILLIS = Util::getMillis;

	private UiClock() {
	}
}
