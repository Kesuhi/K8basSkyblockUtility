package com.k8bas.skyblockutility.data

import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SchemaRegistryConfig
import com.networknt.schema.dialect.Dialect
import com.networknt.schema.dialect.Dialects
import com.networknt.schema.keyword.AnnotationKeyword
import com.networknt.schema.keyword.KeywordFactory
import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.function.Function
import kotlin.system.exitProcess

/**
 * The bundled-data validator (T3.0i; REQ-DATA-02, -03, -04, -06, -12). `check` runs it through
 * BundledDataValidationTest, and `./gradlew validateData` on its own. For every table file it checks the
 * REQ-DATA-02 envelope (data-schemas/envelope.schema.json) and the table's own JSON Schema (draft 2020-12),
 * duplicate object keys, duplicate primary keys (the schema's x-keys), cross-table references (a string
 * `<table>:<key>` whose table is indexed must name one of its keys), `§` and text over 80 characters outside
 * the schema's x-prose fields, and the index: an entry per file and a file per entry, one file per table id,
 * and the checksum, row count, schemaVersion, dataVersion and gameVersion the file has. Each problem names
 * the table and the offending key.
 */
object DataValidator {
	/** One problem: the table (or file), the offending key or JSON pointer, and what is wrong. */
	data class Problem(val table: String, val key: String, val message: String) {
		override fun toString() = "$table: $key: $message"
	}

	/** The problems and the size of each table file in bytes (REQ-DATA-12). */
	data class Report(val problems: List<Problem>, val bytes: Map<String, Long>)

	private const val MAX_PROSE = 80
	/** `<table id>:<key>`: a table id (dots allowed, as in `ref.skills`) and a key without spaces or colons. */
	private val REFERENCE = Regex("^([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)*):([^\\s:]+)$")

	private val REGISTRY: SchemaRegistry = SchemaRegistry.withDefaultDialect(
		// x-keys and x-prose are this project's annotations: read by this validator, ignored by the schema check.
		Dialect.builder(Dialects.getDraft202012()).unknownKeywordFactory(KeywordFactory { name, _ -> AnnotationKeyword(name) }).build(),
	) { builder ->
		builder.schemaRegistryConfig(SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build())
			.nodeReader { reader -> reader.jsonMapper(DataFiles.MAPPER) }
	}

	@JvmStatic
	fun main(args: Array<String>) {
		val project = Path.of(args.firstOrNull() ?: ".")
		val data = project.resolve(DataIndexer.DATA)
		val report = validate(data, project.resolve(DataIndexer.SCHEMAS))
		report.bytes.forEach { (table, bytes) -> println("validateData: $table $bytes bytes") }
		val problems = ArrayList(report.problems)
		if (args.size > 1) {
			val base = baseTables(project, args[1])
			if (base == null) {
				problems += Problem("baseRef", args[1], "not a commit in this repository (fetch it first)")
			} else {
				problems += checkDataVersions(data) { id -> base[id] }
			}
		}
		problems.forEach { System.err.println("validateData: $it") }
		println("validateData: ${report.bytes.size} table(s), ${problems.size} problem(s)")
		if (problems.isNotEmpty()) {
			exitProcess(1)
		}
	}

