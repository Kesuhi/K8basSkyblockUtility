package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.highlight.NameMatchMode;

import java.util.UUID;

/** A tracked NPC. Fixed ones get a permanent waypoint at their known coordinates; unfixed ones
 *  get a Mob Highlighter-style nearby-nametag search instead (see NpcSearchModule, which converts
 *  each unfixed NpcRule into a plain HighlightRule for its own HighlightManager instance). Both
 *  kinds only ever activate while the player is actually on the matching island. */
public class NpcRule {
	public String id = UUID.randomUUID().toString();
	public String label = "New NPC";
	public boolean enabled = true;
	public String island;
	public boolean fixed;
	/** The database entry's own id this rule was created from (NpcDatabaseEntry.id), or null for
	 *  a hand-made rule. Lets the picker hide an entry that's already been added instead of
	 *  letting it be added again as a duplicate. */
	public String sourceId;

	// Fixed only:
	public double x;
	public double y;
	public double z;

	// Unfixed only:
	public NameMatchMode nameMatchMode = NameMatchMode.CONTAINS;
	public String namePattern = "";

	/** Packed 0xRRGGBB. Unused since 1.2.0: NPCs are drawn in white (R31). Kept in the file, so a
	 *  downgrade keeps the colour it had. Never the beam's colour (REQ-NPCWP-07). */
	public int color = 0x0AA351;
	/** A fixed NPC's own beam colour, 0xRRGGBB, or null to follow its island's (REQ-NPCWP-05,
	 *  WaypointColors). Gson leaves it null in configs written before T3.4. */
	public Integer beamColor;

	/** Repairs what Gson leaves invalid (REQ-CFG-07): an unknown or missing match mode becomes
	 *  CONTAINS, and a missing id is generated once. @return true if anything changed. */
	public boolean normalize() {
		boolean changed = false;
		if (nameMatchMode == null) {
			nameMatchMode = NameMatchMode.CONTAINS;
			changed = true;
		}
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
			changed = true;
		}
		// A null label would throw wherever it is drawn (waypoint label, title, settings).
		if (label == null) {
			label = "New NPC";
			changed = true;
		}
		if (namePattern == null) {
			namePattern = "";
			changed = true;
		}
		return changed;
	}
}
