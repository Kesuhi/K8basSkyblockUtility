package com.k8bas.skyblockutility.update;

import java.util.concurrent.atomic.AtomicReference;

/**
 * At most one update check or download in flight (REQ-UPD-04). A holder that cannot take it does not wait: an automatic
 * check is skipped, a manual one or a download answers what holds it.
 */
final class UpdateLock {
	/** What holds the lock. */
	enum Holder {
		CHECK,
		DOWNLOAD
	}

	private final AtomicReference<Holder> held = new AtomicReference<>();

	/** Takes the lock for a check if it is free; true if this caller now holds it. */
	boolean tryAcquire() {
		return acquireOrHolder(Holder.CHECK) == null;
	}

	/**
	 * Takes the lock for {@code holder} if it is free. Returns null if this caller now holds it, else what held it at that
	 * moment, read in the same step, so the answer never names a holder that had already let go.
	 */
	Holder acquireOrHolder(Holder holder) {
		return held.compareAndExchange(null, holder);
	}

	void release() {
		held.set(null);
	}
}
