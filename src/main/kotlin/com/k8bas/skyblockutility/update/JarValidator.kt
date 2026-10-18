package com.k8bas.skyblockutility.update

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Checks a downloaded jar before it may be staged (REQ-UPD-15): only its `fabric.mod.json` is read, at most 64 KiB,
 * and nothing is extracted. Fabric ignores build metadata when it compares versions, so whether the jar runs on this
 * game comes from its declared dependencies, tested with the Loader's own predicates.
 */
object JarValidator {
	const val MOD_ID: String = "k8bas_skyblock_utility"
	const val MAX_MOD_JSON_BYTES: Int = 64 * 1024

	/**
	 * The reason [jar] is not this mod's build of [expectedVersion] for the running game, or null if it is.
	 *
	 * @param expectedVersion the version part of the asset name, e.g. `1.4.0+26.2`
	 * @param minecraft the running Minecraft version
	 * @param loader the running Fabric Loader version
	 */
	@JvmStatic
	fun problem(jar: Path, expectedVersion: String, minecraft: String, loader: String): String? {
		val modJson = when (val read = readModJson(jar)) {
			is JsonObject -> read
			is String -> return read
			else -> return "its fabric.mod.json cannot be read"
		}
		val id = string(modJson, "id")
		if (id != MOD_ID) {
			return "its id is ${quote(id)}, not '$MOD_ID'"
		}
		val version = string(modJson, "version")
		if (version != expectedVersion) {
			return "its version is ${quote(version)}, not '$expectedVersion'"
		}
		// Fabric reads an absent environment as "*".
		val environment = modJson.get("environment")
		if (environment != null) {
			val side = if (environment.isJsonPrimitive) environment.asString else environment.toString()
			if (side != "client" && side != "*") {
				return "it is for the environment '$side', not the client"
			}
		}
		val depends = modJson.get("depends") as? JsonObject
		return dependency(depends?.get("minecraft"), minecraft, "Minecraft")
			?: dependency(depends?.get("fabricloader"), loader, "Fabric Loader")
	}

	/** The parsed object, or the reason it could not be read. */
	private fun readModJson(jar: Path): Any = try {
		ZipFile(jar.toFile()).use { zip ->
			val entry = zip.getEntry("fabric.mod.json") ?: return "it has no fabric.mod.json"
			if (entry.isDirectory || entry.size > MAX_MOD_JSON_BYTES) {
				return "its fabric.mod.json is larger than 64 KiB"
			}
			// The declared size can lie; the read is capped either way.
			val bytes = zip.getInputStream(entry).use { it.readNBytes(MAX_MOD_JSON_BYTES + 1) }
			if (bytes.size > MAX_MOD_JSON_BYTES) {
				return "its fabric.mod.json is larger than 64 KiB"
			}
			val root = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8))
			if (!root.isJsonObject) "its fabric.mod.json is not a JSON object" else root.asJsonObject
		}
	} catch (e: ZipException) {
		"it is not a readable jar: " + e.message
	} catch (e: IOException) {
		"it is not a readable jar: " + e.javaClass.simpleName
	} catch (e: JsonParseException) {
		"its fabric.mod.json is not valid JSON"
	}

	/** A dependency as Fabric reads it: one predicate string, or an array of them of which one must match. */
	private fun dependency(declared: JsonElement?, running: String, name: String): String? {
		val predicates = when {
			declared == null || declared.isJsonNull -> return "it names no $name version"
			declared is JsonArray -> declared.map { if (it.isJsonPrimitive) it.asString else it.toString() }
			declared.isJsonPrimitive -> listOf(declared.asString)
			else -> return "its $name dependency ${declared} cannot be parsed"
		}
		val version = try {
			Version.parse(running)
		} catch (e: VersionParsingException) {
			return "the running $name version $running cannot be parsed"
		}
		for (predicate in predicates) {
			val parsed = try {
				VersionPredicate.parse(predicate)
			} catch (e: VersionParsingException) {
				return "its $name dependency '$predicate' cannot be parsed"
			}
			if (parsed.test(version)) {
				return null
			}
		}
		val shown = if (declared is JsonArray) declared.toString() else predicates.first()
		return "it needs $name $shown, not $running"
	}

	private fun string(obj: JsonObject, key: String): String? {
		val value = obj.get(key)
		return if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
	}

	private fun quote(text: String?): String = if (text == null) "(missing)" else "'$text'"
}
