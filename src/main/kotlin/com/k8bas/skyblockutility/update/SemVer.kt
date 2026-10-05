package com.k8bas.skyblockutility.update

import java.util.Optional

/**
 * A strict Semantic Versioning 2.0.0 version (REQ-UPD-05). Build metadata ("+26.2") is accepted and
 * ignored, so `1.1.0+26.2` equals `1.1.0+26.1.2`.
 *
 * A class rather than a data class: the pre-release list is copied on construction, which a data
 * class's generated `copy` would skip. Java sees the record-style accessors `major()`, `minor()`,
 * `patch()` and `preRelease()`.
 */
class SemVer(
	@get:JvmName("major") val major: Int,
	@get:JvmName("minor") val minor: Int,
	@get:JvmName("patch") val patch: Int,
	preRelease: List<String>,
) : Comparable<SemVer> {
	@get:JvmName("preRelease")
	val preRelease: List<String> = java.util.List.copyOf(preRelease)

	val isPreRelease: Boolean
		get() = preRelease.isNotEmpty()

	override fun compareTo(other: SemVer): Int {
		val core = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
		if (core != 0) {
			return core
		}
		// A version without a pre-release part ranks above the same version with one.
		if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
			return preRelease.isEmpty().compareTo(other.preRelease.isEmpty())
		}
		for ((mine, theirs) in preRelease.zip(other.preRelease)) {
			val result = compareIdentifier(mine, theirs)
			if (result != 0) {
				return result
			}
		}
		return preRelease.size.compareTo(other.preRelease.size)
	}

	override fun equals(other: Any?): Boolean =
		other is SemVer && major == other.major && minor == other.minor && patch == other.patch && preRelease == other.preRelease

	override fun hashCode(): Int = ((major * 31 + minor) * 31 + patch) * 31 + preRelease.hashCode()

	override fun toString(): String = "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")

	companion object {
		private const val NUMBER = "0|[1-9]\\d*"
		private const val PRE_ID = "0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*"
		private val PATTERN = Regex(
			"^($NUMBER)\\.($NUMBER)\\.($NUMBER)" +
				"(?:-((?:$PRE_ID)(?:\\.(?:$PRE_ID))*))?" +
				"(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
		)

		/** The version, or null when [text] is not SemVer (or a number does not fit an int). */
		fun parseOrNull(text: String?): SemVer? {
			val match = text?.let(PATTERN::matchEntire) ?: return null
			val (major, minor, patch, pre) = match.destructured
			return try {
				SemVer(major.toInt(), minor.toInt(), patch.toInt(), if (pre.isEmpty()) emptyList() else pre.split('.'))
			} catch (tooLarge: NumberFormatException) {
				null
			}
		}

		/** A release tag: one leading "v" is stripped, the rest must be SemVer. */
		fun fromTagOrNull(tag: String?): SemVer? = tag?.let { parseOrNull(it.removePrefix("v")) }

		@JvmStatic
		fun parse(text: String?): Optional<SemVer> = Optional.ofNullable(parseOrNull(text))

		@JvmStatic
		fun fromTag(tag: String?): Optional<SemVer> = Optional.ofNullable(fromTagOrNull(tag))

		/** Numeric identifiers compare as numbers and rank below alphanumeric ones, which compare in ASCII order. */
		private fun compareIdentifier(a: String, b: String): Int {
			val aNumeric = a.all(Char::isDigit)
			val bNumeric = b.all(Char::isDigit)
			return when {
				aNumeric && bNumeric -> if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
				aNumeric != bNumeric -> if (aNumeric) -1 else 1
				else -> a.compareTo(b)
			}
		}
	}
}
