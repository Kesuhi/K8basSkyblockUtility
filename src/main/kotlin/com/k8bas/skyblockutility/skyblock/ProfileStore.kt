package com.k8bas.skyblockutility.skyblock

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.k8bas.skyblockutility.config.store.AtomicFileStore
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

/**
 * The per-profile store (T3.0d, REQ-GS-09): `<root>/<account>/<profile key>/<consumer>.json`, one file per
 * consumer (`odds`, `bestiary`, `sbxp`), each written through config-store's atomic facility, so a save cut
 * off before its move leaves the previous file (REQ-CFG-12). Each folder remembers its profile id in [META].
 * A known name that comes back with another id is a new profile: the old folder moves to
 * `<root>/<account>/archive/<key>-<time>/`, never merged. The store reads and writes only a folder it is
 * sure belongs to the current profile: never while the profile is unknown, nor while a profile without an
 * id yet meets a folder that already belongs to an id, nor after an archive move that failed (it is retried
 * at the next [use]). The only files the game-state package writes are these parsed results (REQ-GS-12).
 * Client thread only.
 */
class ProfileStore @JvmOverloads constructor(
	private val root: Path,
	private val warn: Consumer<String>,
	private val mover: FolderMover = FolderMover { from, to -> Files.move(from, to) },
) {
	/** Moves a profile folder into the archive; replaceable in tests. */
	fun interface FolderMover {
		@Throws(IOException::class)
		fun move(from: Path, to: Path)
	}

	private sealed class Stored {
		object None : Stored()
		data class Id(val id: String) : Stored()
		object Unreadable : Stored()
	}

	private val stores = HashMap<Path, AtomicFileStore>()
	private var profile: Profile? = null
	private var usable = false

	/** The current profile, or null when unknown. Archives the old state when the id changed. */
	fun use(next: Profile?) {
		profile = next
		usable = false
		if (next == null) {
			return
		}
		val folder = folder(next)
		val stored = stored(folder)
		val id = next.id
		usable = when {
			stored is Stored.Unreadable -> {
				warn.accept("Could not read ${META} of profile ${next.name}; its state is not used this time")
				false
			}
			// Not sure yet which profile this is: wait for the id rather than mix two profiles' state.
			id == null -> stored is Stored.None
			stored is Stored.Id && stored.id == id -> true
			stored is Stored.Id -> archive(next, folder) && remember(folder, next, id)
			else -> remember(folder, next, id)
		}
	}

	/** The consumer's state for the current profile, or null when unknown, not usable or not saved yet. */
	fun read(consumer: String): String? {
		val file = file(consumer) ?: return null
		stores[file]?.flush()
		return try {
			if (Files.isRegularFile(file)) Files.readString(file, StandardCharsets.UTF_8) else null
		} catch (e: IOException) {
			warn.accept("Could not read ${file.fileName} of this profile (${e.javaClass.simpleName})")
			null
		}
	}

	/** Saves the consumer's state for the current profile; nothing while it is unknown or not usable. */
	fun write(consumer: String, content: String) {
		val file = file(consumer) ?: return
		save(file, content)
	}

	/** Writes everything pending now, e.g. on client shutdown. */
	fun flush() {
		stores.values.forEach { it.flush() }
	}

	private fun file(consumer: String): Path? {
		require(CONSUMER.matches(consumer)) { "not a consumer file name: $consumer" }
		val current = profile ?: return null
		return if (usable) folder(current).resolve("$consumer.json") else null
	}

	private fun folder(profile: Profile): Path = root.resolve(profile.account.toString()).resolve(folder(profile.key))

	private fun save(file: Path, content: String) {
		stores.getOrPut(file) { AtomicFileStore(file) { e -> warn.accept("Could not save ${file.fileName} of this profile (${e.message})") } }
			.requestSave(content)
	}

	private fun remember(folder: Path, profile: Profile, id: String): Boolean {
		val meta = JsonObject()
		meta.addProperty("profileId", id)
		meta.addProperty("name", profile.name)
		save(folder.resolve(META), meta.toString())
		return true
	}

	private fun stored(folder: Path): Stored {
		val meta = folder.resolve(META)
		stores[meta]?.flush()
		if (!Files.isRegularFile(meta)) {
			return Stored.None
		}
		return try {
			JsonParser.parseString(Files.readString(meta)).asJsonObject.get("profileId")?.asString?.let { Stored.Id(it) } ?: Stored.Unreadable
		} catch (e: Exception) {
			Stored.Unreadable
		}
	}

	/** Moves the old profile's folder into the archive, its pending writes first; false if it could not. */
	private fun archive(profile: Profile, folder: Path): Boolean {
		stores.filterKeys { it.startsWith(folder) }.forEach { (path, store) ->
			store.flush()
			stores.remove(path)
		}
		val archive = root.resolve(profile.account.toString()).resolve(ARCHIVE)
		val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
		var target = archive.resolve("${folder(profile.key)}-$stamp")
		var n = 2
		while (Files.exists(target)) {
			target = archive.resolve("${folder(profile.key)}-$stamp-${n++}")
		}
		var failure: IOException? = null
		for (attempt in 1..MOVE_ATTEMPTS) {
			try {
				Files.createDirectories(archive)
				mover.move(folder, target)
				return true
			} catch (e: IOException) {
				// A scanner or indexer may hold a file for a moment.
				failure = e
				if (attempt < MOVE_ATTEMPTS) {
					Thread.sleep(RETRY_PAUSE_MS)
				}
			}
		}
		warn.accept("Could not archive the old state of profile ${profile.name} (${failure?.javaClass?.simpleName}); it is kept apart and tried again")
		return false
	}

	companion object {
		/** The file in each profile folder that holds its profile id. */
		const val META = "profile.json"
		private const val ARCHIVE = "archive"
		private const val MOVE_ATTEMPTS = 3
		private const val RETRY_PAUSE_MS = 100L
		private val CONSUMER = Regex("^[a-z][a-z0-9_]*$")

		/** A profile key as a folder name: letters, digits, _ and -, anything else as _. */
		@JvmStatic
		fun folder(key: String): String {
			val safe = key.replace(Regex("[^a-z0-9_-]"), "_")
			return when {
				safe.isEmpty() || safe.all { it == '_' } && key.all { it == '.' } -> "_"
				// The archive folder sits next to the profile folders.
				safe == ARCHIVE -> "_$ARCHIVE"
				else -> safe
			}
		}
	}
}
