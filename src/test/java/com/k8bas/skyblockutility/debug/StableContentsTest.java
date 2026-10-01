package com.k8bas.skyblockutility.debug;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StableContentsTest {
	private static final long EMPTY = 0;
	private final StableContents stable = new StableContents();

	/** Feeds (stateId, fingerprint) pairs and returns the tick numbers that captured. */
	private List<Integer> run(long[][] ticks) {
		List<Integer> captured = new ArrayList<>();
		for (int i = 0; i < ticks.length; i++) {
			if (stable.onTick((int) ticks[i][0], ticks[i][1], EMPTY)) {
				captured.add(i);
			}
		}
		return captured;
	}

	@Test
	void capturesAfterTheContentAndTwoQuietTicks() {
		// open (empty) -> content arrives at tick 2 -> unchanged at ticks 3 and 4
		assertEquals(List.of(4), run(new long[][]{{0, EMPTY}, {0, EMPTY}, {3, 11}, {3, 11}, {3, 11}, {3, 11}, {3, 11}}));
	}

	@Test
	void anEmptyMenuWithoutServerStateIsNeverCaptured() {
		assertEquals(List.of(), run(new long[][]{{0, EMPTY}, {0, EMPTY}, {0, EMPTY}, {0, EMPTY}}));
	}

	@Test
	void slotUpdatesRestartTheQuietPeriod() {
		// content at 0, a late slot update at 2 (before it was stable), then quiet
		assertEquals(List.of(4), run(new long[][]{{3, 11}, {3, 11}, {4, 12}, {4, 12}, {4, 12}}));
	}

	@Test
	void eachPageIsCapturedOnce() {
		assertEquals(List.of(2, 5), run(new long[][]{{3, 11}, {3, 11}, {3, 11}, {5, 22}, {5, 22}, {5, 22}, {5, 22}}));
	}

	@Test
	void resetStartsOverForANewScreen() {
		run(new long[][]{{3, 11}, {3, 11}, {3, 11}});
		stable.reset();
		assertEquals(List.of(2), run(new long[][]{{3, 11}, {3, 11}, {3, 11}}));
	}
}
