package com.k8bas.skyblockutility.debug

import net.minecraft.client.Minecraft
import java.util.UUID
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Replaces the names of real players, yourself included, with placeholders in capture output
 * ("Self", "Player1", "Player2", …), so a dump in latest.log can become a fixture without personal
 * data (REQ-GS-13, REQ-XC-PRIVACY-01; decided 2026-10-01). Names are learnt from what the client
 * knows when a dump is written: you, the real players in the tab list and in the world. Real players
 * have version-4 UUIDs; Hypixel's NPCs and the fake profiles behind its tab widgets do not, so their
 * names stay. A name keeps its placeholder for the whole session. Only whole, case-exact names are
 * replaced. Client thread only. Internal (was package-private); its members stay public for the Java test.
 */
internal class NameMasker {
	private val placeholders = LinkedHashMap<String, String>()
	private var players = 0
	private var names: Pattern? = null

	fun learnSelf(name: String?) {
		if (name != null && USERNAME.matcher(name).matches() && SELF != placeholders[name]) {
			placeholders[name] = SELF
			names = null
		}
	}

	fun learnPlayer(name: String?, uuid: UUID?) {
		if (name != null && uuid != null && uuid.version() == 4 && USERNAME.matcher(name).matches() && !placeholders.containsKey(name)) {
			placeholders[name] = "Player" + ++players
			names = null
		}
	}

	/** Null is never a learnt name. */
	fun knows(name: String?): Boolean = name != null && placeholders.containsKey(name)

	fun mask(line: String?): String? {
		if (placeholders.isEmpty() || line == null) {
			return line
		}
		val pattern = names ?: Pattern.compile(
			"(?<![A-Za-z0-9_])(" + placeholders.keys.joinToString("|") { Pattern.quote(it) } + ")(?![A-Za-z0-9_])",
		).also { names = it }
		return pattern.matcher(line).replaceAll { match -> Matcher.quoteReplacement(placeholders[match.group(1)]!!) }
	}

	/** Learns every real player name the client knows right now. */
	fun learnFrom(client: Minecraft) {
		client.player?.let { learnSelf(it.gameProfile.name()) }
		client.connection?.let { connection ->
			for (info in connection.listedOnlinePlayers) {
				learnPlayer(info.profile.name(), info.profile.id())
			}
		}
		client.level?.let { level ->
			for (player in level.players()) {
				learnPlayer(player.gameProfile.name(), player.uuid)
			}
		}
	}

	companion object {
		/** The capture output of this session. */
		@JvmField
		val SESSION: NameMasker = NameMasker()

		private val USERNAME: Pattern = Pattern.compile("[A-Za-z0-9_]{3,16}")
		private const val SELF: String = "Self"
	}
}
