package com.k8bas.skyblockutility.config;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.Map;

public class SkyblockUtilityConfig {
	public GeneralConfig general = new GeneralConfig();
	public Map<String, JsonElement> modules = new LinkedHashMap<>();
}
