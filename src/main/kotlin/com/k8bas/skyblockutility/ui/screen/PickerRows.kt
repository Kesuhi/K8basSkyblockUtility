package com.k8bas.skyblockutility.ui.screen

import com.k8bas.skyblockutility.ui.option.RuleDatabase
import java.util.Locale
import java.util.TreeMap

/**
 * The rows of the database picker (REQ-UI-11): a folder per island, sub-folders inside it (e.g. an
 * event), and the entries by name. Entries that already back a rule are hidden, and so is a folder left
 * empty. Without a query only the folders opened by hand show their entries; with one, every entry whose
 * name, match text or folder contains it shows, its folders open unless closed during that search (as
 * the settings screen does). Pure, so it is tested without a game.
 */
internal object PickerRows {
	/**
	 * @param folderKey the folder's key ("The End", or "Hub/Spooky Festival" for a sub-folder); for an
	 *                  entry, the key of the folder it is in
	 * @param label     an entry's name as the database gave it (nullable, as the Java record's was)
	 * @param entry     null for a folder
	 * @param count     a folder's entries shown (with its sub-folders')
	 */
	@JvmRecord
	data class Row(val folderKey: String?, val label: String?, val depth: Int, val entry: RuleDatabase.Entry?, val count: Int, val open: Boolean) {
		override fun toString(): String = "Row[folderKey=$folderKey, label=$label, depth=$depth, entry=$entry, count=$count, open=$open]"
	}

	@JvmStatic
	fun rows(entries: List<RuleDatabase.Entry>, used: Set<String>, query: String, opened: Set<String>): List<Row> =
		rows(entries, used, query, opened, java.util.Set.of())

	/** @param closedInSearch the folders closed by hand during the current search; ignored without a query */
	@JvmStatic
	fun rows(entries: List<RuleDatabase.Entry>, used: Set<String>, query: String, opened: Set<String>, closedInSearch: Set<String>): List<Row> {
		val q = query.trim(Character::isWhitespace).lowercase(Locale.ROOT)
		val searching = q.isNotEmpty()
		val folders = TreeMap<String, TreeMap<String, MutableList<RuleDatabase.Entry>>>(String.CASE_INSENSITIVE_ORDER)
		for (entry in entries) {
			if (used.contains(entry.id()) || searching && !matches(entry, q)) {
				continue
			}
			// "" holds the entries directly in the island's folder; it sorts first.
			folders.getOrPut(entry.folder()) { TreeMap(String.CASE_INSENSITIVE_ORDER) }.getOrPut(entry.subfolder() ?: "") { ArrayList() }.add(entry)
		}
		val rows = ArrayList<Row>()
		for ((key, subfolders) in folders) {
			val open = if (searching) !closedInSearch.contains(key) else opened.contains(key)
			rows.add(Row(key, key, 0, null, subfolders.values.sumOf { it.size }, open))
			if (!open) {
				continue
			}
			for ((sub, inFolder) in subfolders) {
				val inside = inFolder.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name() })
				if (sub.isEmpty()) {
					inside.forEach { rows.add(Row(key, it.name(), 1, it, 0, false)) }
					continue
				}
				val subKey = "$key/$sub"
				val subOpen = if (searching) !closedInSearch.contains(subKey) else opened.contains(subKey)
				rows.add(Row(subKey, sub, 1, null, inside.size, subOpen))
				if (subOpen) {
					inside.forEach { rows.add(Row(subKey, it.name(), 2, it, 0, false)) }
				}
			}
		}
		return rows
	}

	private fun matches(entry: RuleDatabase.Entry, q: String): Boolean =
		contains(entry.name(), q) || contains(entry.searchText(), q) || contains(entry.folder(), q) || contains(entry.subfolder(), q)

	private fun contains(text: String?, q: String): Boolean = text != null && text.lowercase(Locale.ROOT).contains(q)
}
