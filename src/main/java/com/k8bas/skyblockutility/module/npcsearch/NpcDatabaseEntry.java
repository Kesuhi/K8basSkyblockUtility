package com.k8bas.skyblockutility.module.npcsearch;

public class NpcDatabaseEntry {
	public String id;
	public String displayName;
	/** Top-level grouping shown as a folder in the picker — the island the NPC is found on. */
	public String island;
	/** True for an NPC that stands at a fixed spot (gets a permanent waypoint at x/y/z); false
	 *  for one that wanders (gets a Mob Highlighter-style nametag search instead). */
	public boolean fixed;
	public double x;
	public double y;
	public double z;
	/** Only meaningful when fixed == false — core name text to match with CONTAINS. */
	public String matchText;
}
