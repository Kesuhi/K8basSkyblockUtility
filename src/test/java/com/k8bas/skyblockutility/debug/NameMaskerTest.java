package com.k8bas.skyblockutility.debug;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Decision 2026-10-01 (G1, S-7), REQ-GS-13 / REQ-XC-PRIVACY-01: real player names are masked in dumps. */
class NameMaskerTest {
	private static final UUID REAL = new UUID(0x1b2c3d4e5f604a7bL, 0x8c9daebfc0d1e2f3L);
	private static final UUID NPC = new UUID(0x1b2c3d4e5f602a7bL, 0x8c9daebfc0d1e2f3L);

	@Test
	void yourselfAndOtherPlayersGetStablePlaceholders() {
		NameMasker masker = new NameMasker();
		masker.learnSelf("Tester_01");
		masker.learnPlayer("Alice", REAL);
		masker.learnPlayer("Bob", REAL);
		masker.learnPlayer("Alice", REAL);

		assertEquals("[MVP+] Self joined, then Player1 and Player2", masker.mask("[MVP+] Tester_01 joined, then Alice and Bob"));
		assertEquals("order=3 name=Player1 | [VIP] Player1 ♲", masker.mask("order=3 name=Alice | [VIP] Alice ♲"));
		masker.learnPlayer("Carol", REAL);
		assertEquals("Player1 Player3", masker.mask("Alice Carol"), "numbers stay the same for the session");
	}

	@Test
	void onlyWholeNamesAreReplaced() {
		NameMasker masker = new NameMasker();
		masker.learnPlayer("Steve", REAL);
		assertEquals("Steven, xSteve, Steve_2, Player1!", masker.mask("Steven, xSteve, Steve_2, Steve!"));
		assertEquals("steve", masker.mask("steve"), "case-sensitive, so ordinary words stay");
	}

	@Test
	void npcsFakeTabProfilesAndNonNamesAreKept() {
		NameMasker masker = new NameMasker();
		masker.learnPlayer("Trinity", NPC);
		masker.learnPlayer("!B-a", REAL);
		masker.learnPlayer("ab", REAL);
		masker.learnPlayer("a_name_longer_than_16", REAL);
		masker.learnPlayer(null, REAL);
		masker.learnPlayer("Dave", null);
		assertEquals("Trinity !B-a ab", masker.mask("Trinity !B-a ab"));
		assertFalse(masker.knows("Dave"));
		assertEquals("line", new NameMasker().mask("line"), "nothing learnt, nothing changed");
	}

	@Test
	void selfWinsOverAnEarlierPlayerEntry() {
		NameMasker masker = new NameMasker();
		masker.learnPlayer("Tester_01", REAL);
		masker.learnSelf("Tester_01");
		assertEquals("Self", masker.mask("Tester_01"));
		assertTrue(masker.knows("Tester_01"));
	}
}
