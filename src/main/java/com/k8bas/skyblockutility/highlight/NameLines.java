package com.k8bas.skyblockutility.highlight;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Picks an entity's name from the name lines floating near it (T3.1, REQ-GLOW-09). Hypixel puts action labels
 * such as "CLICK" under an NPC's name. In the G1 capture (Mort, Adventurer Saul) the name stand sits at the body's
 * feet and CLICK 0.25 below, so nearest-wins already gave the name; SPEC's case, where the label is nearer than the
 * name (Tomioka: name 2.0 above the feet, CLICK 1.7), is UNVERIFIED for Trinity, Tomioka and Duncan, and this
 * guards it. Labels, empty lines and lines whose name is not shown are skipped; the nearest remaining line wins.
 * "CLICK" and its LEFT/RIGHT/SHIFT forms are the labels seen so far; others are UNVERIFIED until captured. Pure, so
 * it is tested without a game.
 */
public final class NameLines {
	/** An action label after colour codes are stripped: "CLICK", "RIGHT CLICK", "CLICK TO TALK", never "Clicker Bob". */
	private static final Pattern ACTION_LABEL = Pattern.compile("^(?:(?:LEFT|RIGHT|SHIFT)[ -])?CLICK(?:\\s.*)?$");

	/**
	 * One name line: its text as shown, its squared distance to the entity, and whether its name is shown at all.
	 */
	public record Line(String text, double distanceSq, boolean shown) {
	}

	private NameLines() {
	}

	/** The nearest line that is a name, colour codes stripped, or null if there is none (the caller falls back). */
	public static String pick(List<Line> lines) {
		String best = null;
		double bestDistanceSq = Double.MAX_VALUE;
		for (Line line : lines) {
			if (!line.shown() || line.text() == null) {
				continue;
			}
			String text = NameMatcher.stripColorCodes(line.text());
			String trimmed = text.strip();
			if (trimmed.isEmpty() || ACTION_LABEL.matcher(trimmed).matches()) {
				continue;
			}
			if (line.distanceSq() < bestDistanceSq) {
				bestDistanceSq = line.distanceSq();
				best = text;
			}
		}
		return best;
	}
}
