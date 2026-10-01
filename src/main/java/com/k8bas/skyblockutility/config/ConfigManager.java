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
 */
public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "k8bas_skyblock_utility.json";

	/** Resolved on first use rather than at class load, so tests can point load(Path) at a
	 *  temporary file without a running Fabric Loader. */
	private static Path configPath;
	private static SkyblockUtilityConfig root;
	private static AtomicFileStore store;
	/** Shows a save failure to the player; set by the client entrypoint (no-op in tests). */
	private static Consumer<String> saveFailureNotice = message -> {
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
		configPath = path;
		if (store == null || !store.file().equals(path)) {
			store = new AtomicFileStore(path, e -> saveFailureNotice.accept(
					"Couldn't save settings (" + e.getMessage() + "). They are kept for now; the next change retries."));
		}
		if (Files.exists(path)) {
			try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement tree = JsonParser.parseReader(reader);
				if (tree != null && tree.isJsonObject()) {
					// Migrate the tree before binding it: Gson would give a missing configVersion the
					// current default, hiding that the file is older.
					JsonObject object = tree.getAsJsonObject();
					Migrator.Result migration = ConfigMigrations.MIGRATOR.migrate(object);
					if (migration.newerThanKnown()) {
						LOGGER.warn("k8bas_skyblock_utility.json has configVersion {}, newer than this build knows ({})",
								migration.fromVersion(), ConfigMigrations.MIGRATOR.currentVersion());
					}
					SkyblockUtilityConfig loaded = GSON.fromJson(object, SkyblockUtilityConfig.class);
					root = loaded != null ? loaded : new SkyblockUtilityConfig();
					if (migration.changed()) {
						LOGGER.info("Migrated k8bas_skyblock_utility.json from version {} (steps {})", migration.fromVersion(), migration.ran());
						save();
					}
				} else {
					root = new SkyblockUtilityConfig();
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Failed to read k8bas_skyblock_utility.json, using defaults", e);
				root = new SkyblockUtilityConfig();
			}
		} else {
			root = new SkyblockUtilityConfig();
			save();
		}
	}

	/** Queues the current state for saving; never blocks on disk I/O, so it is safe on a keypress. */
	public static void save() {
		if (store == null) {
			load(configPath());
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

	public static <T> T getModuleSection(String moduleId, Class<T> type, Supplier<T> defaultFactory) {
		var raw = root.modules.get(moduleId);
		T value = raw != null ? GSON.fromJson(raw, type) : null;
		if (value == null) {
			value = defaultFactory.get();
			putModuleSection(moduleId, value);
			save();
		}
		return value;
	}

	public static void putModuleSection(String moduleId, Object section) {
		root.modules.put(moduleId, GSON.toJsonTree(section));
	}
}
