package com.k8bas.skyblockutility.module.odds

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * The one format of every displayed probability (T3.9a, REQ-ODDS-06): "1 in N (P%) <basis>". N is whole with
 * thousands separators; P has 2 significant digits, rounded half up on its decimal value, and shows as "<0.0001%"
 * below that. A chance scaled or boosted to 1 or more is "1 in 1 (100%)". A value from a table row with
 * `verified: false` ends in " ?" (its tooltip names the source). The Croesus run estimate has its own form ([runs]).
 */
object OddsFormat {
	/** What a value is per, as REQ-ODDS-06 names it. */
	enum class Basis(val label: String) {
		PER_KILL("per kill"),
		PER_ROLL("per roll"),
		PER_CORPSE("per corpse"),
		BASE("base"),
		WITH_LUCK("with your MF/PL"),
		RELATIVE("relative"),
	}

	/** What the RNG-meter panel shows once the meter is full (REQ-ODDS-08). */
	const val GUARANTEED = "guaranteed"

	private val MIN_PERCENT = BigDecimal("0.0001")

	/** "1 in N (P%) basis" for [p] above 0 (1 or more counts as 1); with " ?" when the row is not [verified]. */
	@JvmStatic
	@JvmOverloads
	fun oneIn(p: Double, basis: Basis, verified: Boolean = true): String {
		require(p > 0) { "a probability above 0, got $p" }
		val chance = min(p, 1.0)
		return "1 in ${oneInN(chance)} (${percent(chance)}) ${basis.label}" + if (verified) "" else " ?"
	}

	/**
	 * The Croesus run estimate (REQ-ODDS-19): "≈ 1 in N runs (P%, base, no bonuses)" with N = [runs] rounded to a
	 * whole number of at least 1, and P = 100 / N.
	 */
	@JvmStatic
	fun runs(runs: Double): String {
		val n = max(1L, runs.roundToLong())
		return "≈ 1 in ${grouped(n)} runs (${percent(1.0 / n)}, base, no bonuses)"
	}

	private fun oneInN(p: Double): String = grouped(max(1L, (1 / p).roundToLong()))

	/** On the decimal value of [p] (as written), so 0.01 is "1.0%" and 1/1,000,000 is exactly the minimum. */
	private fun percent(p: Double): String {
		val value = BigDecimal.valueOf(p).movePointRight(2)
		if (value < MIN_PERCENT) {
			return "<0.0001%"
		}
		val rounded = value.round(MathContext(2, RoundingMode.HALF_UP))
		// A value with one significant digit (0.4, 1) gets its second one (0.40, 1.0).
		val twoDigits = if (rounded.precision() < 2) rounded.setScale(rounded.scale() + 2 - rounded.precision()) else rounded
		return twoDigits.toPlainString() + "%"
	}

	private fun grouped(n: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
}
