package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;

/**
 * Whether an option works right now, for its card: OK, loading (its data is still being fetched),
 * partial (works with a gap, e.g. a missing tab widget) or unavailable (e.g. a skipped hook, T3.0f).
 *
 * @param message why, shown on the card; empty for OK
 */
public record OptionStatus(State state, String message) {
	public enum State {
		OK, LOADING, PARTIAL, UNAVAILABLE
	}

	public static final OptionStatus OK = new OptionStatus(State.OK, "");

	public OptionStatus {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(message, "message");
	}
}
