package com.k8bas.skyblockutility.skyblock

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * One top-container slot of an opened menu (REQ-GS-06): its index, the item's registry id, the count, the
 * name, the lore lines from the server-sent item data (`DataComponents.LORE`, so tooltip mods do not change
 * them) and the SkyBlock id when the item has one.
 */
data class MenuSlot(val index: Int, val item: String, val count: Int, val name: String, val lore: List<String>, val skyblockId: String?)

/**
 * An opened menu at one stable moment (REQ-GS-06), immutable: the title, the server's state id, the number
 * of top-container slots and the non-empty ones among them. Player-inventory slots are never part of it.
 */
data class MenuSnapshot(val title: String, val stateId: Int, val size: Int, val slots: List<MenuSlot>) {
	/** The slot at [index], or null when it is empty or out of range. */
	fun slot(index: Int): MenuSlot? = slots.firstOrNull { it.index == index }

	/** The server has sent the contents (its state id is set, or a slot holds an item). */
	val hasContent: Boolean get() = stateId != 0 || slots.isNotEmpty()

	/**
	 * Same title and slots. The state id is left out: the server raises it for any slot of the open menu, the
	 * player's inventory included, and when it resends unchanged contents (e.g. after a click it refused).
	 */
	fun sameContent(other: MenuSnapshot?): Boolean =
		other != null && title == other.title && size == other.size && slots == other.slots
}

/** What a menu subscriber hears: a new stable snapshot, or that its menu is gone. */
sealed class MenuEvent {
	data class Shown(val menu: MenuSnapshot) : MenuEvent()

	object Closed : MenuEvent()
}

/** The open menu as the tracker sees it: the screen (by identity), its title, and how to read its contents. */
class OpenMenu(val screen: Any, val title: String, val read: Supplier<MenuSnapshot>)

/**
 * The menu reader's core (T3.0e, REQ-GS-06), free of game code. Each [tick] gets the open menu, or null.
 * It reads the contents only while an active subscriber's title pattern matches (REQ-GS-16). Contents are
 * stable once they arrived and stayed unchanged for [QUIET_TICKS] more ticks; each stable state is shown
 * once to each interested subscriber, a page or content change gives a new one (EC-GS-06), and a menu
 * closed before it is stable gives nothing (EC-GS-07). Only the content counts, not the state id, so the same
 * content is never shown twice in a row. A subscriber that was shown a menu hears when it is gone for it.
 * [clear] signals close and reads the menu still open afresh (EC-GS-08, EC-GS-09). Client thread only;
 * snapshots are immutable.
 */
class MenuTracker(private val guard: Runnable) {
	private class Subscriber(val owner: String, val active: BooleanSupplier, val title: Predicate<String>, val listener: Consumer<MenuEvent>) {
		var shown: MenuSnapshot? = null
	}

	private val subscribers = CopyOnWriteArrayList<Subscriber>()
	private var screen: Any? = null
	private var pending: MenuSnapshot? = null
	private var quiet = 0
	private var stable: MenuSnapshot? = null

	/** Hears the menus whose title [title] accepts while [active] is true. */
	fun subscribe(owner: String, active: BooleanSupplier, title: Predicate<String>, listener: Consumer<MenuEvent>) {
		subscribers.add(Subscriber(owner, active, title, listener))
	}

	/** The open menu's stable snapshot while someone wants it, else null. */
	fun current(): MenuSnapshot? = stable

	/** One tick with [open] as the open menu (null: none). */
	fun tick(open: OpenMenu?) = poll { open }

	/**
	 * One tick; [source] gives the open menu and is asked only while a subscriber is active, so with every
	 * consumer off not even the title is read (REQ-GS-16).
	 */
	fun poll(source: Supplier<OpenMenu?>) {
		guard.run()
		if (subscribers.none { it.active.asBoolean }) {
			closeShown()
			forget()
			return
		}
		val menu = source.get()
		val interested = if (menu == null) emptyList() else subscribers.filter { it.active.asBoolean && it.title.test(menu.title) }
		closeShown(interested)
		if (menu == null || interested.isEmpty()) {
			forget()
			return
		}
		if (menu.screen !== screen) {
			forget()
			screen = menu.screen
		}
		val read = menu.read.get()
		if (!read.sameContent(pending)) {
			pending = read
			quiet = 0
			stable = null
		} else if (stable == null && read.hasContent && ++quiet >= QUIET_TICKS) {
			stable = read
		}
		val now = stable ?: return
		for (subscriber in interested) {
			if (subscriber.shown === now) {
				continue
			}
			// Back to what it was shown last (a brief change, or a resend): nothing new to tell.
			val same = now.sameContent(subscriber.shown)
			subscriber.shown = now
			if (!same) {
				deliver(subscriber, MenuEvent.Shown(now))
			}
		}
	}

	/**
	 * Disconnect, server or world change, profile switch: close what was shown and forget the menu; one still
	 * open is read afresh, so a late clear (e.g. the first profile identification) does not lose it.
	 */
	fun clear() {
		guard.run()
		closeShown()
		forget()
	}

	/** Tells each subscriber that was shown a menu, and is not in [keep], that it is gone. */
	private fun closeShown(keep: List<Subscriber> = emptyList()) {
		for (subscriber in subscribers) {
			if (subscriber.shown != null && subscriber !in keep) {
				subscriber.shown = null
				deliver(subscriber, MenuEvent.Closed)
			}
		}
	}

	private fun forget() {
		screen = null
		pending = null
		quiet = 0
		stable = null
	}

	private fun deliver(subscriber: Subscriber, event: MenuEvent) {
		try {
			subscriber.listener.accept(event)
		} catch (e: RuntimeException) {
			LOGGER.error("A menu listener of {} failed", subscriber.owner, e)
		}
	}

	companion object {
		/** Unchanged ticks after the contents arrived before they count as stable. */
		const val QUIET_TICKS = 2

		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/skyblock")
	}
}
