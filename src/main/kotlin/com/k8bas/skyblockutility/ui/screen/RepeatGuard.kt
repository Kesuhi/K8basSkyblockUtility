package com.k8bas.skyblockutility.ui.screen

import java.util.function.LongSupplier

/**
 * Lets an action through at most once per [windowMs] (R31): a rule's Remove acts at once, and after a
 * removal the next rule's Remove slides under the cursor, so the second click of a double click must not
 * remove that one too. The window starts at the last action that passed. Pure, so it is tested with a fake clock.
 */
class RepeatGuard(private val windowMs: Long, private val clock: LongSupplier) {
	private var passed = false
	private var last = 0L

	/** True, and the window restarts, if no action passed in the last [windowMs]; false otherwise. */
	fun allow(): Boolean {
		val now = clock.asLong
		if (passed && now - last < windowMs) {
			return false
		}
		passed = true
		last = now
		return true
	}
}
