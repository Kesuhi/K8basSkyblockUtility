package com.k8bas.skyblockutility.config.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Step 3 (T3.4b, REQ-NPCWP-09, R8): a fixed NPC Search rule whose colour is not the old default green
 * {@code 0x0AA351} keeps that colour as its beam colour, so no customised colour is lost; one on the default
 * follows its island's colour. Moving rules are left as they are ({@code color} stays their stored glow colour),
 * and nothing is removed, so every field of a 1.0.1 rule is still there. A rule that already has a beam colour
 * keeps it.
 */
final class BeamColorOverride implements MigrationStep {
	/** The 1.0.x NPC colour every rule started with. */
	static final int OLD_DEFAULT = 0x0AA351;

	@Override
	public int number() {
		return 3;
	}

	@Override
	public String description() {
		return "fixed NPC Search rules with their own colour keep it as their beam colour";
	}

	@Override
	public void apply(JsonObject root) {
		if (!(root.get("modules") instanceof JsonObject modules)
				|| !(modules.get("npc_search") instanceof JsonObject npcSearch)
				|| !npcSearch.has("rules") || !npcSearch.get("rules").isJsonArray()) {
			return;
		}
		for (JsonElement element : npcSearch.getAsJsonArray("rules")) {
			if (element instanceof JsonObject rule && isTrue(rule.get("fixed")) && !hasBeamColor(rule)
					&& rule.get("color") instanceof JsonPrimitive color && color.isNumber()) {
				int rgb;
				try {
					rgb = color.getAsInt() & 0xFFFFFF;
				} catch (NumberFormatException e) {
					// A number no int holds (a hand edit, a damaged file): the rule is left as it is, and binding it
					// later falls back to the section's defaults with a backup (REQ-CFG-06).
					continue;
				}
				if (rgb != OLD_DEFAULT) {
					rule.addProperty("beamColor", rgb);
				}
			}
		}
	}

	private static boolean hasBeamColor(JsonObject rule) {
		return rule.get("beamColor") instanceof JsonPrimitive beam && beam.isNumber();
	}

	private static boolean isTrue(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
	}
}
