package com.k8bas.skyblockutility.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./gradlew dataIndex` (T3.0i): rewrites `data/index.json` from the table files in the REQ-DATA-04 format
 * the runtime registry reads: path, checksum ([DataText.checksum]), row count (the x-keys arrays), and the
 * file's schemaVersion, dataVersion and gameVersion. It never edits a table and writes what it finds; the
 * validator judges it. A file it cannot parse, or one without a table id, gets no entry.
 */
object DataIndexer {
	const val DATA = "src/main/resources/assets/k8bas_skyblock_utility/data"
	const val SCHEMAS = "data-schemas"

	@JvmStatic
	fun main(args: Array<String>) {
		val project = Path.of(args.firstOrNull() ?: ".")
		val written = writeIndex(project.resolve(DATA), project.resolve(SCHEMAS))
		println("dataIndex: $written table(s) in ${project.resolve(DATA).resolve(DataFiles.INDEX)}")
	}

	/** Writes the index of [data] and returns how many tables it lists. */
	@JvmStatic
	fun writeIndex(data: Path, schemas: Path): Int {
		val tables = JsonObject()
		for (file in DataFiles.tables(data).sortedBy { it.id }) {
			val json = file.json ?: continue
			if (file.id == file.path || tables.has(file.id)) {
				continue
			}
			val entry = JsonObject()
			entry.addProperty("path", file.path)
			entry.addProperty("sha256", DataText.checksum(file.bytes))
			entry.addProperty("rows", DataFiles.rows(json, DataFiles.schema(schemas, file.id)))
			entry.addProperty("schemaVersion", json.get("schemaVersion")?.takeIf { it.isIntegralNumber }?.intValue() ?: 0)
			entry.addProperty("dataVersion", json.get("dataVersion")?.takeIf { it.isIntegralNumber }?.intValue() ?: 0)
			entry.addProperty("gameVersion", json.get("gameVersion")?.takeIf { it.isString }?.stringValue() ?: "")
			tables.add(file.id, entry)
		}
		val index = JsonObject()
		index.addProperty("indexVersion", 1)
		index.add("tables", tables)
		Files.createDirectories(data)
		val text = GsonBuilder().setPrettyPrinting().create().toJson(index) + "\n"
		Files.writeString(data.resolve(DataFiles.INDEX), text, StandardCharsets.UTF_8)
		return tables.size()
	}
}
