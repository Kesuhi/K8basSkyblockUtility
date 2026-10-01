package com.k8bas.skyblockutility.config.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Brings a persisted JSON tree up to the current version (REQ-CFG-02, -09, -12): every step whose
 * number is above the file's version runs exactly once, in ascending order, and the version field
 * is then set to the current one. A file without the field counts as version 0. Reused for every
 * file the mod persists, each with its own version field and step list.
 */
public final class Migrator {
	private final String versionField;
	private final List<MigrationStep> steps;

	public Migrator(String versionField, List<MigrationStep> steps) {
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).number() != i + 1) {
				throw new IllegalArgumentException("migration steps must be numbered 1.." + steps.size() + " in order");
			}
		}
		this.versionField = versionField;
		this.steps = List.copyOf(steps);
	}

	public int currentVersion() {
		return steps.size();
	}

	/** The file's version: the integer field, or 0 when it is missing or not a number. */
	public int versionOf(JsonObject root) {
		JsonElement version = root.get(versionField);
		return version instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : 0;
	}

	/**
	 * @param ran the numbers of the steps applied, in order
	 * @param newerThanKnown the file is from a newer version of the mod (nothing was changed)
	 */
	public record Result(int fromVersion, List<Integer> ran, boolean newerThanKnown) {
		public boolean changed() {
			return !ran.isEmpty();
		}
	}

	/** Migrates the tree in place. */
	public Result migrate(JsonObject root) {
		int from = versionOf(root);
		if (from > currentVersion()) {
			return new Result(from, List.of(), true);
		}
		List<Integer> ran = new ArrayList<>();
		for (MigrationStep step : steps) {
			if (step.number() > from) {
				step.apply(root);
				ran.add(step.number());
			}
		}
		root.addProperty(versionField, currentVersion());
		return new Result(from, List.copyOf(ran), false);
	}
}
