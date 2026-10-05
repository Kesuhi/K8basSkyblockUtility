package com.k8bas.skyblockutility.ui.widget

import java.util.function.Predicate

/**
 * The keyboard focus of a screen with keyboard navigation (REQ-UI-18, T2.9b), apart from typing: the control that
 * has the focus ring and takes Space, Enter and the arrows while nothing is typing. A field being typed in keeps
 * its own focus in the screen (keyboardFocus), so the Esc order of REQ-UI-08/14/22 stays as it is: this focus is
 * never an Esc step. Pure and generic, so it is tested with strings.
 */
class KeyboardNav<W : Any> {
	/** The focused control, or null. */
	var focus: W? = null

	/** Whether the ring shows: after a key, not after a click. */
	var ringVisible = false

	/** Whether the focus is in the open overlay (a modal's controls) rather than the screen. */
	var focusInOverlay = false

	/** The control that had the focus when an overlay opened; it gets it back when the overlay closes. */
	private var beforeOverlay: W? = null

	/** The order the focus last moved in, to find what is left when the focused control goes. */
	private val snapshot = ArrayList<W>()

	private var activationKey = NO_KEY
	private var swallowSpaceChar = false

	/** Tab or Shift+Tab: the next (previous) control in [order], which becomes the focus with the ring showing. */
	fun navigate(order: List<W>, forward: Boolean): W? {
		val next = FocusCycle.step(order, focus, forward) ?: return null
		focus = next
		ringVisible = true
		remember(order)
		return next
	}

	/** Keeps [order] as the order the focus moved in last. */
	fun remember(order: List<W>) {
		snapshot.clear()
		snapshot.addAll(order)
	}

	/**
	 * A click: the ring hides, and the clicked control (null: none) is where Tab goes on from. A click that opened an
	 * overlay parks the control instead, for when the overlay closes.
	 */
	fun pointer(control: W?, overlayOpenedByIt: Boolean) {
		ringVisible = false
		if (overlayOpenedByIt) {
			beforeOverlay = control
		} else {
			focus = control
		}
	}

	/** An overlay opened: nothing in the screen behind it has the focus. */
	fun overlayOpened() {
		if (beforeOverlay == null) {
			beforeOverlay = focus
		}
		focus = null
		focusInOverlay = false
	}

	/** The overlay closed: the control that had the focus (or opened it) has it again. */
	fun overlayClosed() {
		focus = beforeOverlay
		beforeOverlay = null
		focusInOverlay = false
	}

	/** A key activated the focus; until it is released its repeats do nothing, and a Space types no space. */
	fun activated(keyId: Int, space: Boolean) {
		activationKey = keyId
		swallowSpaceChar = space
	}

	fun isActivationRepeat(keyId: Int): Boolean = activationKey != NO_KEY && activationKey == keyId

	/** True once for the release of the key that activated. */
	fun released(keyId: Int): Boolean {
		if (activationKey != NO_KEY && activationKey == keyId) {
			activationKey = NO_KEY
			return true
		}
		return false
	}

	/** True once for the space character that follows a Space activation. */
	fun takeSpaceChar(codePoint: Int): Boolean {
		if (swallowSpaceChar && codePoint == ' '.code) {
			swallowSpaceChar = false
			return true
		}
		return false
	}

	/** The control to draw the ring around: none while hidden, for the control being typed in, or in the other layer. */
	fun ringTarget(typing: W?, overlayOpen: Boolean): W? {
		val focused = focus ?: return null
		return if (ringVisible && focused !== typing && overlayOpen == focusInOverlay) focused else null
	}

	/** If the focus is gone (removed, filtered away), the control after it in the last order that is still [present]. */
	fun repair(present: Predicate<W>) {
		val focused = focus ?: return
		if (present.test(focused)) {
			return
		}
		val index = FocusCycle.indexOf(snapshot, focused)
		focus = if (index < 0) null else FocusCycle.survivor(snapshot, index, present)
	}

	fun clear() {
		focus = null
		ringVisible = false
		focusInOverlay = false
		beforeOverlay = null
		snapshot.clear()
		activationKey = NO_KEY
		swallowSpaceChar = false
	}

	private companion object {
		const val NO_KEY = Int.MIN_VALUE
	}
}
