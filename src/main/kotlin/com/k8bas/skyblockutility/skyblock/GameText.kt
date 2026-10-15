package com.k8bas.skyblockutility.skyblock

/**
 * Text helpers shared by the game-state readers (T3.0a). Hypixel's sidebar teams carry legacy `§` codes as
 * literal text, among them filler codes vanilla does not know (`§q`, `§u`, ...) that split words and numbers
 * (`Purse: §6311,732§s§6,284`), so `ChatFormatting.stripFormatting`'s `§[0-9a-fk-or]` is not enough.
 */
object GameText {
	/** A `§` and the character after it, or a lone `§` at the end (a code the server cut off). */
	private val CODES = Regex("§[\\s\\S]?")
	private val INVISIBLE = Regex("[\\u200B-\\u200D\\u2060\\uFEFF]")
	private val LETTERS = Regex("\\p{L}+")
	private val DIGITS = Regex("\\p{Nd}+")
	private const val MAX_SHAPE = 80

	/** The text without `§` codes and zero-width characters (REQ-GS-03); spaces are kept. */
	@JvmStatic
	fun strip(text: String): String = INVISIBLE.replace(CODES.replace(text, ""), "")

	/**
	 * The shape of a line, for logging an unknown one (REQ-GS-10): every run of letters becomes `a`, every
	 * run of digits `9`, so the log names no player and lines that differ only in their numbers share a shape.
	 */
	@JvmStatic
	fun shape(text: String): String = DIGITS.replace(LETTERS.replace(strip(text), "a"), "9").take(MAX_SHAPE)
}
