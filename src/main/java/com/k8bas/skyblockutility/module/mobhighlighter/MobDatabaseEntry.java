package com.k8bas.skyblockutility.module.mobhighlighter;

import java.util.List;

public class MobDatabaseEntry {
	public String id;
	public String displayName;
	/** Core name text to match with CONTAINS — Skyblock nametags carry a live health suffix
	 *  and occasional extra symbols/spacing, so exact matching is unreliable. */
	public String matchText;
	/** Top-level grouping shown as a folder in the picker — the island the mob is found on. */
	public String island;
	/** Optional second-level folder within the island or group (e.g. "Lava Fishing" under "Fishing").
	 *  Null for entries that sit directly in their island's folder. */
	public String subfolder;
	/** Former ids of entries merged into this one (REQ-NPCDB-07(d)): rules linked to them still find it. */
	public List<String> aliases = List.of();
	/** Invisible by design (REQ-NPCDB-08): never outlined, so the picker offers it greyed and not addable (T3.8c). */
	public boolean invisible;
}
