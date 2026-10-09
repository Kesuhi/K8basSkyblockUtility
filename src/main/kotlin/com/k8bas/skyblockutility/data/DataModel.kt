package com.k8bas.skyblockutility.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function

/**
 * One table's entry in `data/index.json` (REQ-DATA-04): its file (relative to the data folder), checksum,
 * row count and the versions of the file it describes. The checksum is the SHA-256 of the file's UTF-8 text
 * with a BOM removed and CRLF as LF, so a CRLF checkout matches the index too (REQ-DATA-08). The index is the registry's list of tables:
 * `{"indexVersion": 1, "tables": {"<table id>": {"path", "sha256", "rows", "schemaVersion", "dataVersion",
 * "gameVersion"}}}`, written by `./gradlew dataIndex` (T3.0i).
 */
@JvmRecord
data class IndexEntry(
	val table: String,
	val path: String,
	val sha256: String,
	val rows: Int,
	val schemaVersion: Int,
	val dataVersion: Int,
	val gameVersion: String,
)

/** Where a table stands for its consumers (REQ-DATA-07, REQ-DATA-09). */
sealed class TableState<out T> {
	/** Not read yet; the data follows, never a permanently empty table. */
	object Loading : TableState<Nothing>()

	/** Read and decoded; [value] is the one immutable instance every consumer shares (EC-DATA-05). */
	data class Loaded<T>(val value: T, val entry: IndexEntry) : TableState<T>()

	/** Missing, unreadable, wrong checksum or unknown shape: the features that need it show "data unavailable". */
	data class Unavailable(val reason: String) : TableState<Nothing>()
}

/** Turns a table's JSON into what its consumers use; it must return an immutable value. */
fun interface TableDecoder<T> {
	fun decode(table: TableContext): T
}

/**
 * A table a feature needs: its id, the one schemaVersion this code understands and its decoder. A table
 * with another schemaVersion is unavailable rather than misread (REQ-DATA-07).
 */
class TableSpec<T : Any>(val id: String, val schemaVersion: Int, val decoder: TableDecoder<T>)

/**
 * What a decoder sees: the index entry and the whole table object (envelope included). [rows] reads an
 * array of row objects and skips each malformed one with one WARN naming the table and the row
 * (REQ-DATA-07), so one bad row never loses the table.
 */
class TableContext internal constructor(val entry: IndexEntry, val body: JsonObject, private val warn: Consumer<String>) {
	/**
	 * The decoded rows of [array], in order. A row that is not an object, or whose [decode] throws or
	 * returns null, is skipped and logged once; [id] names the row in that log (its position if it fails).
	 */
	fun <R : Any> rows(array: JsonArray?, id: Function<JsonObject, String?>, decode: Function<JsonObject, R?>): List<R> =
		rows("", array, id, decode)

	/** The same for one of several row arrays of a table; [label] (e.g. `tasks`) names the array in the log. */
	fun <R : Any> rows(label: String, array: JsonArray?, id: Function<JsonObject, String?>, decode: Function<JsonObject, R?>): List<R> {
		if (array == null) {
			throw IllegalArgumentException("no row array" + if (label.isEmpty()) "" else " $label")
		}
		// Each call logs its own rows, so two arrays' "#0" are both reported.
		val warned = HashSet<String>()
		val prefix = if (label.isEmpty()) "row" else "$label row"
		fun skip(row: String, reason: String) {
			if (warned.add(row)) {
				warn.accept("Data table ${entry.table}: $prefix $row skipped ($reason)")
			}
		}
		val rows = ArrayList<R>(array.size())
		for ((index, element) in array.withIndex()) {
			if (!element.isJsonObject) {
				skip("#$index", "not an object")
				continue
			}
			val row = element.asJsonObject
			val name = try {
				id.apply(row)
			} catch (e: RuntimeException) {
				null
			} ?: "#$index"
			val value = try {
				decode.apply(row)
			} catch (e: RuntimeException) {
				skip(name, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
				continue
			}
			if (value == null) {
				skip(name, "no value")
				continue
			}
			rows.add(value)
		}
		return java.util.List.copyOf(rows)
	}
}

/** A consumer's view of one table: its state now, and a call once it has loaded. */
class TableHandle<T : Any> internal constructor(val spec: TableSpec<T>, private val callbacks: Executor) {
	@Volatile
	private var state: TableState<T> = TableState.Loading
	private val listeners = CopyOnWriteArrayList<Consumer<T>>()
	internal val scheduled = AtomicBoolean()

	fun state(): TableState<T> = state

	/** The decoded table, or null while loading or when unavailable. */
	fun value(): T? = (state as? TableState.Loaded<T>)?.value

	/**
	 * [listener] gets the table once it has loaded (at once if it already has), on the callback executor,
	 * exactly once: whichever side takes it out of the waiting list delivers it, this call or the loader.
	 */
	fun onLoaded(listener: Consumer<T>) {
		listeners.add(listener)
		val now = state
		if (now is TableState.Loaded<T> && listeners.remove(listener)) {
			callbacks.execute { listener.accept(now.value) }
		}
	}

	internal fun finish(result: TableState<T>) {
		state = result
		if (result is TableState.Loaded<T>) {
			for (listener in listeners) {
				if (listeners.remove(listener)) {
					callbacks.execute { listener.accept(result.value) }
				}
			}
		}
	}
}
