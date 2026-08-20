package com.k8bas.skyblockutility.module.mobhighlighter;

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
	/** Blocks; 0 or negative means unlimited. */
	public double maxDistance = 0;
}
