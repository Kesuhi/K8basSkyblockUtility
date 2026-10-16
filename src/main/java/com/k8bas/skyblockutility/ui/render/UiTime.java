package com.k8bas.skyblockutility.ui.render;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * A UI clock source (REQ-UI-39): the time in milliseconds and whether motion is on. Both are asked each time, so the
 * Animations switch applies at once in an open screen. The game's is {@link UiClock#GAME}; a test gives its own.
 */
public interface UiTime {
	long millis();

	/** False: every animated value is at its end at once (Animations OFF, or the gametests' default). */
	boolean motion();

	static UiTime of(LongSupplier millis, BooleanSupplier motion) {
		return new UiTime() {
			@Override
			public long millis() {
				return millis.getAsLong();
			}

			@Override
			public boolean motion() {
				return motion.getAsBoolean();
			}
		};
	}
}
