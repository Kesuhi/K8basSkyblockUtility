package com.k8bas.skyblockutility.highlight;

import java.util.UUID;

/** Shared by Mob Highlighter and NPC Search's "unfixed" (search-by-name) NPC rules — both are
 *  "find a nearby entity whose nametag matches, outline it" in exactly the same way. */
public class HighlightRule {
	public String id = UUID.randomUUID().toString();
	public String label = "New Rule";
	public boolean enabled = true;
	/** e.g. "minecraft:zombie"; null/blank matches any entity type. */
	public String entityTypeId = null;
	public NameMatchMode nameMatchMode = NameMatchMode.CONTAINS;
	/** Blank when nameMatchMode == NONE. */
	public String namePattern = "";
	/** Packed 0xRRGGBB. */
	public int color = 0xFF0000;
	/** Blocks; 0 or negative means unlimited. */
	public double maxDistance = 0;
	/** Display island name (matching IslandTracker's output) this rule is restricted to; null
	 *  means active on every island. Database-sourced rules whose source mob/NPC can only exist
	 *  on one physical island get this set automatically; hand-made rules and anything sourced
	 *  from a cross-island event category (Jerry, Fishing, festivals) leave it null. */
	public String island = null;
}
