package com.k8bas.skyblockutility.config.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.k8bas.skyblockutility.location.Islands;

/**
 * Step 1 (T1.9b, REQ-LOC-03, D-2): the dungeon lobby is its own island now. NPC Search rules with a
 * fixed position on "Catacombs" stand in the lobby, so they move to "Dungeon Hub". Moving-NPC rules
 * (Trinity, Tomioka, Duncan walk inside runs) and every mob rule keep "Catacombs".
 */
final class DungeonHubSplit implements MigrationStep {
	@Override
	public int number() {
		return 1;
	}

	@Override
	public String description() {
		return "fixed NPC Search rules on Catacombs move to Dungeon Hub";
	}

	@Override
	public void apply(JsonObject root) {
		if (!(root.get("modules") instanceof JsonObject modules)
				|| !(modules.get("npc_search") instanceof JsonObject npcSearch)
				|| !npcSearch.has("rules") || !npcSearch.get("rules").isJsonArray()) {
			return;
		}
		for (JsonElement element : npcSearch.getAsJsonArray("rules")) {
			if (element instanceof JsonObject rule && isTrue(rule.get("fixed")) && isString(rule.get("island"), Islands.CATACOMBS)) {
				rule.addProperty("island", Islands.DUNGEON_HUB);
			}
		}
	}

	private static boolean isTrue(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
	}

	private static boolean isString(JsonElement element, String value) {
		return element instanceof JsonPrimitive primitive && primitive.isString() && value.equals(primitive.getAsString());
	}
}
