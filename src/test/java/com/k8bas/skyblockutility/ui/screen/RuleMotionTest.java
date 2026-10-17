package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.UiTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-56 (REQ-UI-52, T3.45), on a test clock: a clicked rule card opens and closes over 150 ms; a click during the motion
 * reverses it from where it is; a change without a click (a search opening or closing it, EC-UI-32) is there at once, and
 * so is every change with Animations OFF.
 */
class RuleMotionTest {
	private final long[] now = {5_000};
	private final boolean[] motion = {true};
	private final RuleMotion rules = new RuleMotion(UiTime.of(() -> now[0], () -> motion[0]));

	@Test
	void aClickOpensItOver150MsAndClosesItTheSameWay() {
		assertEquals(0F, rules.reveal("a", false), "closed");
		rules.toggled("a", true);
		assertEquals(0F, rules.reveal("a", true), "0 ms");
		now[0] += 75;
		float half = rules.reveal("a", true);
		assertEquals(0.875F, half, 1e-4, "75 ms: ease-out cubic halfway, 1 - 0.5³");
		now[0] += 75;
		assertEquals(1F, rules.reveal("a", true), "150 ms");
		now[0] += 500;
		assertEquals(1F, rules.reveal("a", true), "and stays");
		rules.toggled("a", false);
		assertEquals(1F, rules.reveal("a", false), "closing, 0 ms");
		now[0] += 75;
		float closing = rules.reveal("a", false);
		assertTrue(closing > 0F && closing < 1F, "closing, 75 ms: " + closing);
		now[0] += 75;
		assertEquals(0F, rules.reveal("a", false), "closing, 150 ms");
	}

	/** A click at 75 ms reverses the motion from the height it has then. */
	@Test
	void aClickDuringTheMotionReversesItFromWhereItIs() {
		rules.reveal("a", false);
		rules.toggled("a", true);
		now[0] += 75;
		float at = rules.reveal("a", true);
		rules.toggled("a", false);
		assertEquals(at, rules.reveal("a", false), 1e-6, "it turns back where it was");
		now[0] += 10;
		float back = rules.reveal("a", false);
		assertTrue(back < at && back > 0F, "and closes from there: " + back);
		now[0] += 150;
		assertEquals(0F, rules.reveal("a", false));
	}

	/** EC-UI-32: a search opens a matching rule, and its end closes it again, at once; each rule has its own motion. */
	@Test
	void aChangeWithoutAClickIsThereAtOnce() {
		assertEquals(0F, rules.reveal("a", false));
		assertEquals(1F, rules.reveal("a", true), "a search opened it");
		assertEquals(0F, rules.reveal("a", false), "the search ended");
		rules.toggled("b", true);
		now[0] += 75;
		assertEquals(1F, rules.reveal("a", true), "another rule's state is its own");
		float b = rules.reveal("b", true);
		assertTrue(b > 0F && b < 1F, "while b still opens: " + b);
	}

	/** A click right after a change no frame has shown yet (a search ended, then a click) still eases from the state before it. */
	@Test
	void aClickAfterAnUnseenChangeEasesFromThatState() {
		assertEquals(1F, rules.reveal("a", true), "open");
		// The search that had it open ends, and before a frame shows it closed, a click opens it.
		rules.toggled("a", true);
		assertEquals(0F, rules.reveal("a", true), "0 ms: from closed, not open at once");
		now[0] += 150;
		assertEquals(1F, rules.reveal("a", true));
	}

	/** An overlay opening over the page ends every card's motion where it was going. */
	@Test
	void settleEndsEveryMotion() {
		rules.reveal("a", false);
		rules.reveal("b", true);
		rules.toggled("a", true);
		rules.toggled("b", false);
		now[0] += 30;
		rules.settle();
		assertEquals(1F, rules.reveal("a", true));
		assertEquals(0F, rules.reveal("b", false));
	}

	@Test
	void withAnimationsOffEveryChangeIsThereAtOnce() {
		motion[0] = false;
		rules.reveal("a", false);
		rules.toggled("a", true);
		assertEquals(1F, rules.reveal("a", true));
		rules.toggled("a", false);
		assertEquals(0F, rules.reveal("a", false));
	}
}
