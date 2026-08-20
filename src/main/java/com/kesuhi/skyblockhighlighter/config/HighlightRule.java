package com.kesuhi.skyblockhighlighter.config;

import java.util.UUID;

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
}
