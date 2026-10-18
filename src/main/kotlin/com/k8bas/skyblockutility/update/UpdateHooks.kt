package com.k8bas.skyblockutility.update

/**
 * What a found release needs before an install may be offered (REQ-UPD-08): a SHA-256 to check the download against (T4.2)
 * and an install the running game allows (T4.3). Until those tasks land, the defaults read GitHub's digest only and answer
 * notify-only, so every found release is offered for a manual install from its release page.
 */
class UpdateHooks(val hash: HashCheck, val eligibility: EligibilityCheck) {
	/** The checksum a download of [jar] would be checked against. */
	fun interface HashCheck {
		fun check(release: Release, jar: Release.Asset): Hash
	}

	/** Whether this game may install an update itself, and if not, why. */
	fun interface EligibilityCheck {
		fun check(): Eligibility
	}

	sealed interface Hash {
		@JvmRecord
		data class Verifiable(val sha256: String) : Hash {
			override fun toString(): String = "Verifiable[sha256=$sha256]"
		}

		/** No digest of the kind "sha256:" (and, from T4.2, no valid sidecar). */
		data object Missing : Hash

		/** The digest and the sidecar disagree (T4.2). */
		data object Conflict : Hash

		/** The release has a sidecar that could not be read now, so the two cannot be compared yet; asked again next time. */
		data object Unavailable : Hash
	}

	/** @param reason shown when not [eligible], a sentence without a full stop */
	@JvmRecord
	data class Eligibility(val eligible: Boolean, val reason: String?) {
		override fun toString(): String = "Eligibility[eligible=$eligible, reason=$reason]"

		companion object {
			@JvmField
			val ELIGIBLE: Eligibility = Eligibility(true, null)
		}
	}

	companion object {
		/** Until T4.3: an install is never offered, so the player installs by hand. */
		@JvmField
		val NOTIFY_ONLY: Eligibility = Eligibility(false, "Install it by hand from the release page")

		/** GitHub's digest when it is a SHA-256, else none; never eligible. */
		@JvmField
		val DEFAULT: UpdateHooks = UpdateHooks(HashCheck { _, jar -> fromDigest(jar.digest) }, EligibilityCheck { NOTIFY_ONLY })

		/** Why no install may be offered (REQ-UPD-08's notify-only and conflict rows), or null if one may. */
		@JvmStatic
		fun notOfferedReason(hash: Hash, eligibility: Eligibility): String? = when {
			hash is Hash.Conflict -> "Release checksum conflict, install it by hand from the release page"
			hash is Hash.Missing -> "It has no SHA-256 checksum, so install it by hand from the release page"
			hash is Hash.Unavailable -> "Couldn't read its checksum file from GitHub, try again later"
			!eligibility.eligible -> eligibility.reason ?: NOTIFY_ONLY.reason
			else -> null
		}

		/** "sha256:" and 64 hex digits, lowercased; anything else is not a usable SHA-256 (REQ-UPD-08). */
		@JvmStatic
		fun fromDigest(digest: String?): Hash {
			val hex = digest?.takeIf { it.startsWith("sha256:") }?.substring("sha256:".length)?.lowercase(java.util.Locale.ROOT)
			if (hex == null || hex.length != 64 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) {
				return Hash.Missing
			}
			return Hash.Verifiable(hex)
		}
	}
}
