package com.k8bas.skyblockutility.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.k8bas.skyblockutility.config.migration.ConfigMigrations
import com.k8bas.skyblockutility.config.migration.Migrator
import com.k8bas.skyblockutility.config.store.AtomicFileStore
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Owns the single config file and its root shape (general section + one JSON blob per
 * module). Deliberately doesn't know any individual module's config class at compile
 * time — modules ask for their own typed section and hand back an updated one to persist,
 * which is what keeps each module self-contained instead of this class growing a
 * per-module if-chain as more modules get added.
 *
 * Saving goes through one AtomicFileStore (REQ-CFG-04/05): save() serialises the current state on
 * the calling thread and returns at once; the file is written atomically within 2 s, merged with
 * any other pending save, and flushed on client shutdown (REQ-CFG-10).
 *
 * Loading never loses settings (REQ-CFG-06/07/08/11): an unreadable file or section is kept as a
 * byte-exact timestamped backup before anything writes the config path, and only that part falls
 * back to defaults; a file from a newer version is backed up first; the first data-changing
 * migration leaves a one-time .v0.bak copy. Each backup is announced once in chat after joining.
 * If the file cannot be read, or a backup it needs cannot be written, nothing is saved for the rest
 * of the session, so the file on disk is never replaced without its copy (REQ-CFG-06, REQ-CFG-08).
 *
 * Every synchronized function is also @JvmStatic, so all of them lock ConfigManager.class as the Java
 * `static synchronized` methods did (a plain object member would lock the instance instead).
 */
object ConfigManager {
	private val LOGGER: Logger = LoggerFactory.getLogger("k8bas_skyblock_utility/config")
	private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
	private const val FILE_NAME = "k8bas_skyblock_utility.json"
	private val BACKUP_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

	/**
	 * Resolved on first use rather than at class load, so tests can point load(Path) at a
	 * temporary file without a running Fabric Loader.
	 */
	private var configPath: Path? = null
	private var root: SkyblockUtilityConfig? = null
	private var store: AtomicFileStore? = null

	/**
	 * The bytes the file had when it was loaded, for a backup taken later (a broken module section
	 * only shows when its module reads it).
	 */
	private var originalBytes: ByteArray? = null
	private var backupThisSession: Path? = null

	/** Whether the settings screen has named this session's backup yet (EC-UI-16). */
	private var backupNamedInScreen = false
	/** Nullable entries, as the Java list took them: a queued null fails only when the notices are drained. */
	private val pendingNotices = ArrayList<String?>()

	/** Shows a save failure to the player; set by the client entrypoint (no-op in tests). */
	private var saveFailureNotice: Consumer<String>? = Consumer { }

	/** Set when the file could not be read or backed up: saving would lose the user's file. */
	@Volatile
	private var savesSuspended = false

	/** Every call of the single save path, for tests that count writes (AC-UI-14). */
	private val SAVE_REQUESTS = AtomicInteger()
	const val READ_ATTEMPTS: Int = 3
	const val READ_RETRY_PAUSE_MS: Long = 150

	/** Reads and writes the file system; replaceable in tests (Java implementations may throw IOException). */
	interface Disk {
		@Throws(IOException::class)
		fun read(path: Path): ByteArray

		@Throws(IOException::class)
		fun writeBackup(path: Path, bytes: ByteArray)
	}

	/** A plain static field, as before: tests assign it. */
	@JvmField
	var disk: Disk = object : Disk {
		override fun read(path: Path): ByteArray = Files.readAllBytes(path)

		override fun writeBackup(path: Path, bytes: ByteArray) {
			Files.write(path, bytes)
		}
	}

	private fun configPath(): Path {
		val path = configPath ?: FabricLoader.getInstance().configDir.resolve(FILE_NAME)
		configPath = path
		return path
	}

	@JvmStatic
	fun load() {
		load(configPath())
	}

	/** Loads (or creates) the config at the given path; later saves go to the same path. */
	@JvmStatic
	fun load(path: Path) {
		load(path, ConfigMigrations.MIGRATOR)
	}

