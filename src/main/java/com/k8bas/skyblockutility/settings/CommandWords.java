package com.k8bas.skyblockutility.settings;

import java.util.List;
import java.util.Locale;

/**
 * What `/ksu <text>` does with its text (REQ-UI-10): a first word on {@link #RESERVED} is a subcommand
 * and runs its own action; any other text opens the settings screen with it in the search box. Pure, so
 * it is tested without a game.
 */
public final class CommandWords {
	/** The subcommand words, in one place; a later module that adds a subcommand adds its word here. */
	public static final List<String> RESERVED = List.of("hud", "debug", "update", "sbxp");

	private CommandWords() {
	}

	/** The reserved word {@code text} starts with, or null when the text is a search term. */
	public static String reserved(String text) {
		String first = text.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
		return RESERVED.contains(first) ? first : null;
	}
}
