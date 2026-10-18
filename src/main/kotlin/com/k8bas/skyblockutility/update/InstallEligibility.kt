package com.k8bas.skyblockutility.update

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate

/**
 * Whether this game may install an update itself (REQ-UPD-16); if not, the update is notify-only and the reason is shown
 * by name. It needs the mod to run from exactly one regular `.jar` in a folder it may write to, a release version, no
 * development run, and an instance no launcher manages: a launcher would undo or fight a jar replaced behind its back.
 * The launcher markers are heuristics; a false positive only downgrades to notify-only.
 *
 * @param origin how Fabric loaded the mod, null when unknown
 * @param gameDir the game directory (the instance's root)
 * @param writable whether a folder may be written to; [Files.isWritable] in the game. Nothing is written to test it, as
 *   nothing may be written into `mods/` while the game runs (REQ-UPD-17).
 */
class InstallEligibility(
	private val developmentEnvironment: Boolean,
	private val origin: Origin?,
	private val runningVersion: String,
	private val gameDir: Path,
	private val writable: Predicate<Path>,
) : UpdateHooks.EligibilityCheck {
	/**
	 * How Fabric loaded the mod.
	 *
	 * @param fromPaths the origin's kind is PATH (not nested in another jar, not unknown)
	 */
	data class Origin(val fromPaths: Boolean, val paths: List<Path>)

	/** The one regular `.jar` the mod runs from, or null. */
	val runningJar: Path? = origin
		?.takeIf { it.fromPaths && it.paths.size == 1 }
		?.paths?.first()
		?.takeIf { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") && it.parent != null }

	override fun check(): UpdateHooks.Eligibility {
		val reason = when {
			developmentEnvironment -> DEVELOPMENT
			runningJar == null -> NOT_ONE_JAR
			// The jar too: a read-only jar can be replaced but never deleted.
			!writable.test(runningJar.parent) || !writable.test(runningJar) -> UNWRITABLE
			SemVer.parseOrNull(runningVersion) == null -> NOT_SEMVER
			launcherManaged(runningJar) -> MANAGED
			else -> return UpdateHooks.Eligibility.ELIGIBLE
		}
		return UpdateHooks.Eligibility(false, reason)
	}

	private fun launcherManaged(jar: Path): Boolean =
		packwizNames(jar) || curseForge(jar) || modrinthApp()

	/** Prism and packwiz keep `mods/.index/<mod>.pw.toml`, whose `filename` names the jar they manage (verified layout). */
	private fun packwizNames(jar: Path): Boolean {
		val index = jar.parent.resolve(".index")
		if (!Files.isDirectory(index)) {
			return false
		}
		val name = jar.fileName.toString()
		return try {
			Files.newDirectoryStream(index, "*.pw.toml").use { files ->
				files.take(MAX_INDEX_FILES).any { file ->
					Files.size(file) <= MAX_INDEX_BYTES && String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().any { line ->
						FILENAME.matchEntire(line.trim())?.groupValues?.get(2).equals(name, ignoreCase = true)
					}
				}
			}
		} catch (e: IOException) {
			false
		} catch (e: java.nio.file.DirectoryIteratorException) {
			false
		}
	}

	/** CurseForge writes `minecraftinstance.json` into the instance (unverified). */
	private fun curseForge(jar: Path): Boolean =
		Files.exists(gameDir.resolve("minecraftinstance.json")) || jar.parent.parent?.let { Files.exists(it.resolve("minecraftinstance.json")) } == true

	/** The Modrinth App keeps instances in `<app data>/ModrinthApp/profiles/<name>` (unverified; older builds used its id). */
	private fun modrinthApp(): Boolean {
		var dir: Path? = gameDir.toAbsolutePath()
		while (dir?.parent != null) {
			if (dir.parent.fileName?.toString() == "profiles" && dir.parent.parent?.fileName?.toString() in MODRINTH_APP_FOLDERS) {
				return true
			}
			dir = dir.parent
		}
		return false
	}

	companion object {
		const val DEVELOPMENT: String = "This is a development run, so nothing is installed here"
		const val NOT_ONE_JAR: String = "The mod isn't loaded from a single jar file, so install it by hand from the release page"
		const val UNWRITABLE: String = "Can't write to the mods folder, so install it by hand from the release page"
		const val NOT_SEMVER: String = "This build's version isn't a release version, so install it by hand from the release page"
		const val MANAGED: String = "This instance is managed by your launcher, so update it there"

		private const val MAX_INDEX_FILES = 5_000
		private const val MAX_INDEX_BYTES = 64L * 1024
		private val FILENAME = Regex("filename\\s*=\\s*([\"'])(.*)\\1")
		private val MODRINTH_APP_FOLDERS = setOf("ModrinthApp", "com.modrinth.theseus")
	}
}
