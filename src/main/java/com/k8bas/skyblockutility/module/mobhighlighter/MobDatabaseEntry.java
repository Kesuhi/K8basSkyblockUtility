package com.k8bas.skyblockutility.module.mobhighlighter;

public class MobDatabaseEntry {
	public String id;
	public String displayName;
	/** Core name text to match with CONTAINS — Skyblock nametags carry a live health suffix
	 *  and occasional extra symbols/spacing, so exact matching is unreliable. */
	public String matchText;
	/** Top-level grouping shown as a folder in the picker — the island the mob is found on. */
	public String island;
	/** Optional second-level folder within the island (currently only used for "Events", e.g.
	 *  "Spooky Festival"). Null for entries that sit directly in their island's folder. */
	public String subfolder;
}
