package com.k8bas.skyblockutility.config;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * `general.hud` (REQ-HUD-03, REQ-CFG-12): each element's position by element id, as written. Kept as raw
 * JSON so an entry for an unknown id or a malformed one survives a save unchanged (EC-HUD-04, EC-HUD-05);
 * HudPositions reads them.
 */
public class HudConfig {
	public Map<String, JsonElement> positions = new LinkedHashMap<>();
}
