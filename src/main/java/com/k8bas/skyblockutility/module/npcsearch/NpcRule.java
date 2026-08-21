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

	// Fixed only:
	public double x;
	public double y;
	public double z;

	// Unfixed only:
	public NameMatchMode nameMatchMode = NameMatchMode.CONTAINS;
	public String namePattern = "";

	/** Packed 0xRRGGBB. Used for both the waypoint beam/text and the entity outline. */
	public int color = 0x00FF00;
}
