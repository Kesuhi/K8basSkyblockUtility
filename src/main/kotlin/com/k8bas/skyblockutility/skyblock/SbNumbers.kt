package com.k8bas.skyblockutility.skyblock

import java.math.BigDecimal

/**
 * A number as Hypixel shows it. [approximate] is true for an abbreviated value such as `12.4k`: consumers
 * that need exact counts treat it as unknown, and no exact value is guessed from it (EC-GS-14).
 */
data class SbNumber(val value: Double, val approximate: Boolean) {
	/** The exact whole number, or null if the value is abbreviated or not whole. */
	fun exact(): Long? = if (approximate || value != Math.rint(value)) null else value.toLong()
}

/**
 * The one number parser of the game-state readers and their consumers (T3.0a, EC-GS-14): thousands
 * separators (`3,196,526`), decimals (`68.2`), the abbreviations k, M and B (approximate), and Roman or Arabic
 * numerals for tiers and levels. Anything else, `MAX` included, is null: unknown, never zero.
 */
object SbNumbers {
	private val NUMBER = Regex("^([+-]?)(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?([kKmMbB]?)$")
	private val ARABIC = Regex("^\\d{1,9}$")
	private val ROMAN = Regex("^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")
	private val ROMAN_VALUES = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)

	/** A number with optional separators, decimals and k/M/B, or a Roman numeral; null if it is neither. */
	@JvmStatic
	fun parse(text: String): SbNumber? {
		val trimmed = text.trim()
		val match = NUMBER.matchEntire(trimmed)
		if (match != null) {
			val (sign, whole, fraction, suffix) = match.destructured
			var value = BigDecimal(whole.replace(",", "") + if (fraction.isEmpty()) "" else ".$fraction")
			value = value.multiply(
				when (suffix.lowercase()) {
					"k" -> BigDecimal(1_000)
					"m" -> BigDecimal(1_000_000)
					"b" -> BigDecimal(1_000_000_000)
					else -> BigDecimal.ONE
				}
			)
			if (sign == "-") {
				value = value.negate()
			}
			return SbNumber(value.toDouble(), suffix.isNotEmpty())
		}
		return parseRoman(trimmed)?.let { SbNumber(it.toDouble(), false) }
	}

	/** A tier or level written as an Arabic (`262`) or Roman (`CCLXII`) numeral; null otherwise. */
	@JvmStatic
	fun parseLevel(text: String): Int? {
		val trimmed = text.trim()
		return if (ARABIC.matches(trimmed)) trimmed.toInt() else parseRoman(trimmed)
	}

	/** A canonical Roman numeral in capitals, 1 to 3999. */
	private fun parseRoman(text: String): Int? {
		if (text.isEmpty() || !ROMAN.matches(text)) {
			return null
		}
		var total = 0
		for (i in text.indices) {
			val value = ROMAN_VALUES.getValue(text[i])
			val next = if (i + 1 < text.length) ROMAN_VALUES.getValue(text[i + 1]) else 0
			total += if (value < next) -value else value
		}
		return total
	}
}
