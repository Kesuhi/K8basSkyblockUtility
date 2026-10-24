package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.module.bestiary.BestiaryChatEvent.Milestone;
import com.k8bas.skyblockutility.module.bestiary.BestiaryChatEvent.TierUp;
import com.k8bas.skyblockutility.module.bestiary.BestiaryChatEvent.Unreadable;
import com.k8bas.skyblockutility.skyblock.CaptureFixtures;
import com.k8bas.skyblockutility.skyblock.ServerMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-BEST-09 [A], the chat part (T5.2a, REQ-BEST-05, REQ-BEST-10, REQ-BEST-17): every BESTIARY tier-up block of the
 * maintainer's 26.2 logs (fixtures/bestiary: 462, where AC-BEST-09 counted 378 before the logs grew) gives its family,
 * from-tier and to-tier with no failure, every milestone (86) its numbers, also in the block without border lines;
 * milestones and tiers parse in Roman and Arabic form.
 */
class BestiaryChatParserTest {
	private static final String BORDER = "▬".repeat(64);

	private static List<BestiaryChatEvent> feed(BestiaryChatParser parser, List<String> lines) {
		List<BestiaryChatEvent> events = new ArrayList<>();
		for (String line : lines) {
			BestiaryChatEvent event = parser.accept(new ServerMessage(line, false));
			if (event != null) {
				events.add(event);
			}
		}
		return events;
	}

	@Test
	void everyLoggedBlockParses() {
		List<String> lines = CaptureFixtures.lines("/fixtures/bestiary/chat-blocks.txt").stream()
				.filter(line -> !line.equals("----"))
				.map(line -> line.equals("<border>") ? BORDER : line)
				.toList();
		List<BestiaryChatEvent> events = feed(new BestiaryChatParser(), lines);
		assertEquals(List.of(), events.stream().filter(Unreadable.class::isInstance).toList(), "no failure");
		List<TierUp> tierUps = events.stream().filter(TierUp.class::isInstance).map(TierUp.class::cast).toList();
		List<Milestone> milestones = events.stream().filter(Milestone.class::isInstance).map(Milestone.class::cast).toList();
		assertEquals(462, tierUps.size());
		assertEquals(86, milestones.size());
		// Each tier-up names a family and goes up; a family found for the first time goes from 0 to I.
		for (TierUp up : tierUps) {
			assertTrue(!up.getName().isBlank() && up.getTo() > up.getFrom() && up.getFrom() >= 0, up.toString());
		}
		assertEquals(new TierUp("Flare", 10, 11), tierUps.get(0));
		assertTrue(tierUps.contains(new TierUp("Littlefoot", 0, 1)), "first tier");
		assertTrue(tierUps.contains(new TierUp("Zombie Grunt", 10, 11)), "the block without border lines");
		assertTrue(tierUps.contains(new TierUp("Torrid", 1, 2)), "a header a stacking mod had marked (x2)");
		assertTrue(tierUps.stream().anyMatch(up -> up.getName().equals("Barbarian Duke X")), "a name ending in a numeral");
		// A milestone reached inside a tier-up block (47), and the claim in a block of its own (39), whose from is the last
		// one claimed: pending milestones claimed together jump several at once ("CCLXVIII ➡ CCLXXII").
		assertTrue(milestones.contains(new Milestone(261, 262, false)), "CCLXI➡CCLXII");
		assertTrue(milestones.contains(new Milestone(268, 272, true)), "a claim of four");
		assertEquals(47, milestones.stream().filter(milestone -> !milestone.getClaimed()).count());
		assertEquals(39, milestones.stream().filter(Milestone::getClaimed).count());
		for (Milestone milestone : milestones) {
			assertTrue(milestone.getTo() > milestone.getFrom(), milestone.toString());
		}
	}

