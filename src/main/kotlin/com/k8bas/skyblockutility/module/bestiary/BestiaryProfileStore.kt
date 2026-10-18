package com.k8bas.skyblockutility.module.bestiary

import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * When the tracker's last-known values are read from and written to the profile store (T5.2d, REQ-BEST-14). Nothing is
 * read or written while the profile is unknown or its store not verified ([storable]), or while the family table is
 * missing (the stored rows would have nowhere to go and the next write would drop them). A profile's values are read when
 * it becomes known, and again whenever the tracker has forgotten a profile since (a switch away and back), so they show
 * with their age after a login and the file is never cut down to what was seen after the switch; the tracker forgets the
 * last profile before, so a file never gets another profile's values. Changed values, also those seen before the read,
 * are written at most every 2 s (the store merges disk writes as well). A file this version cannot read is never
 * overwritten, and [warn] says so once per profile.
 *
 * @param profile the current profile's identity (account and name), null while unknown
 */
class BestiaryProfileStore(
	private val read: Supplier<String?>,
	private val write: Consumer<String>,
	private val storable: BooleanSupplier,
	private val profile: Supplier<Any?>,
	private val warn: Consumer<String>,
) {
	private var readFor: Any? = null
	private var readGeneration = -1L
	private var blocked = false
	private var written = 0L
	private var writtenAt = 0L

	/** Once per client tick while the tracker is ON. */
	fun tick(tracker: BestiaryTracker, now: Long) {
		if (!storable.asBoolean || !tracker.hasTable()) {
			return
		}
		val current = profile.get() ?: return
		if (current != readFor || tracker.generation() != readGeneration) {
			if (current != readFor) {
				blocked = false
			}
			readFor = current
			readGeneration = tracker.generation()
			val content = read.get()
			if (!tracker.restore(content) && content != null && !blocked) {
				blocked = true
				warn.accept("Bestiary: this profile's stored values are in a form this version cannot read; they are kept and not overwritten")
			}
			// What the tracker showed before the read is not in the file yet.
			written = -1
			writtenAt = now
			return
		}
		if (!blocked && tracker.changes() != written && now - writtenAt >= WRITE_EVERY_MS) {
			write.accept(tracker.snapshot())
			written = tracker.changes()
			writtenAt = now
		}
	}

	private companion object {
		const val WRITE_EVERY_MS = 2_000L
	}
}
