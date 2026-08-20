package com.k8bas.skyblockutility.module.mobhighlighter;

public class MobDatabaseEntry {
	public String id;
	public String displayName;
	/** Core name text to match with CONTAINS — Skyblock nametags carry a live health suffix
	 *  and occasional extra symbols/spacing, so exact matching is unreliable. */
	public String matchText;
	/** Top-level grouping shown as a folder in the picker — the island the mob is found on. */
	public String island;
}
