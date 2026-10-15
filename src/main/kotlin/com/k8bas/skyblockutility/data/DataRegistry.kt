package com.k8bas.skyblockutility.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.function.Consumer

/** Reads a file of the data folder by its relative path; null if there is none. Read-only. */
fun interface DataSource {
	fun read(path: String): ByteArray?
}

/**
 * The one registry of bundled tables (T3.0c, D-26): every table a feature needs loads through it, from the
 * mod's own files, with no network access (REQ-DATA-01, REQ-DATA-13). It reads `index.json`, then each
 * registered table: checks the path, the SHA-256 and the envelope (table id, schemaVersion), accepts UTF-8
 * with or without a BOM and LF or CRLF (REQ-DATA-08), and hands the decoded, immutable value to every
 * consumer of that table (EC-DATA-05). Loading runs on [loader]; consumers see "loading" until then
 * (REQ-DATA-09). A failure makes only that table unavailable, is logged once and never escapes
 * (REQ-DATA-07). Nothing is written anywhere (EC-DATA-07).
 *
 * @param loader runs the reading, off the client thread in production
 * @param callbacks runs the onLoaded listeners, on the client thread in production
 * @param warn gets one line per unavailable table and per skipped row
 */
class DataRegistry(
	private val source: DataSource,
	private val loader: Executor,
	private val callbacks: Executor,
	private val warn: Consumer<String>,
) {
	private val handles = ConcurrentHashMap<String, TableHandle<*>>()

	@Volatile
	private var index: Map<String, IndexEntry>? = null

	@Volatile
	private var indexProblem: String? = null

	@Volatile
	private var started = false

	/** The handle of [spec]'s table; the same handle for every consumer of that table. */
	fun <T : Any> register(spec: TableSpec<T>): TableHandle<T> {
		val handle = handles.computeIfAbsent(spec.id) { TableHandle(spec, callbacks) }
		check(handle.spec === spec) { "Table ${spec.id} is already registered with another spec" }
		@Suppress("UNCHECKED_CAST")
		val typed = handle as TableHandle<T>
		if (started) {
			loader.execute { load(typed) }
		}
		return typed
	}

	/** Reads the index and every table registered so far, on the loader; later registrations load at once. */
	fun start() {
		started = true
		loader.execute {
			readIndex()
			handles.values.forEach { load(it) }
		}
	}

	/** One line per indexed table: id, dataVersion, gameVersion and its state (REQ-DATA-11). */
	fun describe(): List<String> {
		indexProblem?.let { return listOf("index unavailable: $it") }
		val entries = index ?: return listOf("loading")
		return entries.values.map { entry ->
			val state = when (val now = handles[entry.table]?.state()) {
				null -> "not used"
				TableState.Loading -> "loading"
				is TableState.Loaded<*> -> "loaded"
				is TableState.Unavailable -> "unavailable (${now.reason})"
			}
			"${entry.table}: data v${entry.dataVersion}, SkyBlock ${entry.gameVersion}, $state"
		}
	}

	/** The index entries, once read; empty before. */
	fun entries(): List<IndexEntry> = index?.values?.toList() ?: listOf()

	private fun readIndex() {
		try {
			val bytes = source.read(INDEX) ?: return failIndex("no $INDEX")
			val json = JsonParser.parseString(DataText.text(bytes)).asJsonObject
			if (json.get("indexVersion")?.asInt != INDEX_VERSION) {
				return failIndex("unknown indexVersion")
			}
			val tables = LinkedHashMap<String, IndexEntry>()
			for ((id, element) in json.getAsJsonObject("tables").entrySet()) {
				val entry = element.asJsonObject
				tables[id] = IndexEntry(
					id, entry.get("path").asString, entry.get("sha256").asString, entry.get("rows").asInt,
					entry.get("schemaVersion").asInt, entry.get("dataVersion").asInt, entry.get("gameVersion").asString,
				)
			}
			// In the index's order, for the listing.
			index = java.util.Collections.unmodifiableMap(tables)
		} catch (e: OutOfMemoryError) {
			throw e
		} catch (e: Throwable) {
			failIndex("unreadable $INDEX (${e.javaClass.simpleName})")
		}
	}

	private fun failIndex(problem: String) {
		indexProblem = problem
		warn.accept("Data index unavailable: $problem; every bundled table is unavailable")
	}

	private fun <T : Any> load(handle: TableHandle<T>) {
		if (!handle.scheduled.compareAndSet(false, true)) {
			return
		}
		val id = handle.spec.id
		val result: TableState<T> = try {
			decode(handle.spec)
		} catch (e: OutOfMemoryError) {
			throw e
		} catch (e: Throwable) {
			// Not only RuntimeException: a decoder's StackOverflowError must not leave the table "loading".
			TableState.Unavailable("could not be read: ${e.javaClass.simpleName}")
		}
		if (result is TableState.Unavailable) {
			warn.accept("Data table $id unavailable: ${result.reason}")
		}
		handle.finish(result)
	}

	private fun <T : Any> decode(spec: TableSpec<T>): TableState<T> {
		indexProblem?.let { return TableState.Unavailable("index unavailable: $it") }
		val entry = index?.get(spec.id) ?: return TableState.Unavailable("not in the index")
		if (!DataText.isSafePath(entry.path)) {
			return TableState.Unavailable("bad path in the index")
		}
		val bytes = source.read(entry.path) ?: return TableState.Unavailable("file missing")
		if (!DataText.checksum(bytes).equals(entry.sha256, ignoreCase = true)) {
			return TableState.Unavailable("checksum differs from the index")
		}
		val body: JsonObject = try {
			JsonParser.parseString(DataText.text(bytes)).asJsonObject
		} catch (e: RuntimeException) {
			return TableState.Unavailable("unreadable JSON")
		}
		if (body.get("table")?.asString != spec.id) {
			return TableState.Unavailable("table id in the file is not ${spec.id}")
		}
		val schemaVersion = body.get("schemaVersion")?.asInt
		if (schemaVersion != spec.schemaVersion || schemaVersion != entry.schemaVersion) {
			return TableState.Unavailable("unknown schemaVersion $schemaVersion (this version reads ${spec.schemaVersion})")
		}
		val value = spec.decoder.decode(TableContext(entry, body, warn))
		return TableState.Loaded(value, entry)
	}

	private companion object {
		const val INDEX = "index.json"
		const val INDEX_VERSION = 1

	}
}

