package com.k8bas.skyblockutility.debug;

import com.k8bas.skyblockutility.highlight.NameMatcher;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Menu titles the armed container dump may write (T0.4b). Every title here is UNVERIFIED until the
 * G1 captures confirm it, so matching ignores colour codes, surrounding spaces and a page suffix
 * such as "(1/3)". The menu reader (T3.0e) reuses this list.
 */
public final class CaptureAllowlist {
	private static final Set<String> EXACT = Set.of(
			// SkyBlock leveling
			"SkyBlock Leveling", "Ways to Level Up", "Skill Related Tasks",
			// component menus (D-27)
			"Your Skills", "Skills", "Collections", "Collection", "Your Museum", "Museum",
			"Heart of the Mountain", "Heart of the Forest", "Pets", "Accessory Bag",
			// bestiary and dungeon rewards
			"Bestiary", "Croesus");
	/** "<parent> ➜ <page>", only for leveling parents, the task menus and the bestiary. */
	private static final Pattern SUBMENU = Pattern.compile(
			"(Tasks|Core|Event|Dungeon|Essence Shop|Slaying|Mining|Farming|Fishing|Foraging|Miscellaneous|Story|Complete Dungeons|Bestiary) ➜ .+");
	private static final Pattern RNG_METER = Pattern.compile(".+ RNG Meter");
	private static final Pattern REWARD_CHEST = Pattern.compile("(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock) Chest");
	private static final Pattern FLOOR = Pattern.compile("(Master Mode )?(The )?Catacombs - Floor [IVX]+");
	private static final Pattern PAGE = Pattern.compile("^\\(\\d+/\\d+\\)\\s*|\\s*\\(\\d+/\\d+\\)$");

	private CaptureAllowlist() {
	}

	public static boolean matches(String rawTitle) {
		String title = normalise(rawTitle);
		return EXACT.contains(title) || SUBMENU.matcher(title).matches() || RNG_METER.matcher(title).matches()
				|| REWARD_CHEST.matcher(title).matches() || FLOOR.matcher(title).matches();
	}

	static String normalise(String rawTitle) {
		return PAGE.matcher(NameMatcher.stripColorCodes(rawTitle).strip()).replaceAll("").strip();
	}
}
