package com.k8bas.skyblockutility.skyblock

/**
 * Tells lines a player typed from the server's own lines (REQ-GS-05): a pasted copy of a level-up or an
 * action-bar echo in public, party, guild, co-op or private chat must never feed a server-event parser.
 * Hypixel's player chat has a channel prefix (`Guild > `, `Party > `, `Officer > `, `Co-op > `), a private
 * `From`/`To` sender, or a sender with a SkyBlock level (`[326]`) or a rank in brackets (`[MVP+]`, staff's
 * `[ዞ]`, any rank added later) before `name:`, with emblem or game-mode glyphs around it. A plain `Word: text`
 * line and the server's own speakers (`[NPC]`, `[BOSS]`, ...) are not player chat.
 *
 * Known limit: unranked chat shown without a level reads `Name: text`, the shape of a server line, so a
 * server-event parser matches its pattern from the start of the line, never anywhere in it.
 */
object ChatLines {
	private val CHANNEL = Regex("^(?:Guild|Party|Officer|Co-op) > ")
	private val PRIVATE = Regex("^(?:From|To) (?:\\[[^\\]]+] )?[A-Za-z0-9_]{1,16}: ")

	/** Glyphs such as emblems and the ironman icon: no letters, digits, brackets or colons. */
	private const val GLYPH = "[^\\sA-Za-z0-9_\\[\\]:]{1,2}"
	private val SENDER = Regex(
		"^(?:(\\[\\d{1,4}]) )?(?:$GLYPH )*(?:\\[([^\\]]{1,12})] )?(?:$GLYPH )*[A-Za-z0-9_]{1,16}(?: $GLYPH)*(?: \\[[^\\]]{1,16}])?: "
	)

	/** Bracketed speakers that are the server's, not ranks. */
	private val SERVER_SPEAKERS = setOf("NPC", "BOSS", "STATUE", "CROWD", "SECRET")

	/** True if [text] (formatting codes allowed) is shaped like a line a player wrote. */
	@JvmStatic
	fun isPlayerChat(text: String): Boolean {
		val line = GameText.strip(text)
		if (CHANNEL.containsMatchIn(line) || PRIVATE.containsMatchIn(line)) {
			return true
		}
		val sender = SENDER.find(line) ?: return false
		val level = sender.groups[1]
		val rank = sender.groups[2]?.value
		return level != null || rank != null && rank !in SERVER_SPEAKERS
	}
}
