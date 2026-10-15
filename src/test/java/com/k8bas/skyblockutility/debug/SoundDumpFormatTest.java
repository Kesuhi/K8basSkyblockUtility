package com.k8bas.skyblockutility.debug;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-GS-19 [A] (T3.3c): one sound the client plays is one `[K8BAS-DUMP] sound` line with everything a ding capture
 * needs (id, file, category, where, how loud, which pitch, when, and the player's position and distance), and the
 * sound capture disarms itself after 10 minutes.
 */
class SoundDumpFormatTest {
	@Test
	void aPositionedSoundHasEveryField() {
		SoundDumpFormat.Sound harp = new SoundDumpFormat.Sound("minecraft:block.note_block.harp", "minecraft:note/harp", "record",
				-120.5, 12.0, -170.5, false, "LINEAR", 3.0f, 1.4142f, 48.0f);
		assertEquals("[K8BAS-DUMP] sound 7 t=+1234ms tick=5678 id=minecraft:block.note_block.harp file=minecraft:note/harp src=record"
						+ " pos=-120.50,12.00,-170.50 rel=false att=LINEAR vol=3.00 pitch=1.414 range=48.0 player=-118.30,11.00,-168.20 d=3.34",
				SoundDumpFormat.line(7, 1234, 5678, harp, new double[] {-118.3, 11.0, -168.2}));
	}

	@Test
	void withoutAPlayerTheDistanceIsADash() {
		SoundDumpFormat.Sound click = new SoundDumpFormat.Sound("minecraft:ui.button.click", "-", "ui", 0, 0, 0, true, "NONE", 0.25f, 1.0f, 16.0f);
		assertTrue(SoundDumpFormat.line(1, 0, 0, click, null).endsWith(" rel=true att=NONE vol=0.25 pitch=1.000 range=16.0 player=- d=-"));
	}

	@Test
	void theSoundCaptureDisarmsAfterTenMinutes() {
		AtomicLong now = new AtomicLong(1_000);
		CaptureArm arm = new CaptureArm(now::get, SoundDump.ARMED_MS);
		arm.arm();
		now.addAndGet(10 * 60_000 - 1);
		assertTrue(arm.isArmed());
		now.addAndGet(1);
		assertFalse(arm.isArmed());
		assertTrue(arm.expiredNow());
	}
}