	@JvmStatic
	@Synchronized
	fun load(path: Path, migrator: Migrator) {
		configPath = path
		val current = store
		if (current == null || current.file() != path) {
			store = AtomicFileStore(path) { e ->
				saveFailureNotice!!.accept("Couldn't save settings (" + e.message + "). They are kept for now; the next change retries.")
			}
		}
		originalBytes = null
		backupThisSession = null
		backupNamedInScreen = false
		savesSuspended = false
		pendingNotices.clear()
		if (!Files.exists(path)) {
			root = SkyblockUtilityConfig()
			save()
			return
		}
		val bytes = readWithRetries(path)
		originalBytes = bytes
		if (bytes == null) {
			// A locked or unreadable file (antivirus, sync tool, editor) is not a broken one: keep it.
			root = SkyblockUtilityConfig()
			suspendSaves("it could not be read")
			return
		}
		val obj = parseObject(bytes)
		if (obj == null) {
			// Invalid JSON, a 0-byte file or no JSON object at all (EC-CFG-01).
			backup("broken", "it could not be read; defaults are in use")
			root = SkyblockUtilityConfig()
			return
		}

		// Migrate the tree before binding it: Gson would give a missing configVersion the current
		// default, hiding that the file is older.
		val migration = migrator.migrate(obj)
		if (migration.newerThanKnown) {
			LOGGER.warn(
				"{} has configVersion {}, newer than this build knows ({}); a backup is kept before saving",
				FILE_NAME, migration.fromVersion, migrator.currentVersion(),
			)
			backup("v" + migration.fromVersion, "it is from a newer version of the mod")
		}
		root = bind(obj)
		if (migration.changed()) {
			val preMigration = path.resolveSibling("$FILE_NAME.v0.bak")
			if (!Files.exists(preMigration) && !writeBackup(preMigration)) {
				suspendSaves("the copy made before updating it could not be written")
				return
			}
			LOGGER.info("Migrated {} from version {} (steps {})", FILE_NAME, migration.fromVersion, migration.ran)
			save()
		}
	}

