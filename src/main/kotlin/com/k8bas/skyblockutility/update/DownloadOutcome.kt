package com.k8bas.skyblockutility.update

import java.nio.file.Path
import java.time.ZoneId

/**
 * What a confirmed download ([UpdateService.download]) did, with the text the confirmation screen, `/ksu update install`
 * and the settings card show (REQ-UPD-11, REQ-UPD-14, REQ-UPD-15). Every outcome but [Staged] leaves no file behind.
 */
sealed interface DownloadOutcome {
	/** The lines to show, times in [zone]. */
	fun lines(zone: ZoneId): List<String>

	/**
	 * Downloaded, its SHA-256 and its fabric.mod.json checked, moved to [file] in the staging folder, and armed: the helper
	 * puts it in when the game exits (T4.3).
	 */
	@JvmRecord
	data class Staged(val version: String, val file: Path, val sha256: String) : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("Update ready, restart Minecraft to apply")

		override fun toString(): String = "Staged[version=$version, file=$file, sha256=$sha256]"
	}

	/** The bytes are not the release's (REQ-UPD-14). */
	data object ChecksumMismatch : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("Checksum mismatch, nothing changed")
	}

	/** The jar's fabric.mod.json does not fit this game (REQ-UPD-15); [reason] goes to the log. */
	@JvmRecord
	data class Incompatible(val reason: String) : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("Incompatible build, nothing changed")

		override fun toString(): String = "Incompatible[reason=$reason]"
	}

	/** The transfer broke a rule of REQ-UPD-13 or the network failed; [reason] goes to the log. */
	@JvmRecord
	data class Failed(val reason: String) : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("Download failed, nothing changed")

		override fun toString(): String = "Failed[reason=$reason]"
	}

	/** No install may be offered for the candidate (REQ-UPD-08's notify-only rows); [reason] says why. Nothing was fetched. */
	@JvmRecord
	data class NotOffered(val reason: String) : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf(reason)

		override fun toString(): String = "NotOffered[reason=$reason]"
	}

	/**
	 * The confirmation was for [requested], which is not the candidate on offer (REQ-UPD-11: a stale click is refused and
	 * shows the current one); [current] is null when there is none.
	 */
	@JvmRecord
	data class Stale(val requested: String, val current: SemVer?) : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> =
			listOf("v$requested is not the update on offer; " + if (current == null) "there is none" else "that is v$current")

		override fun toString(): String = "Stale[requested=$requested, current=$current]"
	}

	/** The release's file changed since the confirmation screen showed its checksum: nothing was fetched. */
	@JvmRecord
	data class Changed(val version: SemVer) : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("v$version changed on GitHub since you saw it; open it again to confirm")

		override fun toString(): String = "Changed[version=$version]"
	}

	/** A check holds the lock (REQ-UPD-04). */
	data object Busy : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("An update check is running, try again when it is done")
	}

	/** Another download holds the lock. */
	data object Downloading : DownloadOutcome {
		override fun lines(zone: ZoneId): List<String> = listOf("An update is already downloading")
	}
}
