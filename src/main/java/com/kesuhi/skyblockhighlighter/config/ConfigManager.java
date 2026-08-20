package com.kesuhi.skyblockhighlighter.config;

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

public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("skyblockhighlighter/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("skyblockhighlighter.json");

	private static ModConfig config;

	private ConfigManager() {
	}

	public static ModConfig get() {
		if (config == null) {
			load();
		}
		return config;
	}

	public static void load() {
		if (Files.exists(CONFIG_PATH)) {
			try (var reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
				config = loaded != null ? loaded : new ModConfig();
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Failed to read skyblockhighlighter.json, using defaults", e);
				config = new ModConfig();
			}
		} else {
			config = new ModConfig();
			save();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (var writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to save skyblockhighlighter.json", e);
		}
	}
}