	@Test
	void romanAndArabicForms() {
		List<BestiaryChatEvent> events = feed(new BestiaryChatParser(), List.of(
				BORDER, "", "BESTIARY", "Ghost 14 ➡ 15", "REWARDS", BORDER,
				BORDER, "", "BESTIARY MILESTONE", "261➡262", "", BORDER,
				BORDER, "", "BESTIARY MILESTONE", "CCLXI ➡ CCLXII", BORDER,
				BORDER, "", "BESTIARY", "Ghost XIV➡XV", BORDER,
				// Spacing the logs never show is read too.
				"BESTIARY", "Ghost  XIV  ➡  XV", "BESTIARY MILESTONE", "261  ➡  262"));
		assertEquals(List.of(new TierUp("Ghost", 14, 15), new Milestone(261, 262, true), new Milestone(261, 262, true), new TierUp("Ghost", 14, 15),
				new TierUp("Ghost", 14, 15), new Milestone(261, 262, false)), events);
	}

	@Test
	void aMilestoneInATierUpBlockIsReachedAndOneOnItsOwnIsClaimed() {
		List<BestiaryChatEvent> events = feed(new BestiaryChatParser(), List.of(
				BORDER, "", "BESTIARY", "Ghost XIV ➡ XV", "", "REWARDS", "+1 SkyBlock XP", "", "BESTIARY MILESTONE", "CCLXI➡CCLXII", "", BORDER,
				BORDER, "", "BESTIARY MILESTONE", "CCLXI ➡ CCLXII", "", "REWARD", "+1 Health", "", BORDER));
		assertEquals(List.of(new TierUp("Ghost", 14, 15), new Milestone(261, 262, false), new Milestone(261, 262, true)), events);
	}

	@Test
	void aResetForgetsAHalfReadBlock() {
		// A header right before a world change: the next server line is no value of it.
		BestiaryChatParser parser = new BestiaryChatParser();
		assertEquals(List.of(new TierUp("Ghost", 14, 15)), feed(parser, List.of(BORDER, "", "BESTIARY", "Ghost XIV ➡ XV", "BESTIARY MILESTONE")));
		parser.reset();
		assertEquals(List.of(), feed(parser, List.of("Sending you to the Hub")), "no Unreadable for a line after the world change");
		assertEquals(List.of(new Milestone(261, 262, true)), feed(parser, List.of("BESTIARY MILESTONE", "CCLXI➡CCLXII")), "after a reset no tier-up block is open");
	}

	@Test
	void onlyTheLineAfterAHeaderCounts() {
		BestiaryChatParser parser = new BestiaryChatParser();
		// A line shaped like a tier-up outside a block, and the action bar, are no events.
		assertEquals(List.of(), feed(parser, List.of("Ghost 14 ➡ 15", "CCLXI➡CCLXII", "REWARDS")));
		assertNull(parser.accept(new ServerMessage("BESTIARY", true)));
		assertNull(parser.accept(new ServerMessage("Ghost 14 ➡ 15", false)), "the action bar header armed nothing");
		// A header followed by something else is reported once, and the parser does not stay armed.
		assertEquals(List.of(new Unreadable("BESTIARY", "Ghost leveled up")), feed(parser, List.of("BESTIARY", "Ghost leveled up", "Ghost 14 ➡ 15")));
		// Padding and one blank line between header and value are fine; a second blank line ends the wait.
		assertEquals(List.of(new TierUp("Ghost", 14, 15)), feed(parser, List.of("   BESTIARY  ", "", "     Ghost XIV ➡ XV   ")));
		assertEquals(List.of(new Unreadable("BESTIARY", "")), feed(parser, List.of("BESTIARY", "", "", "Ghost XIV ➡ XV")));
		// Only a whole line is a header: a chat line that ends in one arms nothing (the reader also drops player chat).
		assertEquals(List.of(), feed(parser, List.of("Guild > [VIP] Player1 [MEMBER]: BESTIARY MILESTONE", "249➡250",
				"Player1: BESTIARY", "Ghost XIV ➡ XV")));
	}
}
