package com.k8bas.skyblockutility.ui.option

/**
 * Whether an option works right now, for its card: OK, loading (its data is still being fetched),
 * partial (works with a gap, e.g. a missing tab widget) or unavailable (e.g. a skipped hook, T3.0f).
 *
 * @param message why, shown on the card; empty for OK
 */
@JvmRecord
data class OptionStatus(val state: State, val message: String) {
	enum class State {
		OK,
		LOADING,
		PARTIAL,
		UNAVAILABLE,
	}

	override fun toString(): String = "OptionStatus[state=$state, message=$message]"

	companion object {
		@JvmField
		val OK: OptionStatus = OptionStatus(State.OK, "")
	}
}
