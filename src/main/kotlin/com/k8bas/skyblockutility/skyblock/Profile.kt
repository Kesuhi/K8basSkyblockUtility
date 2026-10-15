package com.k8bas.skyblockutility.skyblock

import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Supplier

/** A SkyBlock profile's game mode, from the glyph after its name in the tab list. */
enum class ProfileType { NORMAL, IRONMAN, STRANDED, BINGO }

/**
 * The current SkyBlock profile (REQ-GS-08): the Minecraft account, the key (the name case-folded and trimmed,
 * with the type glyph removed), the name as shown, the type, and the profile id once seen.
 */
data class Profile(val account: UUID, val key: String, val name: String, val type: ProfileType, val id: String?)

/**
 * The profile lines Hypixel shows: the tab widget header `Profile: <name> [glyph]` and the chat lines
 * `You are playing on profile: <name> [(Co-op)]` and `Profile ID: <id>`. Glyphs: ♲ ironman (SPEC AC-GS-08);
 * ☀ stranded and Ⓑ bingo are UNVERIFIED until captured.
 */
object ProfileText {
	private val GLYPHS = mapOf(Char(0x2672) to ProfileType.IRONMAN, Char(0x2600) to ProfileType.STRANDED, Char(0x24B7) to ProfileType.BINGO)
	private val CO_OP = Regex("\\s*\\(Co-op\\)\\s*$")
	private val PLAYING = Regex("^You are playing on profile: (.+)$")
	private val ID = Regex("^Profile ID: ([0-9a-fA-F-]{8,})$")

	/** The name as shown, without the type glyph or a co-op note. */
	@JvmStatic
	fun name(text: String): String = CO_OP.replace(text.trim().filterNot { it in GLYPHS }.trim(), "").trim()

	/** The type a glyph in [text] names, or null if there is none. */
	@JvmStatic
	fun type(text: String): ProfileType? = text.firstNotNullOfOrNull { GLYPHS[it] }

	/** The profile key: the name case-folded and trimmed, without glyph or co-op note. */
	@JvmStatic
	fun key(text: String): String = name(text).lowercase(Locale.ROOT)

	/** The profile name in a "You are playing on profile:" line, or null. */
	@JvmStatic
	fun playing(line: String): String? = PLAYING.matchEntire(line.trim())?.groupValues?.get(1)

	/** The id in a "Profile ID:" line, or null. */
	@JvmStatic
	fun id(line: String): String? = ID.matchEntire(line.trim())?.groupValues?.get(1)
}

/**
 * Which profile the player is on (T3.0d, REQ-GS-08): from the tab `Profile:` widget and the profile chat
 * lines; unknown (null) until identified. A change event fires when the profile or the account changes. In
 * the Rift the tab name reads backwards and is ignored (EC-GS-10), as is any tab name that is the current
 * name backwards; a tab line without a glyph (dungeons,
 * Kuudra) keeps the type last seen for that profile (EC-GS-11). Not thread-safe: driven on the client thread.
 */
class ProfileTracker(private val account: Supplier<UUID?>) {
	private var current: Profile? = null
	private var pendingId: String? = null
	private val listeners = CopyOnWriteArrayList<Consumer<Profile?>>()

	fun current(): Profile? = current

	/** Called with the new profile, or null when it became unknown. */
	fun onChange(listener: Consumer<Profile?>) {
		listeners.add(listener)
	}

	/** The tab widgets; [inRift] ignores the reversed Rift name. */
	fun tab(tab: TabSnapshot, inRift: Boolean) {
		val inline = tab.widget(WIDGET)?.inline ?: return
		if (inRift || inline.isBlank()) {
			return
		}
		// Also the Rift's name read before the island is known: the current name backwards is no new profile.
		val key = ProfileText.key(inline)
		val now = current
		if (now != null && key != now.key && key == now.key.reversed()) {
			return
		}
		identify(inline, ProfileText.type(inline))
	}

	/** A server chat line (player chat is filtered out before this). */
	fun chat(line: String) {
		ProfileText.id(line)?.let { id ->
			val now = current
			if (now == null) {
				pendingId = id
			} else if (now.id != id) {
				set(now.copy(id = id))
			}
			return
		}
		ProfileText.playing(line)?.let { identify(it, ProfileText.type(it)) }
	}

	/** Disconnected: the profile is unknown until identified again. */
	fun reset() {
		pendingId = null
		set(null)
	}

	private fun identify(text: String, glyphType: ProfileType?) {
		val account = account.get() ?: return
		val key = ProfileText.key(text)
		if (key.isEmpty()) {
			return
		}
		val now = current
		val same = now != null && now.account == account && now.key == key
		val type = glyphType ?: if (same) now!!.type else ProfileType.NORMAL
		val id = if (same) now!!.id else pendingId
		pendingId = null
		set(Profile(account, key, ProfileText.name(text), type, id))
	}

	private fun set(next: Profile?) {
		val before = current
		current = next
		// A profile or account switch, becoming unknown, or the id or type now known: the store needs the id to
		// tell a recreated profile from the old one. The same profile seen again changes nothing.
		if (before != next) {
			listeners.forEach { it.accept(next) }
		}
	}

	companion object {
		/** The tab widget's header. */
		const val WIDGET = "Profile"
	}
}
