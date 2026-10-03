package com.k8bas.skyblockutility.highlight;

import java.util.List;

/**
 * The four glow behaviour changes of 1.1.0, in the CHANGELOG's wording, for the tooltip of each
 * highlight card (REQ-GLOW-15, AC-GLOW-14).
 */
public final class GlowBehaviourText {
	/** Each occurs verbatim in CHANGELOG.md (1.1.0). */
	public static final List<String> CHANGES = List.of(
			"the outline is visible-only",
			"invisible mobs are never outlined",
			"NPCs are outlined only while in view; blocks hide the outline",
			"the \"You found <NPC>\" title appears after line of sight, once per run, and only for Trinity, Tomioka, Duncan, Xalx and Pete");

	private GlowBehaviourText() {
	}

	/** The changes as tooltip lines. */
	public static String tooltip() {
		StringBuilder text = new StringBuilder("Since 1.1.0:");
		for (String change : CHANGES) {
			text.append("\n- ").append(Character.toUpperCase(change.charAt(0))).append(change.substring(1)).append('.');
		}
		return text.toString();
	}
}