/**
 * How a table file is read as text, and its index checksum (REQ-DATA-04, REQ-DATA-08): UTF-8 with a BOM
 * dropped; the checksum is the SHA-256 of that text with CRLF as LF, so a Windows checkout (core.autocrlf) and
 * CI agree. The registry and the index tool (`./gradlew dataIndex`) both use these, so they cannot drift.
 */
internal object DataText {
	private val BOM = Char(0xFEFF).toString()
	private val CRLF = "" + Char(13) + Char(10)
	private val LF = Char(10).toString()

	/** Relative, inside the data folder: no `..`, no leading slash or drive, no spaces. */
	private val SAFE_PATH = Regex("^(?!.*\\.\\.)[A-Za-z0-9_][A-Za-z0-9_./-]*\\.json$")

	/** True for a table path the registry reads; the validator rejects any other. */
	@JvmStatic
	fun isSafePath(path: String): Boolean = SAFE_PATH.matches(path)

	@JvmStatic
	fun text(bytes: ByteArray): String = String(bytes, StandardCharsets.UTF_8).removePrefix(BOM)

	@JvmStatic
	fun checksum(bytes: ByteArray): String {
		val normal = text(bytes).replace(CRLF, LF).toByteArray(StandardCharsets.UTF_8)
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normal))
	}
}
