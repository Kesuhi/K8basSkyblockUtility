package com.k8bas.skyblockutility.update

import java.net.URI
import java.util.Locale

/**
 * What the confirmation screen shows for a found update (REQ-UPD-11): running → new version and the channel, the asset
 * name and size, the first 12 hex characters of the expected SHA-256, and the release notes as plain text. Its
 * [consent] is the one click's token: this version and the checksum shown.
 */
class Confirmation private constructor(
	val versions: String,
	val channel: String,
	val asset: String,
	val size: String,
	/** The first 12 hex characters, or null when there is no SHA-256 to check against. */
	val checksum: String?,
	val notes: String,
	val installOffered: Boolean,
	/** Why no install is offered, or null. */
	val reason: String?,
	val releasePage: URI,
	private val version: String,
	private val sha256: String?,
) {
	/** The token the *Download & install on restart* click issues, or null when no install is offered. */
	fun consent(): Consent? = if (installOffered && sha256 != null) Consent(version, sha256) else null

	companion object {
		private const val CHECKSUM_CHARS = 12

		@JvmStatic
		fun of(found: CheckOutcome.Found, runningVersion: String, storedChannel: UpdateChannel?): Confirmation {
			val running = SemVer.parseOrNull(runningVersion)
			val shownRunning = running?.toString() ?: runningVersion
			val channel = UpdateChannel.effective(storedChannel, running)
			val sha256 = (found.hash as? UpdateHooks.Hash.Verifiable)?.sha256
			val notes = ReleaseNotes.plain(found.candidate.release.body())
			return Confirmation(
				versions = "v$shownRunning → v${found.candidate.version}",
				channel = if (channel == UpdateChannel.BETA) "Beta" else "Stable",
				asset = found.candidate.jar.name(),
				size = size(found.candidate.jar.size()),
				checksum = sha256?.take(CHECKSUM_CHARS),
				notes = notes.ifEmpty { "No release notes" },
				installOffered = found.installOffered,
				reason = UpdateHooks.notOfferedReason(found.hash, found.eligibility),
				releasePage = UpdateNotifier.releasePage(found.candidate.release.htmlUrl()),
				version = found.candidate.version.toString(),
				sha256 = sha256,
			)
		}

		/**
		 * [text] broken into lines no wider than [width] as [measure] counts: at spaces, a word longer than a line by
		 * characters (whole code points, at least one per line); a blank line stays one. Linear in the text: a long
		 * unbroken token is cut by adding up its characters' widths, never by measuring ever shorter prefixes.
		 */
		@JvmStatic
		fun wrap(text: String, width: Int, measure: java.util.function.ToIntFunction<String>): List<String> {
			val room = maxOf(1, width)
			val out = ArrayList<String>()
			val space = measure.applyAsInt(" ")
			for (paragraph in text.split('\n')) {
				val line = StringBuilder()
				var lineWidth = 0
				for (word in paragraph.split(' ')) {
					val wordWidth = measure.applyAsInt(word)
					val joinedWidth = if (line.isEmpty()) wordWidth else lineWidth + space + wordWidth
					if (joinedWidth <= room) {
						if (line.isNotEmpty()) {
							line.append(' ')
						}
						line.append(word)
						lineWidth = joinedWidth
						continue
					}
					if (line.isNotEmpty()) {
						out.add(line.toString())
						line.setLength(0)
						lineWidth = 0
					}
					if (wordWidth <= room) {
						line.append(word)
						lineWidth = wordWidth
						continue
					}
					// Too long for any line: cut it by its characters' widths.
					var i = 0
					while (i < word.length) {
						val next = word.offsetByCodePoints(i, 1)
						val chunk = word.substring(i, next)
						val chunkWidth = measure.applyAsInt(chunk)
						if (line.isNotEmpty() && lineWidth + chunkWidth > room) {
							out.add(line.toString())
							line.setLength(0)
							lineWidth = 0
						}
						line.append(chunk)
						lineWidth += chunkWidth
						i = next
					}
				}
				out.add(line.toString())
			}
			return out
		}

		/** Bytes as people read them, in decimal units: "950 bytes", "12 KB", "1.2 MB". */
		@JvmStatic
		fun size(bytes: Long): String = when {
			bytes < 1_000 -> "$bytes bytes"
			bytes < 1_000_000 -> "${(bytes + 500) / 1_000} KB"
			else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
		}
	}
}
