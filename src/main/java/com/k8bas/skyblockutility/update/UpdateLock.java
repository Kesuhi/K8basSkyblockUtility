package com.k8bas.skyblockutility.update;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * At most one update check or download in flight (REQ-UPD-04). A holder that cannot take it does not wait: an automatic
 * check is skipped, a manual one answers "already running".
 */
final class UpdateLock {
	private final AtomicBoolean held = new AtomicBoolean();

	/** Takes the lock if it is free; true if this caller now holds it. */
	boolean tryAcquire() {
		return held.compareAndSet(false, true);
	}

	void release() {
		held.set(false);
	}
}
