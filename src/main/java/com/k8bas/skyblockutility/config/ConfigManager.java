package com.k8bas.skyblockutility.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.migration.ConfigMigrations;
import com.k8bas.skyblockutility.config.migration.Migrator;
import com.k8bas.skyblockutility.config.store.AtomicFileStore;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
 */
public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "k8bas_skyblock_utility.json";
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/** Resolved on first use rather than at class load, so tests can point load(Path) at a
	 *  temporary file without a running Fabric Loader. */
	private static Path configPath;
	private static SkyblockUtilityConfig root;
	private static AtomicFileStore store;
	/** The bytes the file had when it was loaded, for a backup taken later (a broken module section
	 *  only shows when its module reads it). */
	private static byte[] originalBytes;
	private static Path backupThisSession;
	private static final List<String> pendingNotices = new ArrayList<>();
	/** Shows a save failure to the player; set by the client entrypoint (no-op in tests). */
	private static Consumer<String> saveFailureNotice = message -> {
	};
	/** Set when the file could not be read or backed up: saving would lose the user's file. */
	private static volatile boolean savesSuspended;
	static final int READ_ATTEMPTS = 3;
	static final long READ_RETRY_PAUSE_MS = 150;

	/** Reads and writes the file system; replaceable in tests. */
	interface Disk {
		byte[] read(Path path) throws IOException;

		void writeBackup(Path path, byte[] bytes) throws IOException;
	}

	static Disk disk = new Disk() {
		@Override
		public byte[] read(Path path) throws IOException {
			return Files.readAllBytes(path);
		}

		@Override
		public void writeBackup(Path path, byte[] bytes) throws IOException {
			Files.write(path, bytes);
		}
	};

	private ConfigManager() {
	}

	private static Path configPath() {
		if (configPath == null) {
			configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		}
		return configPath;
	}

	public static void load() {
		load(configPath());
	}

	/** Loads (or creates) the config at the given path; later saves go to the same path. */
	public static void load(Path path) {
		load(path, ConfigMigrations.MIGRATOR);
	}

	static synchronized void load(Path path, Migrator migrator) {
		configPath = path;
		if (store == null || !store.file().equals(path)) {
			store = new AtomicFileStore(path, e -> saveFailureNotice.accept(
					"Couldn't save settings (" + e.getMessage() + "). They are kept for now; the next change retries."));
		}
		originalBytes = null;
		backupThisSession = null;
		savesSuspended = false;
		pendingNotices.clear();
		if (!Files.exists(path)) {
			root = new SkyblockUtilityConfig();
			save();
			return;
		}
		originalBytes = readWithRetries(path);
		if (originalBytes == null) {
			// A locked or unreadable file (antivirus, sync tool, editor) is not a broken one: keep it.
			root = new SkyblockUtilityConfig();
			suspendSaves("it could not be read");
			return;
		}
		JsonObject object = parseObject(originalBytes);
		if (object == null) {
			// Invalid JSON, a 0-byte file or no JSON object at all (EC-CFG-01).
			backup("broken", "it could not be read; defaults are in use");
			root = new SkyblockUtilityConfig();
			return;
		}

		// Migrate the tree before binding it: Gson would give a missing configVersion the current
		// default, hiding that the file is older.
		Migrator.Result migration = migrator.migrate(object);
		if (migration.newerThanKnown()) {
			LOGGER.warn("{} has configVersion {}, newer than this build knows ({}); a backup is kept before saving",
					FILE_NAME, migration.fromVersion(), migrator.currentVersion());
			backup("v" + migration.fromVersion(), "it is from a newer version of the mod");
		}
		root = bind(object);
		if (migration.changed()) {
			Path preMigration = path.resolveSibling(FILE_NAME + ".v0.bak");
			if (!Files.exists(preMigration) && !writeBackup(preMigration)) {
				suspendSaves("the copy made before updating it could not be written");
				return;
			}
			LOGGER.info("Migrated {} from version {} (steps {})", FILE_NAME, migration.fromVersion(), migration.ran());
			save();
		}
	}

	private static JsonObject parseObject(byte[] bytes) {
		try {
			JsonElement tree = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
			return tree != null && tree.isJsonObject() ? tree.getAsJsonObject() : null;
		} catch (JsonParseException e) {
			LOGGER.error("{} is not valid JSON", FILE_NAME, e);
			return null;
		}
	}

	/** Builds the root section by section, so one unreadable part does not reset the others. */
	private static SkyblockUtilityConfig bind(JsonObject object) {
		SkyblockUtilityConfig config = new SkyblockUtilityConfig();
		config.configVersion = object.get(ConfigMigrations.VERSION_FIELD).getAsInt();

		JsonElement general = object.get("general");
		if (general != null && !general.isJsonNull()) {
			try {
				GeneralConfig parsed = GSON.fromJson(general, GeneralConfig.class);
				config.general = parsed != null ? parsed : new GeneralConfig();
			} catch (JsonParseException | IllegalStateException e) {
				LOGGER.error("The general section of {} is unreadable, using its defaults", FILE_NAME, e);
				backup("broken", "its general section could not be read; that section uses defaults");
			}
		}
		if (config.general.normalize()) {
			LOGGER.info("Cleaned up invalid values in the general section");
		}

		JsonElement modules = object.get("modules");
		if (modules != null && modules.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : modules.getAsJsonObject().entrySet()) {
				config.modules.put(entry.getKey(), entry.getValue());
			}
		} else if (modules != null && !modules.isJsonNull()) {
			LOGGER.error("The modules section of {} is not an object, using defaults for every module", FILE_NAME);
			backup("broken", "its modules section could not be read; the modules use defaults");
		}
		return config;
	}

	/** A byte-exact copy of the file as loaded, once per session, announced after joining. */
	private static synchronized void backup(String reason, String why) {
		if (backupThisSession != null || originalBytes == null) {
			return;
		}
		String stamp = LocalDateTime.now().format(BACKUP_TIME);
		Path target = configPath.resolveSibling(FILE_NAME + "." + reason + "-" + stamp + ".bak");
		for (int i = 1; Files.exists(target); i++) {
			target = configPath.resolveSibling(FILE_NAME + "." + reason + "-" + stamp + "-" + i + ".bak");
		}
		if (writeBackup(target)) {
			backupThisSession = target;
			pendingNotices.add("Your settings file was copied to " + target.getFileName() + " because " + why + ".");
		} else {
			suspendSaves("a backup of it could not be written (" + why + ")");
		}
	}

	private static byte[] readWithRetries(Path path) {
		for (int attempt = 1; ; attempt++) {
			try {
				return disk.read(path);
			} catch (IOException e) {
				if (attempt == READ_ATTEMPTS) {
					LOGGER.error("Failed to read {} after {} attempts", FILE_NAME, READ_ATTEMPTS, e);
					return null;
				}
				try {
					Thread.sleep(READ_RETRY_PAUSE_MS);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return null;
				}
			}
		}
	}

	/** Keeps the file on disk as it is for the rest of the session and tells the player once. */
	private static void suspendSaves(String why) {
		if (savesSuspended) {
			return;
		}
		savesSuspended = true;
		LOGGER.error("Not saving {} this session because {}; the file is left as it is", FILE_NAME, why);
		pendingNotices.add("Your settings file was left untouched because " + why
				+ ". Defaults are used where needed, and changes are not saved until the next start.");
	}

	private static boolean writeBackup(Path target) {
		try {
			disk.writeBackup(target, originalBytes);
			LOGGER.warn("Backed up {} to {}", FILE_NAME, target.getFileName());
			return true;
		} catch (IOException e) {
			LOGGER.error("Could not write the backup {}", target.getFileName(), e);
			return false;
		}
	}

	public static synchronized boolean hasNotices() {
		return !pendingNotices.isEmpty();
	}

	/** Chat notices about backups made while loading; each is returned once (shown after joining). */
	public static synchronized List<String> drainNotices() {
		List<String> notices = List.copyOf(pendingNotices);
		pendingNotices.clear();
		return notices;
	}

	/** Queues the current state for saving; never blocks on disk I/O, so it is safe on a keypress. */
	public static void save() {
		if (store == null) {
			load(configPath());
		}
		if (savesSuspended) {
			return;
		}
		store.requestSave(GSON.toJson(root));
	}

	/** Writes any pending save now and waits for it (client shutdown, tests). */
	public static void flush() {
		if (store != null) {
			store.flush();
		}
	}

	public static void setSaveFailureNotice(Consumer<String> notice) {
		saveFailureNotice = notice;
	}

	public static GeneralConfig general() {
		return root.general;
	}

	/**
	 * The module's typed section. An unreadable section (wrong types) is backed up with the whole
	 * file and replaced by defaults, and the other sections stay as they are (REQ-CFG-06). Invalid
	 * values a module section can repair itself are cleaned up and saved (REQ-CFG-07).
	 */
	public static <T> T getModuleSection(String moduleId, Class<T> type, Supplier<T> defaultFactory) {
		JsonElement raw = root.modules.get(moduleId);
		T value = null;
		if (raw != null && !raw.isJsonNull()) {
			try {
				value = GSON.fromJson(raw, type);
			} catch (JsonParseException | IllegalStateException e) {
				LOGGER.error("The {} section of {} is unreadable, using its defaults", moduleId, FILE_NAME, e);
				backup("broken", "its " + moduleId + " section could not be read; that module uses defaults");
			}
		}
		if (value == null) {
			value = defaultFactory.get();
			putModuleSection(moduleId, value);
			save();
		} else if (value instanceof Normalizable normalizable && normalizable.normalize()) {
			LOGGER.info("Cleaned up invalid values in the {} section", moduleId);
			putModuleSection(moduleId, value);
			save();
		}
		return value;
	}

	public static void putModuleSection(String moduleId, Object section) {
		root.modules.put(moduleId, GSON.toJsonTree(section));
	}
}
