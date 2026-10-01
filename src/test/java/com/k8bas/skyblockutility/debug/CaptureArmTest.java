package com.k8bas.skyblockutility.debug;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-GS-12 [A]: the capture disarms itself after 60 minutes. */
class CaptureArmTest {
	private static final long MINUTE = 60_000;
	private final AtomicLong now = new AtomicLong(1_000_000);
	private final CaptureArm arm = new CaptureArm(now::get);

	@Test
	void startsDisarmed() {
		assertFalse(arm.isArmed());
		assertFalse(arm.expiredNow());
	}

	@Test
	void staysArmedJustUnderAnHour() {
		arm.arm();
		now.addAndGet(60 * MINUTE - 1);
		assertTrue(arm.isArmed());
		assertFalse(arm.expiredNow());
	}

	@Test
	void disarmsAtSixtyMinutesAndReportsItOnce() {
		arm.arm();
		now.addAndGet(60 * MINUTE);
		assertFalse(arm.isArmed());
		assertTrue(arm.expiredNow());
		assertFalse(arm.expiredNow());
	}

	@Test
	void manualDisarmIsNotReportedAsExpiry() {
		arm.arm();
		arm.disarm();
		now.addAndGet(61 * MINUTE);
		assertFalse(arm.isArmed());
		assertFalse(arm.expiredNow());
	}

	@Test
	void reArmingRestartsTheHour() {
		arm.arm();
		now.addAndGet(50 * MINUTE);
		arm.arm();
		now.addAndGet(50 * MINUTE);
		assertTrue(arm.isArmed());
	}
}
