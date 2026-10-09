package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-GS-04 [A]: every game message reaches subscribers exactly once, with its overlay flag and the text the
 * server sent. Fabric 0.161 calls every ALLOW_GAME listener (no short-circuit) and then exactly one of
 * GAME and GAME_CANCELED, all synchronously on the client thread, so a line a mod posts while another is
 * being handled arrives nested and is not a server line. REQ-GS-05: player chat never reaches subscribers.
 * REQ-GS-16: nothing is done while unsubscribed.
 */
class MessageDispatchTest {
	private final List<ServerMessage> heard = new ArrayList<>();
	private final AtomicBoolean active = new AtomicBoolean(true);
	private final MessageDispatch dispatch = new MessageDispatch();

	MessageDispatchTest() {
		dispatch.subscribe("test", active::get, heard::add);
	}

	/** One message as Fabric runs it: ALLOW_GAME, then GAME (let through) or GAME_CANCELED. */
	private void message(String text, boolean overlay) {
		assertTrue(dispatch.allow(text, overlay), "never cancels");
		dispatch.exit();
	}

	@Test
	void aMessageIsDeliveredOnce() {
		message("You are playing on profile: Raspberry", false);
		message("+5 SkyBlock XP", true);
		assertEquals(List.of(new ServerMessage("You are playing on profile: Raspberry", false), new ServerMessage("+5 SkyBlock XP", true)), heard);
	}

	@Test
	void aLineAModPostsWhileHandlingAnotherIsNotAServerLine() {
		// A mod's ALLOW_GAME listener hides "RARE DROP!" and shows its own action-bar line instead (Skyblocker
		// chat rules do this): the nested line runs its own ALLOW_GAME and GAME before the outer GAME_CANCELED.
		assertTrue(dispatch.allow("RARE DROP! Enchanted Book", false));
		assertTrue(dispatch.allow("Rule: a drop!", true));
		dispatch.exit();
		dispatch.exit();
		message("next", false);
		assertEquals(List.of(new ServerMessage("RARE DROP! Enchanted Book", false), new ServerMessage("next", false)), heard);
	}

	@Test
	void aStuckDepthIsResetEachTick() {
		// A mod's listener threw mid-message, so neither GAME nor GAME_CANCELED came.
		dispatch.allow("first", false);
		dispatch.allow("lost", false);
		dispatch.tick();
		message("after the tick", false);
		assertEquals(List.of(new ServerMessage("first", false), new ServerMessage("after the tick", false)), heard);
	}

	@Test
	void moreExitsThanEntriesDoNotGoBelowZero() {
		dispatch.exit();
		dispatch.exit();
		message("line", false);
		assertEquals(1, heard.size());
	}

	@Test
	void theTextIsDeliveredWithoutFormatting() {
		message("§6§lSKYBLOCK LEVEL UP §3Level 325", false);
		assertEquals("SKYBLOCK LEVEL UP Level 325", heard.get(0).getText());
	}

	@Test
	void playerChatNeverReachesTheParsers() {
		message("[326] ☀ [MVP+] X: SKYBLOCK LEVEL UP Level 325 ➡ [326]", false);
		message("Guild > [MVP+] X [Officer]: +1,234 Mining (12,345/50,000)", false);
		assertEquals(List.of(), heard);
	}

	@Test
	void anActionBarLineIsNeverPlayerChat() {
		// Players cannot write to the action bar, so its lines are always the server's.
		message("[326] X: 100/100❤", true);
		assertEquals(List.of(new ServerMessage("[326] X: 100/100❤", true)), heard);
	}

	@Test
	void theModsOwnLinesAreNotServerMessages() {
		// ChatUtils shows them through the same client path as server lines, so they pass these events too.
		AtomicBoolean ours = new AtomicBoolean(true);
		MessageDispatch own = new MessageDispatch(ours::get);
		List<ServerMessage> got = new ArrayList<>();
		own.subscribe("test", () -> true, got::add);
		assertTrue(own.allow("[KSU] Container dump armed for 60 minutes", false));
		own.exit();
		ours.set(false);
		own.allow("Profile ID: 1234", false);
		own.exit();
		assertEquals(List.of(new ServerMessage("Profile ID: 1234", false)), got);
	}

	@Test
	void nothingIsDoneWhileEveryConsumerIsOff() {
		active.set(false);
		for (int i = 0; i < 600; i++) {
			message("line " + i, false);
		}
		assertEquals(List.of(), heard);
		assertEquals(0, dispatch.deliveredCount());
	}

	@Test
	void aFailingSubscriberDoesNotStopTheOthersOrTheMessage() {
		List<ServerMessage> second = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		MessageDispatch logged = new MessageDispatch(() -> false, (owner, e) -> failures.add(owner));
		logged.subscribe("broken", () -> true, message -> {
			throw new IllegalStateException("boom");
		});
		logged.subscribe("second", () -> true, second::add);
		for (int i = 0; i < 100; i++) {
			assertTrue(logged.allow("line", false));
			logged.exit();
		}
		assertEquals(100, second.size());
		assertEquals(List.of("broken"), failures, "a failing subscriber is reported once, not per line");
	}

	@Test
	void eachSubscriberOnlyHearsWhileItIsActive() {
		AtomicBoolean otherOn = new AtomicBoolean(false);
		List<ServerMessage> other = new ArrayList<>();
		dispatch.subscribe("other", otherOn::get, other::add);
		message("one", false);
		otherOn.set(true);
		message("two", false);
		assertEquals(2, heard.size());
		assertEquals(List.of(new ServerMessage("two", false)), other);
	}
}
