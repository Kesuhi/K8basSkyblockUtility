package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.config.GeneralConfig;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The one clock source UI animations run on (REQ-UI-39, T3.24): the game's millisecond time, and motion on unless the
 * Animations switch is OFF. {@code -Dk8bas.ui.motion=off} turns motion off for the gametests, which click at the settled
 * layout. A screen may draw on its own source (a motion gametest's test clock with motion on): while it handles a frame
 * or an input event, {@link #SOURCE} and {@link #MILLIS} answer from it. Client thread only.
 */
public final class UiClock {
	/** The game's source: its time, and the Animations switch (ON while no config is loaded, e.g. in unit tests). */
	public static final UiTime GAME = game(System.getProperty("k8bas.ui.motion"), UiClock::animations, Util::getMillis);

	private static UiTime current = GAME;

	/** The source in effect now: the game's, or the one of the screen handling this frame or event. */
	public static final UiTime SOURCE = new UiTime() {
		@Override
		public long millis() {
			return current.millis();
		}

		@Override
		public boolean motion() {
			return current.motion();
		}
	};

	/** The time of {@link #SOURCE}. */
	public static final LongSupplier MILLIS = SOURCE::millis;

	private UiClock() {
	}

	/** A game source: motion follows {@code animations} unless {@code property} is "off". */
	static UiTime game(String property, BooleanSupplier animations, LongSupplier millis) {
		boolean allowed = !"off".equals(property);
		return UiTime.of(millis, () -> allowed && animations.getAsBoolean());
	}

	private static boolean animations() {
		GeneralConfig general = ConfigManager.generalOrNull();
		return general == null || general.animations();
	}

	public static UiTime current() {
		return current;
	}

	/** Makes {@code source} the one in effect; returns the one before, for {@link #leave}. */
	public static UiTime enter(UiTime source) {
		UiTime previous = current;
		current = source;
		return previous;
	}

	/** Puts back the source {@link #enter} returned. */
	public static void leave(UiTime previous) {
		current = previous;
	}
}