	/** Validates the tables of [data] against the schemas in [schemas] and the index. */
	@JvmStatic
	fun validate(data: Path, schemas: Path): Report {
		val problems = ArrayList<Problem>()
		val files = DataFiles.tables(data)
		val index = readIndex(data, problems)
		val envelope = schema(schemas, DataFiles.ENVELOPE, problems)

		// One file per table id, and an index entry per file and a file per entry.
		files.groupBy { it.id }.filter { it.value.size > 1 }.forEach { (id, same) ->
			problems += Problem(id, "table", "declared by ${same.joinToString { it.path }}")
		}
		for (file in files) {
			if (index.none { it.value.get("path")?.stringValue() == file.path }) {
				problems += Problem(file.id, "index", "${file.path} is not listed in ${DataFiles.INDEX}")
			}
			if (!DataText.isSafePath(file.path)) {
				problems += Problem(file.id, "path", "${file.path} is not a path the game reads (letters, digits, _ . / -)")
			}
		}
		for ((id, entry) in index) {
			val path = entry.get("path")?.takeIf { it.isString }?.stringValue()
			if (path == null || !Files.isRegularFile(data.resolve(path))) {
				problems += Problem(id, "path", "${DataFiles.INDEX} lists $path, which does not exist")
				continue
			}
			// The game looks a table up by this id and then wants the same id in the file.
			val file = files.firstOrNull { it.path == path } ?: continue
			if (file.id != id) {
				problems += Problem(id, "table", "$path declares table ${file.id}, not $id")
			}
		}

		val keysByTable = HashMap<String, Set<String>>()
		val parsed = files.filter { it.json != null }
		for (file in files) {
			if (file.json == null) {
				problems += Problem(file.id, duplicateKey(file.parseError), "not valid JSON: ${file.parseError}")
			}
		}
		val schemasById = HashMap<String, JsonNode?>()
		for (file in parsed) {
			val json = file.json!!
			val tableSchema = DataFiles.schema(schemas, file.id)
			schemasById[file.id] = tableSchema
			envelope?.let { problems += check(file.id, it, json) }
			if (tableSchema == null) {
				problems += Problem(file.id, "schema", "no ${file.id}.schema.json in the schema folder")
			} else {
				problems += check(file.id, REGISTRY.getSchema(SchemaLocation.of("https://k8bas.invalid/data-schemas/${file.id}.schema.json"), tableSchema), json)
			}
			keysByTable[file.id] = primaryKeys(file.id, json, tableSchema, problems)
			text(file.id, json, "", "", DataFiles.prose(tableSchema), problems)
			index[file.id]?.takeIf { it.get("path")?.stringValue() == file.path }?.let { entry -> matchIndex(file, json, tableSchema, entry, problems) }
		}
		for (file in parsed) {
			references(file.id, file.json!!, "", "", DataFiles.prose(schemasById[file.id]), keysByTable, problems)
		}
		return Report(java.util.List.copyOf(problems), files.associate { it.id to it.bytes.size.toLong() })
	}

	/**
	 * The -PbaseRef guard: a table whose text differs from its text in the base ([base] by table id, so a
	 * moved table is still found) must have the base's dataVersion + 1; a table not in the base is exempt.
	 */
	@JvmStatic
	fun checkDataVersions(data: Path, base: Function<String, String?>): List<Problem> {
		val problems = ArrayList<Problem>()
		for (file in DataFiles.tables(data)) {
			val before = base.apply(file.id) ?: continue
			val old = DataFiles.read(file.path, before.toByteArray(Charsets.UTF_8))
			if (DataText.checksum(old.bytes) == DataText.checksum(file.bytes)) {
				continue
			}
			val was = old.json?.get("dataVersion")?.takeIf { it.isIntegralNumber }?.intValue()
			val now = file.json?.get("dataVersion")?.takeIf { it.isIntegralNumber }?.intValue()
			if (was == null || now != was + 1) {
				problems += Problem(file.id, "dataVersion", "changed since the base, so dataVersion must be ${was?.plus(1)}, not $now")
			}
		}
		return problems
	}

	private fun readIndex(data: Path, problems: MutableList<Problem>): Map<String, JsonNode> {
		val file = data.resolve(DataFiles.INDEX)
		if (!Files.isRegularFile(file)) {
			problems += Problem("index", DataFiles.INDEX, "missing")
			return mapOf()
		}
		val index = DataFiles.read(DataFiles.INDEX, Files.readAllBytes(file))
		val tables = index.json?.get("tables")
		if (index.json?.get("indexVersion")?.takeIf { it.isIntegralNumber }?.intValue() != 1 || tables == null || !tables.isObject) {
			problems += Problem("index", DataFiles.INDEX, "not an indexVersion 1 index${index.parseError?.let { ": $it" } ?: ""}")
			return mapOf()
		}
		return tables.properties().associate { (id, entry) -> id to entry }
	}

	private fun schema(schemas: Path, name: String, problems: MutableList<Problem>): Schema? {
		val file = schemas.resolve(name)
		if (!Files.isRegularFile(file)) {
			problems += Problem("schemas", name, "missing")
			return null
		}
		return REGISTRY.getSchema(SchemaLocation.of("https://k8bas.invalid/data-schemas/$name"), DataFiles.MAPPER.readTree(Files.readString(file)))
	}

	private fun check(table: String, schema: Schema, json: JsonNode): List<Problem> = schema.validate(json).map { error ->
		val at = error.instanceLocation.toString()
		val key = if (error.property != null && !at.endsWith("/${error.property}")) "$at/${error.property}" else at
		Problem(table, key.ifEmpty { "/" }, "${error.keyword}: ${error.message}")
	}