	private fun parseObject(bytes: ByteArray): JsonObject? = try {
		val tree: JsonElement? = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8))
		if (tree != null && tree.isJsonObject) tree.asJsonObject else null
	} catch (e: JsonParseException) {
		LOGGER.error("{} is not valid JSON", FILE_NAME, e)
		null
	}

	/** Builds the root section by section, so one unreadable part does not reset the others. */
	private fun bind(obj: JsonObject): SkyblockUtilityConfig {
		val config = SkyblockUtilityConfig()
		config.configVersion = obj.get(ConfigMigrations.VERSION_FIELD).asInt

		val general = obj.get("general")
		if (general != null && !general.isJsonNull) {
			try {
				val parsed: GeneralConfig? = GSON.fromJson(general, GeneralConfig::class.java)
				config.general = parsed ?: GeneralConfig()
			} catch (e: JsonParseException) {
				generalUnreadable(e)
			} catch (e: IllegalStateException) {
				generalUnreadable(e)
			}
		}
		if (config.general.normalize()) {
			LOGGER.info("Cleaned up invalid values in the general section")
		}

		val modules = obj.get("modules")
		if (modules != null && modules.isJsonObject) {
			for ((key, value) in modules.asJsonObject.entrySet()) {
				config.modules[key] = value
			}
		} else if (modules != null && !modules.isJsonNull) {
			LOGGER.error("The modules section of {} is not an object, using defaults for every module", FILE_NAME)
			backup("broken", "its modules section could not be read; the modules use defaults")
		}
		return config
	}

	private fun generalUnreadable(e: RuntimeException) {
		LOGGER.error("The general section of {} is unreadable, using its defaults", FILE_NAME, e)
		backup("broken", "its general section could not be read; that section uses defaults")
	}

	/** A byte-exact copy of the file as loaded, once per session, announced after joining. */
	@JvmStatic
	@Synchronized
	private fun backup(reason: String, why: String) {
		if (backupThisSession != null || originalBytes == null) {
			return
		}
		val stamp = LocalDateTime.now().format(BACKUP_TIME)
		val base = configPath!!
		var target = base.resolveSibling("$FILE_NAME.$reason-$stamp.bak")
		var i = 1
		while (Files.exists(target)) {
			target = base.resolveSibling("$FILE_NAME.$reason-$stamp-$i.bak")
			i++
		}
		if (writeBackup(target)) {
			backupThisSession = target
			pendingNotices.add("Your settings file was copied to " + target.fileName + " because " + why + ".")
		} else {
			suspendSaves("a backup of it could not be written ($why)")
		}
	}

	private fun readWithRetries(path: Path): ByteArray? {
		var attempt = 1
		while (true) {
			try {
				return disk.read(path)
			} catch (e: IOException) {
				if (attempt == READ_ATTEMPTS) {
					LOGGER.error("Failed to read {} after {} attempts", FILE_NAME, READ_ATTEMPTS, e)
					return null
				}
				try {
					Thread.sleep(READ_RETRY_PAUSE_MS)
				} catch (interrupted: InterruptedException) {
					Thread.currentThread().interrupt()
					return null
				}
			}
			attempt++
		}
	}

	/** Keeps the file on disk as it is for the rest of the session and tells the player once. */
	private fun suspendSaves(why: String) {
		if (savesSuspended) {
			return
		}
		savesSuspended = true
		store?.discardPending()
		LOGGER.error("Not saving {} this session because {}; the file is left as it is", FILE_NAME, why)
		pendingNotices.add(
			"Your settings file was left untouched because " + why +
				". Defaults are used where needed, and changes are not saved until the next start.",
		)
	}

	private fun writeBackup(target: Path): Boolean = try {
		disk.writeBackup(target, originalBytes!!)
		LOGGER.warn("Backed up {} to {}", FILE_NAME, target.fileName)
		true
	} catch (e: IOException) {
		LOGGER.error("Could not write the backup {}", target.fileName, e)
		false
	}

	/**
	 * The file name of this session's backup, for the settings screen to name once (EC-UI-16); null
	 * when there is none or it was named already.
	 */
	@JvmStatic
	@Synchronized
	fun takeBackupForScreen(): String? {
		val backup = backupThisSession
		if (backup == null || backupNamedInScreen) {
			return null
		}
		backupNamedInScreen = true
		return backup.fileName.toString()
	}

	@JvmStatic
	@Synchronized
	fun hasNotices(): Boolean = pendingNotices.isNotEmpty()

	/** Chat notices about backups made while loading; each is returned once (shown after joining). */
	@JvmStatic
	@Synchronized
	fun drainNotices(): List<String> {
		// List.copyOf rejects a null, as before, so what it returns holds strings only.
		@Suppress("UNCHECKED_CAST")
		val notices = java.util.List.copyOf(pendingNotices) as List<String>
		pendingNotices.clear()
		return notices
	}

	/** Queues the current state for saving; never blocks on disk I/O, so it is safe on a keypress. */
	@JvmStatic
	fun save() {
		SAVE_REQUESTS.incrementAndGet()
		if (store == null) {
			load(configPath())
		}
		if (savesSuspended) {
			return
		}
		store!!.requestSave(GSON.toJson(root))
	}

	/** How often the save path has been asked to write since the game started (AC-UI-14). */
	@JvmStatic
	fun saveRequests(): Int = SAVE_REQUESTS.get()

	/** Writes any pending save now and waits for it (client shutdown, tests). */
	@JvmStatic
	fun flush() {
		store?.flush()
	}

	/** Queues a chat notice; it is shown once the player is in a world (see drainNotices). */
	@JvmStatic
	@Synchronized
	fun queueNotice(message: String?) {
		pendingNotices.add(message)
	}

	@JvmStatic
	fun setSaveFailureNotice(notice: Consumer<String>?) {
		saveFailureNotice = notice
	}

	@JvmStatic
	/** The platform type is inferred on purpose: like Java, it hands back whatever the field holds, unchecked. */
	fun general() = root!!.general

	/**
	 * The module's typed section. An unreadable section (wrong types) is backed up with the whole
	 * file and replaced by defaults, and the other sections stay as they are (REQ-CFG-06). Invalid
	 * values a module section can repair itself are cleaned up and saved (REQ-CFG-07).
	 */
	@JvmStatic
	fun <T> getModuleSection(moduleId: String?, type: Class<T>?, defaultFactory: Supplier<T>?): T {
		val raw = root!!.modules[moduleId]
		var value: T? = null
		if (raw != null && !raw.isJsonNull) {
			try {
				value = GSON.fromJson(raw, type)
			} catch (e: JsonParseException) {
				sectionUnreadable(moduleId, e)
			} catch (e: IllegalStateException) {
				sectionUnreadable(moduleId, e)
			}
		}
		if (value == null) {
			value = defaultFactory!!.get()
			putModuleSection(moduleId, value)
			save()
		} else if (value is Normalizable && value.normalize()) {
			LOGGER.info("Cleaned up invalid values in the {} section", moduleId)
			putModuleSection(moduleId, value)
			save()
		}
		// T may be nullable for Java (a factory that gives null), as before: an unchecked cast, no check.
		@Suppress("UNCHECKED_CAST")
		return value as T
	}

	private fun sectionUnreadable(moduleId: String?, e: RuntimeException) {
		LOGGER.error("The {} section of {} is unreadable, using its defaults", moduleId, FILE_NAME, e)
		backup("broken", "its $moduleId section could not be read; that module uses defaults")
	}

	@JvmStatic
	fun putModuleSection(moduleId: String?, section: Any?) {
		root!!.modules[moduleId] = GSON.toJsonTree(section)
	}
}
