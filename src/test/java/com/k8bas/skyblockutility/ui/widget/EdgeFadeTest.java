package com.k8bas.skyblockutility.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-60 [A] (REQ-UI-56, T3.49): a scroll edge's fade is as strong as there is to scroll past it, full from 8 GUI px on,
 * none with nothing beyond; its alpha rises monotonically toward the edge.
 */
class EdgeFadeTest {
	@Test
	void itsStrengthFollowsWhatLiesBeyond() {
		assertEquals(0F, EdgeFade.strength(0F), "nothing beyond: no fade");
		assertEquals(0.5F, EdgeFade.strength(4F), 1e-6, "4 GUI px beyond: half");
		assertEquals(1F, EdgeFade.strength(8F), "8: full");
		assertEquals(1F, EdgeFade.strength(500F), "more: still full");
		assertEquals(0F, EdgeFade.strength(-3F), "past the end: none");
	}

	/** A region at its top: no top fade, a full bottom fade with 8 GUI px or more below; at its end the other way round. */
	@Test
	void theTopAndTheBottomFollowTheScroll() {
		int max = 200;
		assertEquals(0F, EdgeFade.strength(0F), "at the top: no top fade");
		assertEquals(1F, EdgeFade.strength(max - 0F), "and a full bottom fade");
		assertEquals(0.5F, EdgeFade.strength(4F), 1e-6, "4 down: the top fade half");
		assertEquals(0F, EdgeFade.strength(max - (float) max), "at the end: no bottom fade");
	}

	@Test
	void itsAlphaRisesTowardTheEdge() {
		for (int scale = 1; scale <= 4; scale++) {
			int rows = EdgeFade.DEPTH * scale;
			int previous = 256;
			for (int row = 0; row < rows; row++) {
				int alpha = EdgeFade.rowAlpha(row, rows, 1F);
				assertTrue(alpha <= previous, "scale " + scale + ", row " + row + ": " + alpha + " after " + previous);
				previous = alpha;
			}
			assertTrue(EdgeFade.rowAlpha(0, rows, 1F) > 200, "strong at the edge");
			assertTrue(EdgeFade.rowAlpha(rows - 1, rows, 1F) < 20, "faint at its inner end");
			assertEquals(0, EdgeFade.rowAlpha(rows, rows, 1F), "nothing past its depth");
			assertEquals(Math.round(EdgeFade.rowAlpha(0, rows, 1F) / 2F), EdgeFade.rowAlpha(0, rows, 0.5F), 1, "half strength, half the alpha");
		}
	}
}
