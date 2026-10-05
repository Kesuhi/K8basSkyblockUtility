package com.k8bas.skyblockutility.ui.widget

import java.util.function.Predicate

/**
 * Steps of keyboard navigation (REQ-UI-18, T2.9b), pure: through the Tab order (wrapping), to the control that is
 * left when the focused one goes, and through a list of choices. Controls are compared by identity, and nothing
 * is allocated.
 */
object FocusCycle {
	/** The next (or previous) in [order] after [current]; the first (or last) when [current] is null or not in it. */
	@JvmStatic
	fun <T : Any> step(order: List<T>, current: T?, forward: Boolean): T? {
		if (order.isEmpty()) {
			return null
		}
		val index = indexOf(order, current)
		if (index < 0) {
			return if (forward) order[0] else order[order.size - 1]
		}
		val next = if (forward) index + 1 else index - 1
		return order[(next + order.size) % order.size]
	}

	/**
	 * What takes the focus when the one at [index] of [before] is gone: the first after it still [present] (the next
	 * rule's header after a removal, as its Remove went too), else the nearest before it, else none.
	 */
	@JvmStatic
	fun <T : Any> survivor(before: List<T>, index: Int, present: Predicate<T>): T? {
		for (i in index + 1 until before.size) {
			if (present.test(before[i])) {
				return before[i]
			}
		}
		for (i in minOf(index, before.size) - 1 downTo 0) {
			if (present.test(before[i])) {
				return before[i]
			}
		}
		return null
	}

	/**
	 * The index a list key moves to from [current] in a list of [count] (a dropdown's values, the sidebar's tabs):
	 * one up or down, to an end, or five at a time; held to the list. -1 for an empty list or a key that is not a
	 * list key. Nothing chosen yet ([current] -1) starts at the first.
	 */
	@JvmStatic
	fun listStep(current: Int, count: Int, key: NavKey): Int {
		if (count <= 0) {
			return -1
		}
		val target = when (key) {
			NavKey.UP, NavKey.LEFT -> if (current < 0) 0 else current - 1
			NavKey.DOWN, NavKey.RIGHT -> if (current < 0) 0 else current + 1
			NavKey.HOME -> 0
			NavKey.END -> count - 1
			NavKey.PAGE_UP -> if (current < 0) 0 else current - PAGE
			NavKey.PAGE_DOWN -> if (current < 0) 0 else current + PAGE
			else -> return -1
		}
		return target.coerceIn(0, count - 1)
	}

	/** By identity, so two equal options are still two places. */
	@JvmStatic
	fun <T : Any> indexOf(order: List<T>, value: T?): Int {
		if (value == null) {
			return -1
		}
		for (i in order.indices) {
			if (order[i] === value) {
				return i
			}
		}
		return -1
	}

	private const val PAGE = 5
}
