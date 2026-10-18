package com.k8bas.skyblockutility.update

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.function.Consumer
import java.util.zip.ZipFile

/**
 * Settles the staged update on every launch, locally and without network (REQ-UPD-18). A hard kill skips the shutdown
 * hook and an antivirus can stop the helper, so the game cannot know at exit whether the swap happened; the next launch
 * compares the running version with the pending record and finishes, re-arms, disarms or clears it. The hook's
 * apply-attempt marker tells a helper that ran (and failed, if the old version still runs) from a hard kill.
 *
 * It deletes only the jar the updater replaced itself, and only when the helper ran; any other copy of this mod is named
 * once and kept (R13). The state file is not trusted for paths: a file is deleted only if it is a jar of this mod beside
 * the running one, and a staged name only if it is an asset name of this mod. Each step fails on its own, so one broken
 * file never stops the rest or the save.
 *
 * @param runningVersion the running version as the Loader reports it
 * @param runningJar the jar the mod runs from, or null (a development run, a nested jar)
 * @param root `.k8bas_update` in the game directory
 * @param eligibility asked again before a re-arm (REQ-UPD-16)
 */
internal class LaunchReconciler(
	private val runningVersion: String,
	private val runningJar: Path?,
	private val root: Path,
	private val install: StagedInstall,
	private val eligibility: UpdateHooks.EligibilityCheck,
	private val info: Consumer<String>,
	private val warn: Consumer<String>,
) {
	/**
	 * What the launch found, for the player.
	 *
	 * @param toast one line for a toast now (REQ-UPD-18's "Updated to vX" and "Update ready, restart Minecraft to apply")
	 * @param chat lines for the first world join (a failed install, a disarmed update, other copies of the mod)
	 */
	data class Report(val toast: String?, val chat: List<String>)

	private val marker: Path = root.resolve(StagedInstall.ATTEMPT_MARKER)

	/** Reconciles [state] in place; the caller saves it, then calls [finish]. */
	fun reconcile(state: UpdateState): Report {
		val chat = ArrayList<String>()
		val attempted = Files.exists(marker)
		val keep = HashSet<Path>()
		val toast = step("the pending update", null) { settle(state, attempted, chat, keep) }
		step("the replaced jar", Unit) { retryLeftover(state, keep) }
		step("unfinished copies", Unit) { sweepTemporaryCopies() }
		step("other copies", Unit) { chat.addAll(otherCopies(state, keep)) }
		return Report(toast, chat)
	}

	/** After the caller saved the state: the attempt marker is consumed only now, so a crash before the save keeps it. */
	fun finish() {
		deleteQuietly(marker)
	}

	private fun settle(state: UpdateState, attempted: Boolean, chat: MutableList<String>, keep: MutableSet<Path>): String? {
		val pending = state.pendingInstall
		if (pending == null) {
			clearStaged("no pending update")
			return null
		}
		val running = SemVer.parseOrNull(runningVersion)
		val target = SemVer.parseOrNull(pending.version())
		val from = SemVer.parseOrNull(pending.fromVersion())
		return when {
			AssetSelector.declaredVersion(pending.fileName()) == null -> {
				warn.accept("The pending update record names no jar of this mod; cleared")
				clear(state)
				null
			}
			running != null && target != null && running.compareTo(target) == 0 -> {
				info.accept("Update to v${pending.version()} applied")
				clear(state)
				// Only a jar the helper replaced is the updater's to delete; one left by a hand install is another copy (R13).
				val old = path(pending.oldJar())
				if (attempted && old != null && replaceable(old)) {
					keep.add(old)
					if (!tryDelete(old)) {
						warn.accept("The replaced jar ${old.fileName} is still locked; it is deleted when the game exits")
						state.leftoverJar = old.toString()
					}
				}
				"Updated to v${pending.version()}"
			}
			running != null && from != null && running.compareTo(from) == 0 -> notApplied(state, pending, attempted, chat)
			else -> {
				info.accept("Pending update v${pending.version()} no longer fits the running v$runningVersion; cleared")
				clear(state)
				null
			}
		}
	}

	/** The version that staged the update still runs: re-arm it, or give up after two failed applies in a row. */
	private fun notApplied(state: UpdateState, pending: PendingInstall, attempted: Boolean, chat: MutableList<String>): String? {
		val failures = pending.failedApplies() + if (attempted) 1 else 0
		val staged = root.resolve("staged").resolve(pending.fileName())
		if (failures >= MAX_FAILED_APPLIES) {
			warn.accept("Update to v${pending.version()} failed $failures times in a row; disarmed")
			clear(state)
			chat.add("The update to v${pending.version()} could not be installed, so install it manually from the release page")
			return null
		}
		if (!Files.isRegularFile(staged)) {
			info.accept("Pending update v${pending.version()} has no staged jar; cleared")
			clear(state)
			return null
		}
		if (pending.sha256() != com.k8bas.skyblockutility.update.helper.UpdateHelper.sha256(staged)) {
			warn.accept("Staged update v${pending.version()} failed its check; deleted")
			clear(state)
			chat.add("The downloaded update v${pending.version()} failed its check and was deleted; nothing changed")
			return null
		}
		val allowed = eligibility.check()
		if (!allowed.eligible) {
			info.accept("Pending update v${pending.version()} can no longer be installed here: ${allowed.reason}; cleared")
			clear(state)
			chat.add("The update to v${pending.version()} can't be installed here now: ${allowed.reason}")
			return null
		}
		val armed = install.arm(staged, pending.fileName(), pending.sha256())
		if (armed.refusal() != null) {
			warn.accept("Pending update v${pending.version()} cannot be re-armed: ${armed.refusal()}; cleared")
			clear(state)
			return null
		}
		state.pendingInstall = PendingInstall(pending.version(), pending.fileName(), pending.sha256(), pending.stagedAtMs(),
			pending.fromVersion(), armed.old().toString(), failures)
		if (attempted) {
			// EC-UPD-08: the helper ran but the old version still runs, so the last install failed; one more try follows.
			warn.accept("Update to v${pending.version()} was not applied at the last exit; armed again")
			chat.add("The update to v${pending.version()} couldn't be installed when you quit; it tries once more next time")
		}
		return "Update ready, restart Minecraft to apply"
	}

	private fun clear(state: UpdateState) {
		state.pendingInstall = null
		clearStaged(null)
	}

	private fun retryLeftover(state: UpdateState, keep: MutableSet<Path>) {
		val leftover = state.leftoverJar ?: return
		val path = path(leftover)
		if (path == null || !Files.exists(path) || !replaceable(path)) {
			state.leftoverJar = null
		} else if (tryDelete(path)) {
			info.accept("Deleted the replaced jar ${path.fileName}")
			state.leftoverJar = null
		} else {
			keep.add(path)
			install.deleteAtExit(path)
		}
	}

	/** A jar of this mod beside the running jar, and not the running jar: the only kind this class deletes. */
	private fun replaceable(file: Path): Boolean {
		val jar = runningJar ?: return false
		return file.parent != null && file.toAbsolutePath().normalize().parent == jar.toAbsolutePath().normalize().parent &&
			file.fileName.toString().endsWith(".jar") && !sameFile(file, jar) && Files.isRegularFile(file) && isThisMod(file)
	}

	/** A `<name>.k8bas-part` the helper left when it was killed mid-copy (EC-UPD-07); Fabric ignores it, but it is ours. */
	private fun sweepTemporaryCopies() {
		val jar = runningJar ?: return
		Files.newDirectoryStream(jar.parent, "*" + com.k8bas.skyblockutility.update.helper.UpdateHelper.TEMPORARY_SUFFIX).use { files ->
			files.forEach { file ->
				info.accept("Removed the unfinished copy ${file.fileName}")
				deleteQuietly(file)
			}
		}
	}

	/** Every other jar in the running jar's folder whose fabric.mod.json has this mod's id, named once each (R13). */
	private fun otherCopies(state: UpdateState, keep: Set<Path>): List<String> {
		val jar = runningJar ?: return listOf()
		val lines = ArrayList<String>()
		Files.newDirectoryStream(jar.parent, "*.jar").use { files ->
			for (file in files) {
				val name = file.fileName.toString()
				if (sameFile(file, jar) || keep.any { sameFile(it, file) } || name in state.warnedFiles || !Files.isRegularFile(file) ||
					!isThisMod(file)
				) {
					continue
				}
				warn.accept("Another copy of this mod is in the mods folder: $name. It is kept; remove it by hand if you do not need it")
				lines.add("Another copy of K8bas Skyblock Utility is in your mods folder: $name")
				state.warnedFiles.add(name)
			}
		}
		return lines
	}

	private fun isThisMod(jar: Path): Boolean = try {
		ZipFile(jar.toFile()).use { zip ->
			val entry = zip.getEntry("fabric.mod.json") ?: return false
			val bytes = zip.getInputStream(entry).use { it.readNBytes(JarValidator.MAX_MOD_JSON_BYTES + 1) }
			if (bytes.size > JarValidator.MAX_MOD_JSON_BYTES) {
				return false
			}
			val root = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8))
			root.isJsonObject && root.asJsonObject.get("id")?.takeIf { it.isJsonPrimitive }?.asString == JarValidator.MOD_ID
		}
	} catch (e: Exception) {
		false
	}

	/** Deletes the staged files, but never through a link or junction put in the folder's place. */
	private fun clearStaged(reason: String?) {
		val staged = root.resolve("staged")
		val attributes = try {
			Files.readAttributes(staged, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
		} catch (e: java.io.IOException) {
			return
		}
		if (!attributes.isDirectory || attributes.isSymbolicLink || attributes.isOther) {
			return
		}
		Files.newDirectoryStream(staged).use { files ->
			files.forEach { file ->
				if (reason != null) {
					info.accept("Removed the orphaned staged file ${file.fileName} ($reason)")
				}
				deleteQuietly(file)
			}
		}
	}

	/** Runs one step; a failure is logged once and gives [fallback], so the other steps and the save still run. */
	private fun <T> step(what: String, fallback: T, action: () -> T): T = try {
		action()
	} catch (e: Exception) {
		warn.accept("Update: the launch check of $what failed: $e")
		fallback
	}

	private fun path(text: String?): Path? = try {
		text?.let { Path.of(it) }
	} catch (e: java.nio.file.InvalidPathException) {
		null
	}

	private fun tryDelete(file: Path): Boolean = try {
		Files.deleteIfExists(file)
		true
	} catch (e: java.io.IOException) {
		false
	}

	private fun deleteQuietly(file: Path) {
		tryDelete(file)
	}

	private fun sameFile(a: Path, b: Path): Boolean = try {
		Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b)
	} catch (e: java.io.IOException) {
		a.toAbsolutePath().normalize().toString().lowercase(Locale.ROOT) == b.toAbsolutePath().normalize().toString().lowercase(Locale.ROOT)
	}

	companion object {
		const val MAX_FAILED_APPLIES: Int = 2
	}
}
