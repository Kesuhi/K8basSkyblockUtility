package com.k8bas.skyblockutility.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Owns the single config file and its root shape (general section + one JSON blob per
 * module). Deliberately doesn't know any individual module's config class at compile
 * time — modules ask for their own typed section and hand back an updated one to persist,
 * which is what keeps each module self-contained instead of this class growing a
 * per-module if-chain as more modules get added.
 */
public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("k8bas_skyblock_utility.json");

	private static SkyblockUtilityConfig root;

	private ConfigManager() {
	}

	public static void load() {
		if (Files.exists(CONFIG_PATH)) {
			try (var reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				SkyblockUtilityConfig loaded = GSON.fromJson(reader, SkyblockUtilityConfig.class);
				root = loaded != null ? loaded : new SkyblockUtilityConfig();
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Failed to read k8bas_skyblock_utility.json, using defaults", e);
				root = new SkyblockUtilityConfig();
			}
		} else {
			root = new SkyblockUtilityConfig();
			save();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (var writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to save k8bas_skyblock_utility.json", e);
		}
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
