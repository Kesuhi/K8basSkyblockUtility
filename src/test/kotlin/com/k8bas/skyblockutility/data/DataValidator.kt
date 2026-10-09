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
 *
 * Licences (T3.0j, REQ-DATA-05, REQ-XC-LICENSE-03, -05): a table not CC0-1.0 lives under
 * `thirdparty/<upstream>/` of an upstream approved in data-schemas/upstreams.json, with that upstream's
 * licence; each upstream used has a THIRD_PARTY_NOTICES.md section that names its files and its licence text in
 * licenses/<upstream>.txt (the build copies it into the jar's META-INF/licenses/); an MIT or Apache source
 * names an approved upstream as notice; no NC or SA licence anywhere, and no NEU george.json; a source on a
 * wiki, Fandom, hypixel.net or Reddit page is one a person supplied (D-16, D-28).
 */
object DataValidator {
	/** One problem: the table (or file), the offending key or JSON pointer, and what is wrong. */
	data class Problem(val table: String, val key: String, val message: String) {
		override fun toString() = "$table: $key: $message"
	}

	/** The problems, the size of each table file in bytes (REQ-DATA-12) and the upstreams whose licence texts the jar must carry. */
	data class Report(val problems: List<Problem>, val bytes: Map<String, Long>, val upstreams: Set<String>)

	private const val MAX_PROSE = 80
	/** `<table id>:<key>`: a table id (dots allowed, as in `ref.skills`) and a key without spaces or colons. */
	/** An NC or SA licence id, e.g. CC-BY-NC-SA-3.0: never shipped. */
	private val NC_SA = Regex("(?i)(^|[-_ ])(NC|SA)([-_ ]|$)|NonCommercial|Non-Commercial|ShareAlike|Share-Alike")

	/** Hosts used only as pages a person cited (D-16, D-28); the official API host is not one of them. */
	private val CITED_ONLY = listOf("hypixel.net", "fandom.com", "minecraft.wiki", "reddit.com", "redd.it")

	/** Source kinds that are wiki, forum or Reddit pages, with or without a url. */
	private val CITED_KINDS = setOf("wiki_crosscheck", "forum", "reddit")
	private const val OFFICIAL_API = "api.hypixel.net"
	private const val CC0 = "CC0-1.0"
	private val THIRD_PARTY = Regex("^thirdparty/([^/]+)/")

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
		val upstreams = licences(files, schemas, problems)
		return Report(java.util.List.copyOf(problems), files.associate { it.id to it.bytes.size.toLong() }, upstreams)
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

	/** The licence rules (T3.0j); returns the approved upstreams the tables use. */
	private fun licences(files: List<TableFile>, schemas: Path, problems: MutableList<Problem>): Set<String> {
		val upstreams = schemas.resolve("upstreams.json").takeIf { Files.isRegularFile(it) }
			?.let { DataFiles.MAPPER.readTree(Files.readString(it)).get("upstreams") }
			?.properties()?.associate { (id, upstream) -> id to upstream } ?: mapOf()
		val project = schemas.toAbsolutePath().parent
		val notices = project.resolve("THIRD_PARTY_NOTICES.md").takeIf { Files.isRegularFile(it) }?.let { Files.readString(it) } ?: ""
		val licenses = project.resolve("licenses")
		val used = sortedSetOf<String>()

		fun requireNotice(table: String, upstream: String, path: String?) {
			val name = upstreams[upstream]?.get("name")?.stringValue() ?: upstream
			if (!Regex("(?m)^## " + Regex.escape(name) + "\\s*$").containsMatchIn(notices)) {
				problems += Problem(table, "THIRD_PARTY_NOTICES", "no '## $name' section in THIRD_PARTY_NOTICES.md")
			}
			if (path != null && !notices.contains(path)) {
				problems += Problem(table, "THIRD_PARTY_NOTICES", "THIRD_PARTY_NOTICES.md does not name $path")
			}
			if (!Files.isRegularFile(licenses.resolve("$upstream.txt"))) {
				problems += Problem(table, "licenses", "licenses/$upstream.txt is missing (the build copies it into the jar's META-INF/licenses/)")
			}
		}

		for (file in files) {
			if (file.path.substringAfterLast('/').equals("george.json", ignoreCase = true)) {
				problems += Problem(file.id, "path", "${file.path}: NEU's george.json is CC BY-NC-SA and is never bundled")
			}
			val json = file.json ?: continue
			val license = json.get("license")?.takeIf { it.isString }?.stringValue()
			val upstream = THIRD_PARTY.find(file.path)?.groupValues?.get(1)
			if (upstream == null) {
				if (license != null && license != CC0) {
					problems += Problem(file.id, "license", "$license tables belong under thirdparty/<upstream>/")
				}
			} else {
				val approved = upstreams[upstream]
				if (approved == null) {
					problems += Problem(file.id, "thirdparty", "$upstream is not an approved upstream (data-schemas/upstreams.json, PLAN §2)")
				} else {
					val expected = approved.get("license")?.stringValue()
					if (license != expected) {
						problems += Problem(file.id, "license", "$license, but $upstream data is $expected")
					}
					used += upstream
					requireNotice(file.id, upstream, file.path)
				}
			}
			// Every licence anywhere in the table: the envelope's, each source's and any row's.
			licenceValues(json, "").forEach { (at, value) ->
				if (NC_SA.containsMatchIn(value)) {
					problems += Problem(file.id, at, "$value is NC or SA material, which is never shipped")
				}
			}
			val sources = json.get("sources")?.takeIf { it.isObject } ?: continue
			for ((name, source) in sources.properties()) {
				val at = "/sources/$name"
				val sourceLicense = source.get("license")?.takeIf { it.isString }?.stringValue()
				val cites = listOf("url", "title").mapNotNull { source.get(it)?.takeIf { node -> node.isString }?.stringValue() }
				if (cites.any { it.contains("george.json", ignoreCase = true) }) {
					problems += Problem(file.id, at, "cites NEU's george.json, which is CC BY-NC-SA and never used")
				}
				if (sourceLicense == "MIT" || sourceLicense == "Apache-2.0") {
					val notice = source.get("notice")?.takeIf { it.isString }?.stringValue()
					when {
						notice == null -> problems += Problem(file.id, "$at/notice", "an $sourceLicense source names its approved upstream as notice")
						notice !in upstreams -> problems += Problem(file.id, "$at/notice", "$notice is not an approved upstream (data-schemas/upstreams.json)")
						else -> {
							used += notice
							requireNotice(file.id, notice, null)
						}
					}
				}
				val url = source.get("url")?.takeIf { it.isString }?.stringValue()
				val host = url?.let {
					try {
						java.net.URI(it).host?.lowercase()
					} catch (e: java.net.URISyntaxException) {
						null
					}
				}
				if (url != null && host == null) {
					problems += Problem(file.id, "$at/url", "$url is not an absolute URL")
				}
				val kind = source.get("kind")?.takeIf { it.isString }?.stringValue()
				val citedOnly = host != null && host != OFFICIAL_API && CITED_ONLY.any { host == it || host.endsWith(".$it") } || kind in CITED_KINDS
				if (citedOnly && source.get("access")?.stringValue() != "user_supplied") {
					problems += Problem(file.id, "$at/access", "${host ?: kind} pages are used only as citations a person supplied (access user_supplied, D-16, D-28)")
				}
			}
		}
		return java.util.Collections.unmodifiableSet(used)
	}

	/** Every string under a `license` key in [node], with its pointer. */
	private fun licenceValues(node: JsonNode, at: String): List<Pair<String, String>> = when {
		node.isObject -> node.properties().flatMap { (name, child) ->
			if (name == "license" && child.isString) listOf("$at/$name" to child.stringValue()) else licenceValues(child, "$at/$name")
		}
		node.isArray -> node.values().flatMapIndexed { i, child -> licenceValues(child, "$at/$i") }
		else -> listOf()
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
