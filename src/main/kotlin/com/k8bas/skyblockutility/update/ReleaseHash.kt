package com.k8bas.skyblockutility.update

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * The SHA-256 a download is checked against (REQ-UPD-14): GitHub's `digest` when it is "sha256:" with 64 hex digits,
 * else the release's sidecar `<name>.sha256`. When both exist they must agree, so a release that carries a sidecar has
 * it read at the check, before any confirmation; it is at most 1 KiB and is not the jar (REQ-UPD-11, R49). A sidecar
 * that was read is remembered for the session. One that could not be fetched now answers [UpdateHooks.Hash.Unavailable],
 * so no install is offered on the digest alone, and it is asked again at the next check.
 *
 * @param downloader with the API's 20 s budget rather than the jar's 120 s, so a blocked host holds a check no longer
 */
internal class ReleaseHash(private val downloader: SecureDownloader, private val warn: Consumer<String>) : UpdateHooks.HashCheck {
	private val known = ConcurrentHashMap<String, UpdateHooks.Hash>()

	override fun check(release: Release, jar: Release.Asset): UpdateHooks.Hash {
		val digest = UpdateHooks.fromDigest(jar.digest())
		val sidecar = release.assets().firstOrNull { it.name() == jar.name() + ".sha256" && it.state() == "uploaded" }
			?: return digest
		val key = listOf(jar.name(), jar.downloadUrl(), jar.digest(), sidecar.downloadUrl(), sidecar.size()).joinToString("\u0000")
		known[key]?.let { return it }
		val read = readSidecar(sidecar) ?: return UpdateHooks.Hash.Unavailable
		val hash = when {
			read.isEmpty -> digest
			digest is UpdateHooks.Hash.Verifiable && digest.sha256 != read.get() -> {
				warn.accept("Release checksum conflict for " + jar.name() + ": the digest says " + digest.sha256 + ", its .sha256 file " + read.get())
				UpdateHooks.Hash.Conflict
			}
			else -> UpdateHooks.Hash.Verifiable(read.get())
		}
		known[key] = hash
		return hash
	}

	/** The sidecar's hash, empty if the file is not a valid sidecar, or null if it could not be fetched now. */
	private fun readSidecar(sidecar: Release.Asset): java.util.Optional<String>? {
		if (sidecar.size() <= 0 || sidecar.size() > MAX_SIDECAR_BYTES) {
			return java.util.Optional.empty()
		}
		val bytes = ByteArrayOutputStream()
		return when (downloader.fetch(sidecar.downloadUrl(), sidecar.size(), MAX_SIDECAR_BYTES.toLong(), bytes)) {
			is SecureDownloader.Done -> java.util.Optional.ofNullable(parseSidecar(bytes.toByteArray()))
			is SecureDownloader.Failed -> null
		}
	}

	companion object {
		const val MAX_SIDECAR_BYTES: Int = 1024
		private val HEX64 = Regex("[0-9a-fA-F]{64}")

		/** The first token as 64 lowercase hex digits, or null: at most 1 KiB of UTF-8, one leading BOM tolerated. */
		@JvmStatic
		fun parseSidecar(bytes: ByteArray): String? {
			if (bytes.size > MAX_SIDECAR_BYTES) {
				return null
			}
			val text = try {
				StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes))
					.toString()
			} catch (e: CharacterCodingException) {
				return null
			}
			val token = text.removePrefix("﻿").trimStart().split(Regex("\\s+"), limit = 2).first()
			return if (HEX64.matches(token)) token.lowercase(Locale.ROOT) else null
		}
	}
}
