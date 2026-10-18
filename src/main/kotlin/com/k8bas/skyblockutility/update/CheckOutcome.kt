package com.k8bas.skyblockutility.update

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What a manual check (*Check now*, `/ksu update check`, the settings card) found, with the text it shows (REQ-UPD-08):
 * one model the notices, the card and the install path all read. The automatic check keeps reporting
 * [CandidateFinder.Outcome] until T4.4 moves its notices onto this model.
 */
sealed interface CheckOutcome {
	/** The lines a manual check shows, times in [zone]. */
	fun lines(zone: ZoneId): List<String>

	/**
	 * A newer release with a jar for this Minecraft version.
	 *
	 * @param skipped its version is the one the player chose to skip (REQ-UPD-08: shown, marked)
	 * @param beta a pre-release, found on the BETA channel
	 */
	@JvmRecord
	data class Found(
		val candidate: CandidateFinder.Candidate,
		val hash: UpdateHooks.Hash,
		val eligibility: UpdateHooks.Eligibility,
		val skipped: Boolean,
		val beta: Boolean,
		val minecraft: String,
	) : CheckOutcome {
		/** An install may be offered: a SHA-256 to check, and an eligible game. */
		val installOffered: Boolean get() = hash is UpdateHooks.Hash.Verifiable && eligibility.eligible

		override fun lines(zone: ZoneId): List<String> {
			val out = ArrayList<String>()
			out.add("v${candidate.version} is available" + (if (beta) " (beta)" else "") + (if (skipped) " (skipped)" else ""))
			candidate.skippedWithoutJar.firstOrNull()?.let { out.add("v$it has no build for Minecraft $minecraft") }
			UpdateHooks.notOfferedReason(hash, eligibility)?.let { out.add(it) }
			return out
		}

		override fun toString(): String =
			"Found[candidate=$candidate, hash=$hash, eligibility=$eligibility, skipped=$skipped, beta=$beta, minecraft=$minecraft]"
	}

	/** A newer release exists, but none newer has a jar for this Minecraft version; [releasePage] for *Open release page*. */
	@JvmRecord
	data class NoBuild(val newest: SemVer, val minecraft: String, val releasePage: String?) : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("v$newest is out but has no build for Minecraft $minecraft")

		override fun toString(): String = "NoBuild[newest=$newest, minecraft=$minecraft, releasePage=$releasePage]"
	}

	/** @param running the running version, without its "+minecraft" part */
	@JvmRecord
	data class UpToDate(val running: String) : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("You're up to date (v$running)")

		override fun toString(): String = "UpToDate[running=$running]"
	}

	/** The running build is not a release version (a development build), so nothing is offered. */
	@JvmRecord
	data class NotARelease(val running: String, val newest: SemVer?) : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> {
			val out = arrayListOf("This build ($running) is not a release, so no update is offered")
			newest?.let { out.add("The newest release is v$it") }
			return out
		}

		override fun toString(): String = "NotARelease[running=$running, newest=$newest]"
	}

	/** Inside a rate-limit wait, or just limited: no request until [untilMs]. */
	@JvmRecord
	data class RateLimited(val untilMs: Long) : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> =
			listOf("GitHub rate limit reached, try again after " + HH_MM.format(Instant.ofEpochMilli(untilMs).atZone(zone)))

		override fun toString(): String = "RateLimited[untilMs=$untilMs]"
	}

	/** Offline, a DNS failure, a timeout, a 5xx, a malformed answer or a redirect away from GitHub. */
	data object Unreachable : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("Couldn't reach GitHub")
	}

	/** HTTP 404: the repository was renamed or made private. */
	data object SourceUnavailable : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("Release source unavailable")
	}

	/** A manual check within 60 s of the last one (REQ-UPD-04): nothing sent. */
	@JvmRecord
	data class Cooldown(val untilMs: Long, val nowMs: Long) : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> {
			val seconds = maxOf(1L, (untilMs - nowMs + 999) / 1000)
			return listOf("Checked a moment ago, try again in $seconds s")
		}

		override fun toString(): String = "Cooldown[untilMs=$untilMs, nowMs=$nowMs]"
	}

	/** Another check is running (REQ-UPD-04: one at a time). */
	data object Busy : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("An update check is already running")
	}

	/** A download is running (REQ-UPD-04: one check or download at a time). */
	data object Downloading : CheckOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("An update is downloading, try again when it is done")
	}
}

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
