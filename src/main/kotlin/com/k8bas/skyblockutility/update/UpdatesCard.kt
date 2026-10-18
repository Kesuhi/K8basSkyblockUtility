package com.k8bas.skyblockutility.update

import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.LongSupplier

/**
 * The General › Updates card over the running service (REQ-UPD-10, T4.4b): the running version, the latest known one, the
 * last check time and a status, and the card's four buttons. Its texts are read every frame, so they come from what the
 * service already knows ([UpdateService.lastOffer], the last answer's time, the pending install), never from the network,
 * and each is built again only when what it shows changed. The buttons run through [Actions], off the render thread.
 *
 * @param clock now, epoch milliseconds, for "today"
 */
class UpdatesCard(
	private val service: UpdateService,
	private val zone: ZoneId,
	private val clock: LongSupplier,
	private val actions: Actions,
) : UpdatesPanel {
	/** What the buttons start; the game's runs them in the background and opens screens on the client thread. */
	interface Actions {
		/** Runs an update action off the render thread. */
		fun run(action: Function<UpdateActions, List<String>>): CompletableFuture<List<String>>

		/** Opens the confirmation screen for the candidate on offer, over the settings. */
		fun showScreen()

		/** Opens [page] through the game's link confirmation. */
		fun openLink(page: URI)
	}

	private val version: String = "v" + (SemVer.parseOrNull(service.runningVersion())?.toString() ?: service.runningVersion())

	/** The card's last answer (a check, a cancel, why Install had nothing to show), shown until the next. */
	@Volatile
	private var answer: List<String> = listOf()

	private var latestFor: Any? = NONE
	private var latestText = ""
	private var lastCheckMillis = Long.MIN_VALUE
	private var lastCheckMinute = Long.MIN_VALUE
	private var lastCheckText = ""
	private var statusFor: List<Any?> = listOf(NONE)
	private var statusText = ""

	override fun version(): String = version

	override fun latest(): String {
		val offer = service.lastOffer()
		if (offer !== latestFor) {
			latestFor = offer
			latestText = when (offer) {
				null -> NOT_CHECKED
				is CheckOutcome.Found -> "v${offer.candidate.version}" + (if (offer.beta) " (beta)" else "") + (if (offer.skipped) " (skipped)" else "")
				is CheckOutcome.NoBuild -> "v${offer.newest}, no build for Minecraft ${offer.minecraft}"
				is CheckOutcome.NotARelease -> offer.newest?.let { "v$it" } ?: "None known"
				else -> "None newer"
			}
		}
		return latestText
	}

	override fun lastCheck(): String {
		val millis = service.lastSuccessMs()
		// The day changes too; a minute's resolution keeps "Today" right.
		val minute = clock.asLong / 60_000
		if (millis != lastCheckMillis || minute != lastCheckMinute) {
			lastCheckMillis = millis
			lastCheckMinute = minute
			lastCheckText = if (millis <= 0) {
				"Never"
			} else {
				val at = Instant.ofEpochMilli(millis).atZone(zone)
				val today = Instant.ofEpochMilli(clock.asLong).atZone(zone).toLocalDate()
				if (at.toLocalDate() == today) "Today, " + TIME.format(at) else DATE_TIME.format(at)
			}
		}
		return lastCheckText
	}

	/** The pending install first (T4.3b), then the card's last answer, then the last check's own text (T4.1). */
	override fun status(): String {
		val pending = service.pendingInstall()
		val offer = service.lastOffer()
		val shown = answer
		val key = listOf(pending, shown, offer)
		if (key != statusFor) {
			statusFor = key
			statusText = when {
				pending != null -> "Update v${pending.version()} ready, restart Minecraft to apply"
				shown.isNotEmpty() -> joined(shown)
				offer != null -> joined(offer.lines(zone))
				else -> NOT_CHECKED
			}
		}
		return statusText
	}

	override fun defaultChannel(): UpdateChannel = UpdateChannel.effective(null, SemVer.parseOrNull(service.runningVersion()))

	override fun checkNow() {
		answerWith(Function { it.check() })
	}

	/** The confirmation screen when an update is on offer; otherwise why not, in the status, as the chat is hidden here. */
	override fun install() {
		when (val offer = service.lastOffer()) {
			is CheckOutcome.Found -> actions.showScreen()
			null -> answer = listOf("$NOT_CHECKED, press Check now")
			else -> answer = offer.lines(zone)
		}
	}

	override fun openReleasePage() {
		val page = when (val offer = service.lastOffer()) {
			is CheckOutcome.Found -> offer.candidate.release.htmlUrl()
			is CheckOutcome.NoBuild -> offer.releasePage
			else -> null
		}
		actions.openLink(UpdateNotifier.releasePage(page))
	}

	override fun cancelPending() {
		answerWith(Function { it.cancel() })
	}

	override fun channelChanged() {
		answer = listOf()
		actions.run(Function { it.refresh() })
	}

	/** One line of sentences: a check's lines are separate sentences without full stops. */
	private fun joined(lines: List<String>): String = lines.joinToString("; ")

	private fun answerWith(action: Function<UpdateActions, List<String>>) {
		actions.run(action).whenComplete { lines, error ->
			answer = if (error != null || lines == null) listOf("The update action failed, see the log") else lines
		}
	}

	private companion object {
		const val NOT_CHECKED = "Not checked this session"
		val NONE = Any()
		val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
		val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
	}
}
