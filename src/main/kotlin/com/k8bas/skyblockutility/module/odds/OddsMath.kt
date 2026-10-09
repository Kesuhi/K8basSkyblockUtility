package com.k8bas.skyblockutility.module.odds

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

/**
 * The Rare Drop Odds math (T3.9a, REQ-ODDS-08): every value is computed here from base chances, weights and meter
 * numbers; no derived percentage is copied from a source. Probabilities are 0..1. Plain Kotlin, no game types.
 */
object OddsMath {
	/** Magic Find counts up to this much. */
	const val MF_CAP = 900

	/** Magic Find scales only drops whose base chance is below this (pets always scale). */
	const val MF_THRESHOLD = 0.05

	/** The chance of at least one success in [attempts] tries at [p] each: 1 − (1 − p)^n. */
	@JvmStatic
	fun atLeastOne(p: Double, attempts: Int): Double = 1 - (1 - p).pow(attempts)

	/** How many tries a success takes on average: 1/p. */
	@JvmStatic
	fun expectedAttempts(p: Double): Double = 1 / p

	/** How lucky a drop on attempt [attempt] was: the share of players who would have had it by then. */
	@JvmStatic
	fun luckPercentile(p: Double, attempt: Int): Double = atLeastOne(p, attempt)

	/** A pet's chance with Magic Find (capped at 900) and Pet Luck: base × (1 + (MF + PL)/100). */
	@JvmStatic
	fun petChance(base: Double, magicFind: Int, petLuck: Int): Double = base * (1 + (min(magicFind, MF_CAP) + petLuck) / 100.0)

	/** Any other drop: base × (1 + MF/100) when base is below 5 %, else base. */
	@JvmStatic
	fun dropChance(base: Double, magicFind: Int): Double = if (base < MF_THRESHOLD) base * (1 + min(magicFind, MF_CAP) / 100.0) else base

	/**
	 * A corpse item's chance per corpse (D-3): the mean over the uniform roll counts [minRolls]..[maxRolls] of
	 * 1 − (1 − r)^n, with r = [weight] / [totalWeight] per roll.
	 */
	@JvmStatic
	fun perCorpse(weight: Double, totalWeight: Double, minRolls: Int, maxRolls: Int): Double {
		require(minRolls in 0..maxRolls) { "roll counts $minRolls..$maxRolls" }
		val perRoll = weight / totalWeight
		return (minRolls..maxRolls).sumOf { atLeastOne(perRoll, it) } / (maxRolls - minRolls + 1)
	}

	/** The RNG meter's boost: 1 + min(2 × stored/required, 2). */
	@JvmStatic
	fun meterBoost(stored: Long, required: Long): Double = 1 + min(2.0 * stored / required, 2.0)

	/** Whether the meter has reached its item: then the panel says "guaranteed" instead of a chance. */
	@JvmStatic
	fun meterGuaranteed(stored: Long, required: Long): Boolean = stored >= required

	/** A slayer drop's base chance at the highest tier: multiplier × 100 / required meter XP percent (500, or 250 for the Riftstalker). */
	@JvmStatic
	fun slayerBase(requiredXp: Long, riftstalker: Boolean): Double = (if (riftstalker) 250.0 else 500.0) / requiredXp

	/** Bosses left until the meter guarantees the item, from the last boss's meter gain; null before any gain is seen. */
	@JvmStatic
	fun bossesUntilGuaranteed(required: Long, stored: Long, lastGain: Long): Int? {
		if (stored >= required) {
			return 0
		}
		return if (lastGain > 0) ceil((required - stored).toDouble() / lastGain).toInt() else null
	}
}
