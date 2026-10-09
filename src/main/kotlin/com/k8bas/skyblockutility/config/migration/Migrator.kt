package com.k8bas.skyblockutility.config.migration

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Brings a persisted JSON tree up to the current version (REQ-CFG-02, -09, -12): every step whose
 * number is above the file's version runs exactly once, in ascending order, and the version field
 * is then set to the current one. A file without the field counts as version 0. Reused for every
 * file the mod persists, each with its own version field and step list.
 */
class Migrator(private val versionField: String?, steps: List<MigrationStep>) {
	private val steps: List<MigrationStep>

	init {
		for (i in steps.indices) {
			require(steps[i].number() == i + 1) { "migration steps must be numbered 1.." + steps.size + " in order" }
		}
		this.steps = java.util.List.copyOf(steps)
	}

	fun currentVersion(): Int = steps.size

	/** The file's version: the integer field, or 0 when it is missing or not a number. */
	fun versionOf(root: JsonObject): Int {
		val version = root.get(versionField)
		return if (version is JsonPrimitive && version.isNumber) version.asInt else 0
	}

	/**
	 * Nullable as the Java record's components were.
	 *
	 * @param ran the numbers of the steps applied, in order
	 * @param newerThanKnown the file is from a newer version of the mod (nothing was changed)
	 */
	@JvmRecord
	data class Result(val fromVersion: Int, val ran: List<Int>?, val newerThanKnown: Boolean) {
		fun changed(): Boolean = !ran!!.isEmpty()

		override fun toString(): String = "Result[fromVersion=$fromVersion, ran=$ran, newerThanKnown=$newerThanKnown]"
	}

	/** Migrates the tree in place. */
	fun migrate(root: JsonObject): Result {
		val from = versionOf(root)
		if (from > currentVersion()) {
			return Result(from, java.util.List.of(), true)
		}
		val ran = ArrayList<Int>()
		for (step in steps) {
			if (step.number() > from) {
				step.apply(root)
				ran.add(step.number())
			}
		}
		root.addProperty(versionField, currentVersion())
		return Result(from, java.util.List.copyOf(ran), false)
	}
}
