package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Profile identity (T3.0d, REQ-GS-08, AC-GS-08 [A], EC-GS-10, EC-GS-11). The tab line `Profile: Raspberry`
 * is the G1 capture's (tab-mineshaft.txt); the ironman glyph line and the chat lines are SPEC's (AC-GS-08);
 * the stranded and bingo glyphs and the `Profile ID:` line are UNVERIFIED until captured.
 */
class ProfileTrackerTest {
	private static final UUID ACCOUNT = new UUID(0L, 1L);
	private static final UUID OTHER = new UUID(0L, 2L);
	// Profile ids are UUIDs too; built from numbers, as the privacy scan allows no UUID literal.
	private static final String ID_A = new UUID(0L, 0xabcdL).toString();
	private static final String ID_B = new UUID(0L, 0xffffL).toString();

	private final AtomicReference<UUID> account = new AtomicReference<>(ACCOUNT);
	private final List<Profile> changes = new ArrayList<>();
	private final ProfileTracker tracker = new ProfileTracker(account::get);

	ProfileTrackerTest() {
		tracker.onChange(changes::add);
	}

	private static TabSnapshot profileWidget(String inline) {
		return new TabSnapshot(List.of(new TabWidget("Profile", inline, List.of("SB Level: [435] 28/100 XP"), false)), List.of());
	}

	@Test
	void theTabNameAndGlyphGiveKeyAndType() {
		// AC-GS-08: `Profile: Strawberry ♲` gives the key `strawberry` and the type ironman.
		tracker.tab(profileWidget("Strawberry ♲"), false);
		Profile profile = tracker.current();
		assertEquals("strawberry", profile.getKey());
		assertEquals("Strawberry", profile.getName());
		assertEquals(ProfileType.IRONMAN, profile.getType());
		assertEquals(ACCOUNT, profile.getAccount());
	}

	@Test
	void theCapturedProfileIsNormal() {
		tracker.tab(TabParser.parse(CaptureFixtures.tabRows("tab-mineshaft.txt")), false);
		assertEquals("raspberry", tracker.current().getKey());
		assertEquals(ProfileType.NORMAL, tracker.current().getType());
	}

	@Test
	void aChatLineIdentifiesTheProfileWithoutTheWidget() {
		// AC-GS-08: no widget, the profile chat line (the reader sees it even when another mod cancels it).
		tracker.tab(TabSnapshot.EMPTY, false);
		assertNull(tracker.current());
		tracker.chat("You are playing on profile: Raspberry (Co-op)");
		assertEquals("raspberry", tracker.current().getKey());
	}

	@Test
	void theProfileIdIsAttachedWhenSeen() {
		tracker.chat("Profile ID: " + ID_A);
		assertNull(tracker.current(), "an id alone does not identify the profile");
		tracker.chat("You are playing on profile: Raspberry");
		assertEquals(ID_A, tracker.current().getId(), "an id seen first is attached");
		tracker.chat("Profile ID: " + ID_B);
		assertEquals(ID_B, tracker.current().getId());
	}

	@Test
	void anotherAccountIsAnotherKey() {
		// AC-GS-08: a different account UUID keeps a separate key.
		tracker.tab(profileWidget("Raspberry"), false);
		Profile first = tracker.current();
		account.set(OTHER);
		tracker.tab(profileWidget("Raspberry"), false);
		assertNotEquals(first, tracker.current());
		assertEquals(OTHER, tracker.current().getAccount());
		assertEquals(2, changes.size());
	}

	@Test
	void aChangeEventFiresOnASwitchOnly() {
		tracker.tab(profileWidget("Raspberry"), false);
		tracker.tab(profileWidget("Raspberry"), false);
		tracker.chat("You are playing on profile: Raspberry");
		assertEquals(1, changes.size());
		tracker.tab(profileWidget("Strawberry ♲"), false);
		assertEquals(2, changes.size());
		assertEquals("strawberry", changes.get(1).getKey());
	}

	@Test
	void theReversedRiftNameIsIgnored() {
		// EC-GS-10: in the Rift the tab name reads backwards; the last identified profile is kept.
		tracker.tab(profileWidget("Raspberry"), false);
		tracker.tab(profileWidget("yrrebpsaR"), true);
		assertEquals("raspberry", tracker.current().getKey());
		assertEquals(1, changes.size());
	}

	@Test
	void theCurrentNameReversedIsNeverANewProfile() {
		// A second guard for EC-GS-10: the Rift's tab read before the island is known.
		tracker.tab(profileWidget("Raspberry"), false);
		tracker.tab(profileWidget("yrrebpsaR"), false);
		assertEquals("raspberry", tracker.current().getKey());
		assertEquals(1, changes.size());
	}

	@Test
	void aMissingGlyphKeepsTheTypeAsLastSeen() {
		// EC-GS-11: dungeon and Kuudra tab lists have no type glyph.
		tracker.tab(profileWidget("Strawberry ♲"), false);
		tracker.tab(profileWidget("Strawberry"), false);
		assertEquals(ProfileType.IRONMAN, tracker.current().getType());
	}

	@Test
	void otherGlyphsGiveTheirTypes() {
		tracker.tab(profileWidget("Kiwi ☀"), false);
		assertEquals(ProfileType.STRANDED, tracker.current().getType());
		tracker.tab(profileWidget("Lime Ⓑ"), false);
		assertEquals(ProfileType.BINGO, tracker.current().getType());
	}

	@Test
	void theKeyIsCaseFoldedAndTrimmed() {
		assertEquals("blueberry", ProfileText.key("  BlueBerry ♲ "));
		assertEquals("raspberry", ProfileText.key("Raspberry (Co-op)"));
	}

	@Test
	void aResetMakesItUnknownAgain() {
		tracker.tab(profileWidget("Raspberry"), false);
		tracker.reset();
		assertNull(tracker.current());
		assertNull(changes.get(changes.size() - 1), "listeners hear the profile is unknown");
	}

	@Test
	void playerChatIsNoProfileLine() {
		// The dispatcher filters player chat before this, but a quoted line must not match either way.
		tracker.chat("[326] Someone: You are playing on profile: Fake");
		assertNull(tracker.current());
	}
}
