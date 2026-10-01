package com.k8bas.skyblockutility.config;

import com.google.gson.JsonElement;
import com.k8bas.skyblockutility.config.migration.ConfigMigrations;

import java.util.LinkedHashMap;
import java.util.Map;

public class SkyblockUtilityConfig {
	/** The file format version (REQ-CFG-02); a file without it is a 1.0.x file (version 0). */
	public int configVersion = ConfigMigrations.MIGRATOR.currentVersion();
	public GeneralConfig general = new GeneralConfig();
	public Map<String, JsonElement> modules = new LinkedHashMap<>();
}
