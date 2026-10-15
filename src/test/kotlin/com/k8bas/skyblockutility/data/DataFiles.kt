package com.k8bas.skyblockutility.data

import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** One table file of the data folder, read and parsed strictly (a duplicate object key is an error). */
internal class TableFile(val path: String, val bytes: ByteArray, val json: JsonNode?, val parseError: String?) {
	/** The table id from the file (from its text when it does not parse), or its path when it has none. */
	val id: String = json?.get("table")?.takeIf { it.isString }?.stringValue()
		?: TABLE.find(DataText.text(bytes))?.groupValues?.get(1)
		?: path

	private companion object {
		val TABLE = Regex("\"table\"\\s*:\\s*\"([^\"]+)\"")
	}
}

/**
 * Reading the data folder for the validator and the index tool (T3.0i): table files, schemas, the x-keys
 * annotation (row arrays and their primary-key field) and JSON pointers with `*` for every member. Test
 * and tool code only; the runtime registry (T3.0c) reads tables itself.
 */
internal object DataFiles {
	const val INDEX = "index.json"
	const val ENVELOPE = "envelope.schema.json"

	/** Rejects a duplicate key in an object, which a lenient parser would silently drop. */
	val MAPPER: JsonMapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

	/** Every table file under [data] (all `.json` but the index), by path. */
	fun tables(data: Path): List<TableFile> {
		if (!Files.isDirectory(data)) {
			return listOf()
		}
		Files.walk(data).use { walk ->
			return walk.filter { it.isRegularFile() && it.extension == "json" && !(it.parent == data && it.name == INDEX) }
				.map { file -> read(data.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file)) }
				.sorted(compareBy { it.path })
				.toList()
		}
	}

	fun read(path: String, bytes: ByteArray): TableFile = try {
		TableFile(path, bytes, MAPPER.readTree(DataText.text(bytes)), null)
	} catch (e: JacksonException) {
		TableFile(path, bytes, null, e.originalMessage)
	}

	/** The schema of a table, or null if `<id>.schema.json` is missing. */
	fun schema(schemas: Path, id: String): JsonNode? {
		val file = schemas.resolve("$id.schema.json")
		return if (Files.isRegularFile(file)) MAPPER.readTree(DataText.text(Files.readAllBytes(file))) else null
	}

	/** The schema's x-keys: row-array pointer to the field that is its primary key. */
	fun keys(schema: JsonNode?): Map<String, String> {
		val keys = schema?.get("x-keys") ?: return mapOf()
		return keys.properties().associate { (pointer, field) -> pointer to field.stringValue() }
	}

	/** The schema's x-prose: field names allowed to hold more than 80 characters. */
	fun prose(schema: JsonNode?): Set<String> = schema?.get("x-prose")?.values()?.map { it.stringValue() }?.toSet() ?: setOf()

	/** The arrays [pointer] names in [root], with their concrete pointers; `*` stands for every member. */
	fun arrays(root: JsonNode, pointer: String): List<Pair<String, JsonNode>> {
		var nodes = listOf("" to root)
		for (segment in pointer.split('/').drop(1)) {
			nodes = nodes.flatMap { (at, node) ->
				when {
					segment == "*" && node.isArray -> node.values().mapIndexed { i, child -> "$at/$i" to child }
					segment == "*" && node.isObject -> node.properties().map { (name, child) -> "$at/$name" to child }
					node.isObject && node.has(segment) -> listOf("$at/$segment" to node.get(segment))
					else -> listOf()
				}
			}
		}
		return nodes.filter { it.second.isArray }
	}

	/** The row count of a table: the elements of every x-keys array. */
	fun rows(json: JsonNode, schema: JsonNode?): Int = keys(schema).keys.sumOf { pointer -> arrays(json, pointer).sumOf { it.second.size() } }
}
