package com.k8bas.skyblockutility.module.corpsespots

import com.k8bas.skyblockutility.skyblock.MineshaftSnapshot

/**
 * One `/ksu corpses` waiting for the mineshaft state (REQ-CORPSE-06, REQ-GS-16). With no feature reading the shaft,
 * the state starts only on the next tick after the command keeps it, and the code search can take up to 15 s; so the
 * reply waits until the state is in the shaft with its search over, or [WAIT_MS] has passed, and then answers from
 * whatever the state has. Outside a shaft it answers at once.
 */
class CorpseListRequest(private val startedMs: Long) {
	/** Whether to answer now: outside a shaft, once the shaft is read, or when the wait is over. */
	fun ready(shaft: MineshaftSnapshot, inShaftIsland: Boolean, nowMs: Long): Boolean =
		!inShaftIsland || (shaft.inShaft && !shaft.searching) || nowMs - startedMs >= WAIT_MS

	companion object {
		/** Just past the 15 s code search, so a shaft whose code never comes still gets an answer. */
		const val WAIT_MS = 16_000L
	}
}
