package com.k8bas.skyblockutility.skyblock

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/** One game message from the server (REQ-GS-04): its text without formatting, and whether it is the action bar. */
data class ServerMessage(val text: String, val overlay: Boolean)

/**
 * Delivers each game message from the server exactly once (REQ-GS-04). Fabric 0.161 runs a message as
 * ALLOW_GAME (every listener, none skipped), then exactly one of GAME and GAME_CANCELED, synchronously on
 * the client thread. [allow] is the early ALLOW_GAME listener: it sees the text as the server sent it, before
 * any MODIFY_GAME, delivers it and always lets it through. [exit] is the late GAME and GAME_CANCELED listener.
 * A line another mod shows while a message is being handled (a chat rule that hides a line and posts its
 * own) arrives nested between the two and is not a server line, so it is not delivered: the client handles
 * packets one at a time. [tick] resets the depth in case a mod's listener threw before GAME or GAME_CANCELED.
 * It never cancels or changes anything. Player chat (REQ-GS-05) and this mod's own chat lines never reach
 * subscribers, and with no active subscriber nothing is looked at (REQ-GS-16). Client thread only.
 *
 * Known limit: a line another mod shows on its own (not while handling a message) passes the same events
 * and cannot be told from a server line without a hook below them; consumers match from the start of the
 * line and deduplicate by meaning (EC-GS-05).
 *
 * @param ownMessage true while this mod itself is showing a chat line (ChatUtils)
 * @param failure told once per subscriber that throws, with its owner name (never the message text)
 */
class MessageDispatch @JvmOverloads constructor(
	private val ownMessage: BooleanSupplier = BooleanSupplier { false },
	private val failure: BiConsumer<String, RuntimeException> = BiConsumer { owner, e -> LOGGER.error("Message subscriber {} failed; further failures are not logged", owner, e) },
) {
	private class Subscriber(val owner: String, val active: BooleanSupplier, val listener: Consumer<ServerMessage>) {
		var failed = false
	}

	private val subscribers = CopyOnWriteArrayList<Subscriber>()
	private var depth = 0
	private var delivered = 0

	/** [listener] hears every server message while [active] is true. */
	fun subscribe(owner: String, active: BooleanSupplier, listener: Consumer<ServerMessage>) {
		subscribers.add(Subscriber(owner, active, listener))
	}

	/** The early ALLOW_GAME listener: delivers a top-level message and always lets it through. */
	fun allow(text: String, overlay: Boolean): Boolean {
		val topLevel = depth == 0
		depth++
		if (topLevel) {
			deliver(text, overlay)
		}
		return true
	}

	/** The late GAME and GAME_CANCELED listener: the message's handling is over. */
	fun exit() {
		if (depth > 0) {
			depth--
		}
	}

	/** Once per client tick, outside any message: a handling that never ended is forgotten. */
	fun tick() {
		depth = 0
	}

	/** How many messages reached subscribers. */
	fun deliveredCount(): Int = delivered

	private fun deliver(text: String, overlay: Boolean) {
		val listening = subscribers.filter { it.active.asBoolean }
		if (listening.isEmpty() || ownMessage.asBoolean) {
			return
		}
		val plain = GameText.strip(text)
		if (!overlay && ChatLines.isPlayerChat(plain)) {
			return
		}
		val message = ServerMessage(plain, overlay)
		delivered++
		for (subscriber in listening) {
			try {
				subscriber.listener.accept(message)
			} catch (e: RuntimeException) {
				if (!subscriber.failed) {
					subscriber.failed = true
					failure.accept(subscriber.owner, e)
				}
			}
		}
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/skyblock")
	}
}
