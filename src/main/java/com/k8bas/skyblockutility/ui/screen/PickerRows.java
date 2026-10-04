package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.RuleDatabase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The rows of the database picker (REQ-UI-11): a folder per island, sub-folders inside it (e.g. an
 * event), and the entries by name. Entries that already back a rule are hidden, and so is a folder left
 * empty. Without a query only the folders opened by hand show their entries; with one, every entry whose
 * name, match text or folder contains it shows, its folders open unless closed during that search (as
 * the settings screen does). Pure, so it is tested without a game.
 */
final class PickerRows {
	private PickerRows() {
	}

	/**
	 * @param folderKey the folder's key ("The End", or "Hub/Spooky Festival" for a sub-folder); for an
	 *                  entry, the key of the folder it is in
	 * @param entry     null for a folder
	 * @param count     a folder's entries shown (with its sub-folders')
	 */
	record Row(String folderKey, String label, int depth, RuleDatabase.Entry entry, int count, boolean open) {
	}

	static List<Row> rows(List<RuleDatabase.Entry> entries, Set<String> used, String query, Set<String> opened) {
		return rows(entries, used, query, opened, Set.of());
	}

	/** @param closedInSearch the folders closed by hand during the current search; ignored without a query */
	static List<Row> rows(List<RuleDatabase.Entry> entries, Set<String> used, String query, Set<String> opened, Set<String> closedInSearch) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		boolean searching = !q.isEmpty();
		Map<String, Map<String, List<RuleDatabase.Entry>>> folders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (RuleDatabase.Entry entry : entries) {
			if (used.contains(entry.id()) || searching && !matches(entry, q)) {
				continue;
			}
			// "" holds the entries directly in the island's folder; it sorts first.
			String sub = entry.subfolder() == null ? "" : entry.subfolder();
			folders.computeIfAbsent(entry.folder(), key -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)).computeIfAbsent(sub, key -> new ArrayList<>())
					.add(entry);
		}
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<String, Map<String, List<RuleDatabase.Entry>>> folder : folders.entrySet()) {
			String key = folder.getKey();
			int count = folder.getValue().values().stream().mapToInt(List::size).sum();
			boolean open = searching ? !closedInSearch.contains(key) : opened.contains(key);
			rows.add(new Row(key, key, 0, null, count, open));
			if (!open) {
				continue;
			}
			for (Map.Entry<String, List<RuleDatabase.Entry>> sub : folder.getValue().entrySet()) {
				List<RuleDatabase.Entry> inside = new ArrayList<>(sub.getValue());
				inside.sort(Comparator.comparing(RuleDatabase.Entry::name, String.CASE_INSENSITIVE_ORDER));
				if (sub.getKey().isEmpty()) {
					inside.forEach(entry -> rows.add(new Row(key, entry.name(), 1, entry, 0, false)));
					continue;
				}
				String subKey = key + "/" + sub.getKey();
				boolean subOpen = searching ? !closedInSearch.contains(subKey) : opened.contains(subKey);
				rows.add(new Row(subKey, sub.getKey(), 1, null, inside.size(), subOpen));
				if (subOpen) {
					inside.forEach(entry -> rows.add(new Row(subKey, entry.name(), 2, entry, 0, false)));
				}
			}
		}
		return rows;
	}

	private static boolean matches(RuleDatabase.Entry entry, String q) {
		return contains(entry.name(), q) || contains(entry.searchText(), q) || contains(entry.folder(), q) || contains(entry.subfolder(), q);
	}

	private static boolean contains(String text, String q) {
		return text != null && text.toLowerCase(Locale.ROOT).contains(q);
	}
}