	/** Collects each x-keys array's keys and reports a duplicate; returns every key of the table. */
	private fun primaryKeys(table: String, json: JsonNode, schema: JsonNode?, problems: MutableList<Problem>): Set<String> {
		val all = HashSet<String>()
		for ((pointer, field) in DataFiles.keys(schema)) {
			val arrays = DataFiles.arrays(json, pointer)
			if (arrays.isEmpty() && !pointer.contains('*')) {
				problems += Problem(table, "x-keys", "$pointer matches no array in the table")
			}
			for ((at, array) in arrays) {
				val seen = HashSet<String>()
				for (row in array.values()) {
					val key = row.get(field)?.takeIf { it.isString || it.isNumber }?.asString() ?: continue
					if (!seen.add(key)) {
						problems += Problem(table, "$at[$field=$key]", "duplicate primary key")
					}
					all += key
				}
			}
		}
		return all
	}

	/** `§` anywhere, and text over 80 characters in a field not on [prose]. */
	private fun text(table: String, node: JsonNode, at: String, field: String, prose: Set<String>, problems: MutableList<Problem>) {
		when {
			node.isObject -> node.properties().forEach { (name, child) ->
				if (name.contains('§')) {
					problems += Problem(table, "$at/$name", "contains a formatting code (§) in a key")
				}
				text(table, child, "$at/$name", name, prose, problems)
			}
			node.isArray -> node.values().forEachIndexed { i, child -> text(table, child, "$at/$i", field, prose, problems) }
			node.isString -> {
				val value = node.stringValue()
				if (value.contains('§')) {
					problems += Problem(table, at, "contains a formatting code (§)")
				}
				if (value.length > MAX_PROSE && field !in prose) {
					problems += Problem(table, at, "${value.length} characters; over $MAX_PROSE only in the schema's x-prose fields")
				}
			}
		}
	}

	/**
	 * A string `<table>:<key>` whose table is one of [keys]' must name one of that table's keys. Text in an
	 * x-prose field is never a reference.
	 */
	private fun references(table: String, node: JsonNode, at: String, field: String, prose: Set<String>, keys: Map<String, Set<String>>, problems: MutableList<Problem>) {
		when {
			node.isObject -> node.properties().forEach { (name, child) -> references(table, child, "$at/$name", name, prose, keys, problems) }
			node.isArray -> node.values().forEachIndexed { i, child -> references(table, child, "$at/$i", field, prose, keys, problems) }
			node.isString && field !in prose -> {
				val match = REFERENCE.matchEntire(node.stringValue()) ?: return
				val target = keys[match.groupValues[1]] ?: return
				if (match.groupValues[2] !in target) {
					problems += Problem(table, "$at=${node.stringValue()}", "refers to a key that table ${match.groupValues[1]} does not have")
				}
			}
		}
	}

	private fun matchIndex(file: TableFile, json: JsonNode, schema: JsonNode?, entry: JsonNode, problems: MutableList<Problem>) {
		fun differs(key: String, actual: Any?, listed: Any?) {
			if (actual != listed) {
				problems += Problem(file.id, key, "${DataFiles.INDEX} lists $listed, the file has $actual (run ./gradlew dataIndex)")
			}
		}
		differs("sha256", DataText.checksum(file.bytes), entry.get("sha256")?.stringValue())
		differs("rows", DataFiles.rows(json, schema), entry.get("rows")?.takeIf { it.isIntegralNumber }?.intValue())
		for (key in listOf("schemaVersion", "dataVersion")) {
			differs(key, json.get(key)?.takeIf { it.isIntegralNumber }?.intValue(), entry.get(key)?.takeIf { it.isIntegralNumber }?.intValue())
		}
		differs("gameVersion", json.get("gameVersion")?.takeIf { it.isString }?.stringValue(), entry.get("gameVersion")?.stringValue())
	}

	/** The key a strict parser names in a duplicate-key error, else "json". */
	private fun duplicateKey(error: String?): String = error?.let { Regex("Duplicate Object property \"([^\"]+)\"").find(it)?.groupValues?.get(1) } ?: "json"

	/** Every table of the data folder at [ref], by table id; null if [ref] is not a commit here. */
	private fun baseTables(project: Path, ref: String): Map<String, String>? {
		git(project, "rev-parse", "--verify", "--quiet", "$ref^{commit}") ?: return null
		val paths = git(project, "ls-tree", "-r", "--name-only", ref, "--", DataIndexer.DATA) ?: return null
		val tables = HashMap<String, String>()
		for (path in paths.lines().filter { it.endsWith(".json") && !it.endsWith("/" + DataFiles.INDEX) }) {
			val text = git(project, "show", "$ref:$path") ?: continue
			tables[DataFiles.read(path, text.toByteArray(Charsets.UTF_8)).id] = text
		}
		return tables
	}

	/** The output of a git command, or null if it failed. */
	private fun git(project: Path, vararg args: String): String? {
		val process = ProcessBuilder(listOf("git") + args).directory(project.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
		val text = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
		return if (process.waitFor() == 0) text else null
	}
}
