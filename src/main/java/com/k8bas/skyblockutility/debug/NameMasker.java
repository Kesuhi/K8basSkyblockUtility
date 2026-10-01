package com.k8bas.skyblockutility.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Replaces the names of real players, yourself included, with placeholders in capture output
 * ("Self", "Player1", "Player2", …), so a dump in latest.log can become a fixture without personal
 * data (REQ-GS-13, REQ-XC-PRIVACY-01; decided 2026-10-01). Names are learnt from what the client
 * knows when a dump is written: you, the real players in the tab list and in the world. Real players
 * have version-4 UUIDs; Hypixel's NPCs and the fake profiles behind its tab widgets do not, so their
 * names stay. A name keeps its placeholder for the whole session. Only whole, case-exact names are
 * replaced. Client thread only.
 */
final class NameMasker {
	/** The capture output of this session. */
	static final NameMasker SESSION = new NameMasker();

	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final String SELF = "Self";

	private final Map<String, String> placeholders = new LinkedHashMap<>();
	private int players;
	private Pattern names;

	void learnSelf(String name) {
		if (name != null && USERNAME.matcher(name).matches() && !SELF.equals(placeholders.get(name))) {
			placeholders.put(name, SELF);
			names = null;
		}
	}

	void learnPlayer(String name, UUID uuid) {
		if (name != null && uuid != null && uuid.version() == 4 && USERNAME.matcher(name).matches()
				&& !placeholders.containsKey(name)) {
			placeholders.put(name, "Player" + ++players);
			names = null;
		}
	}

	boolean knows(String name) {
		return placeholders.containsKey(name);
	}

	String mask(String line) {
		if (placeholders.isEmpty() || line == null) {
			return line;
		}
		if (names == null) {
			names = Pattern.compile("(?<![A-Za-z0-9_])(" + placeholders.keySet().stream().map(Pattern::quote)
					.collect(Collectors.joining("|")) + ")(?![A-Za-z0-9_])");
		}
		Matcher matcher = names.matcher(line);
		return matcher.replaceAll(match -> Matcher.quoteReplacement(placeholders.get(match.group(1))));
	}

	/** Learns every real player name the client knows right now. */
	void learnFrom(Minecraft client) {
		if (client.player != null) {
			learnSelf(client.player.getGameProfile().name());
		}
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			for (PlayerInfo info : connection.getListedOnlinePlayers()) {
				learnPlayer(info.getProfile().name(), info.getProfile().id());
			}
		}
		if (client.level != null) {
			for (Player player : client.level.players()) {
				learnPlayer(player.getGameProfile().name(), player.getUUID());
			}
		}
	}
}
