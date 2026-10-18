package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** T4.4 (R50, REQ-UPD-04): the automatic check runs at startup and at the first server join of the session, and at no other time. */
class SessionChecksTest {
	@Test
	void startupAndTheFirstJoinOnly() {
		AtomicInteger runs = new AtomicInteger();
		SessionChecks checks = new SessionChecks(() -> {
			runs.incrementAndGet();
			return true;
		});
		checks.started();
		assertEquals(1, runs.get());
		checks.joined();
		assertEquals(2, runs.get());
		checks.joined();
		checks.joined();
		assertEquals(2, runs.get(), "later joins in the session check nothing");
		checks.started();
		assertEquals(2, runs.get(), "startup happens once");
	}

	/** Review (T4.4): a join while the startup check still runs (offline) did not start one; the next join is the retry. */
	@Test
	void aJoinThatStartedNothingIsTriedAgain() {
		AtomicInteger runs = new AtomicInteger();
		AtomicBoolean busy = new AtomicBoolean(true);
		SessionChecks checks = new SessionChecks(() -> {
			runs.incrementAndGet();
			return !busy.get();
		});
		checks.joined();
		assertEquals(1, runs.get());
		busy.set(false);
		checks.joined();
		assertEquals(2, runs.get(), "tried again");
		checks.joined();
		assertEquals(2, runs.get(), "once started, done for the session");
	}
}
