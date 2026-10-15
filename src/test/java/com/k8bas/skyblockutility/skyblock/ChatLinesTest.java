package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-GS-05 / AC-GS-05 [A]: lines a player typed never reach server-event parsers. The two AC fixtures are
 * from SPEC; the other shapes are Hypixel's chat formats as players see them (UNVERIFIED until captured: the
 * G1 dumps hold no chat). Known limit: unranked chat shown without a level (`Name: text`) has the shape of a
 * server `Word: text` line, so server-event parsers match from the start of the line (see ChatLines).
 */
class ChatLinesTest {
	@Test
	void theAcceptanceFixturesArePlayerChat() {
		// AC-GS-05: a public-chat paste of a level-up line, and a guild-chat paste of an action-bar XP echo.
		assertTrue(ChatLines.isPlayerChat("[326] ☀ [MVP+] X: SKYBLOCK LEVEL UP Level 325 ➡ [326]"));
		assertTrue(ChatLines.isPlayerChat("Guild > [MVP+] X [Officer]: +1,234 Mining (12,345/50,000)"));
	}

	@Test
	void everyChannelAndSenderShapeIsPlayerChat() {
		for (String line : List.of(
				"Party > [MVP+] X: SKYBLOCK LEVEL UP",
				"Party > X: hi",
				"Officer > [VIP] Someone_1: hi",
				"Co-op > [MVP++] Someone: +5 SkyBlock XP",
				"Guild > Someone: hi",
				"From [MVP+] Someone: You are playing on profile: Raspberry",
				"To Someone: hi",
				"[326] Someone: SKYBLOCK LEVEL UP Level 325 ➡ [326]",
				"[12] ⓑ Someone: Profile ID: 1234",
				"[VIP+] Someone: RARE DROP! (Enchanted Book)",
				"[YOUTUBE] Someone: hi",
				// Any short rank bracket counts, e.g. Hypixel's staff rank, and ranks added later.
				"[326] [ዞ] SomeAdmin: RARE DROP! (Enchanted Book)",
				"[ዞ] SomeAdmin: SKYBLOCK LEVEL UP Level 325 ➡ [326]",
				// Several glyphs before the rank (emblem, game mode) and after the name.
				"[326] ⚚ ♲ [MVP+] X: SKYBLOCK LEVEL UP Level 325 ➡ [326]",
				"[326] [MVP+] X ♲: +5 SkyBlock XP (Run the race)",
				"[326] X ♲ [GUILD]: hi")) {
			assertTrue(ChatLines.isPlayerChat(line), line);
		}
	}

	@Test
	void serverLinesAreNotPlayerChat() {
		for (String line : List.of(
				"SKYBLOCK LEVEL UP Level 325 ➡ [326]",
				"You are playing on profile: Raspberry (Co-op)",
				"Profile ID: 1234",
				"RARE DROP! Enchanted Book (+250 ✯ Magic Find)",
				"[NPC] Kat: Hello there!",
				"[BOSS] Maxor: WELL WELL WELL",
				"[STATUE] Oruo the Omniscient: I am Oruo",
				"[CROWD] Someone: Go go go!",
				"[SECRET] You found a secret!",
				"Commission Completed: Lapis Collector",
				"+5 SkyBlock XP (Run the race in 120s)",
				"  SKYBLOCK GUIDE",
				"",
				"Objective: Kill 10 Zombies")) {
			assertFalse(ChatLines.isPlayerChat(line), line);
		}
	}

	@Test
	void formattingCodesDoNotHideTheShape() {
		assertTrue(ChatLines.isPlayerChat("§2Guild > §b[MVP§c+§b] X§f: hi"));
		assertFalse(ChatLines.isPlayerChat("§6§lSKYBLOCK LEVEL UP §3Level 325"));
	}
}
