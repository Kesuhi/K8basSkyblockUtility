package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.UiFade;
import com.k8bas.skyblockutility.ui.render.UiTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-39 [A] (REQ-UI-38, T3.29): an overlay opens with its opacity from 0 to 1 and its scale from 0.96 to 1.00 about its
 * anchor over 120 ms, easing out, and the backdrop's alpha follows the same curve; with Animations OFF it is open at once
 * (AC-UI-40). AC-UI-35's overlay part: stepped every 10 ms at GUI scale 2, its scaled edges lie on the 0.5 GUI px grid, and at
 * least one frame on a half. The kit's fade multiplies the alpha it draws with.
 */
class OverlayTransitionTest {
	private final long[] now = {5_000};
	private final boolean[] motion = {true};
	private final UiTime time = UiTime.of(() -> now[0], () -> motion[0]);

	@Test
	void itFadesAndScalesInOver120Ms() {
		OverlayTransition transition = new OverlayTransition(time);
		assertEquals(0F, transition.opacity(), "0 ms");
		assertEquals(0.96F, transition.scale(), 1e-6, "0 ms");
		now[0] += 60;
		float opacity = transition.opacity();
		float scale = transition.scale();
		assertTrue(opacity > 0.5F && opacity < 1F, "60 ms, easing out: more than half there: " + opacity);
		assertTrue(scale > 0.98F && scale < 1F, "60 ms, easing out: " + scale);
		now[0] += 60;
		assertEquals(1F, transition.opacity(), "120 ms");
		assertEquals(1F, transition.scale(), "120 ms");
		now[0] += 1_000;
		assertEquals(1F, transition.opacity(), "and from then on");
		assertEquals(0x88, transition.backdropAlpha(0x88), "the backdrop at its alpha");
	}

	@Test
	void theBackdropFollowsTheSameCurve() {
		OverlayTransition transition = new OverlayTransition(time);
		assertEquals(0, transition.backdropAlpha(0x88), "0 ms: clear");
		now[0] += 60;
		int alpha = transition.backdropAlpha(0x88);
		assertTrue(alpha > 0x44 && alpha < 0x88, "60 ms, easing out: more than half: " + alpha);
	}

	/** The drawing map takes the box's corners onto the scaled box; none once open. */
	@Test
	void itsPoseMapsTheBoxOntoTheScaledBox() {
		OverlayTransition transition = new OverlayTransition(time);
		for (int t = 0; t < 120; t += 10) {
			now[0] = 5_000 + t;
			float[] to = transition.box(100, 40, 220, 152, 160, 40, 2);
			float[] map = transition.pose(100, 40, 220, 152, 160, 40, 2);
			assertEquals(to[0], map[0] + map[2] * (100 - 100), 1e-4F, t + " ms: left");
			assertEquals(to[1], map[1] + map[3] * (40 - 40), 1e-4F, t + " ms: top");
			assertEquals(to[2], map[0] + map[2] * (220 - 100), 1e-4F, t + " ms: right");
			assertEquals(to[3], map[1] + map[3] * (152 - 40), 1e-4F, t + " ms: bottom");
			assertEquals(40F, to[1], t + " ms: the anchored top edge stays");
		}
		now[0] = 5_120;
		assertEquals(null, transition.pose(100, 40, 220, 152, 160, 40, 2), "open: drawn as it is");
	}

	/** A dropdown list grows out of its field: about its top's middle when it opens down, its bottom's when up. */
	@Test
	void aListScalesAboutTheSideThatMeetsItsField() {
		float[] down = Dropdown.anchor(100, 40, 120, 112, false);
		float[] up = Dropdown.anchor(100, 40, 120, 112, true);
		assertEquals(160F, down[0]);
		assertEquals(40F, down[1], "down: its top");
		assertEquals(160F, up[0]);
		assertEquals(152F, up[1], "up: its bottom");
	}

	@Test
	void withAnimationsOffItIsOpenAtOnce() {
		motion[0] = false;
		OverlayTransition transition = new OverlayTransition(time);
		assertEquals(1F, transition.opacity());
		assertEquals(1F, transition.scale());
		assertEquals(0x88, transition.backdropAlpha(0x88));
	}

	/** The scaled box about its anchor, its edges on the physical-pixel grid at GUI scale 2, some on a half GUI px. */
	@Test
	void itsScaledEdgesLieOnThePhysicalGrid() {
		OverlayTransition transition = new OverlayTransition(time);
		int halves = 0;
		for (int t = 0; t <= 130; t += 10) {
			now[0] = 5_000 + t;
			// A dropdown list from (100, 40) to (220, 152), anchored at its top edge's middle, under its field.
			float[] box = transition.box(100, 40, 220, 152, 160, 40, 2);
			float s = transition.scale();
			for (int i = 0; i < 4; i++) {
				assertEquals(Math.round(box[i] * 2), box[i] * 2, 1e-4F, t + " ms: edge " + i + " at " + box[i]);
				halves += Math.abs(box[i] - Math.round(box[i])) > 0.25F ? 1 : 0;
			}
			float[] exact = {160 + (100 - 160) * s, 40, 160 + (220 - 160) * s, 40 + (152 - 40) * s};
			for (int i = 0; i < 4; i++) {
				assertTrue(Math.abs(box[i] - exact[i]) <= 0.25F + 1e-4F, t + " ms: edge " + i + " " + box[i] + " for " + exact[i]);
			}
		}
		assertTrue(halves > 0, "some frames put an edge on a half GUI px");
		assertEquals(100F, transition.box(100, 40, 220, 152, 160, 40, 2)[0], "settled: the box itself");
	}

	@Test
	void theFadeMultipliesTheAlphaDrawn() {
		assertEquals(0xFF102030, UiFade.apply(0xFF102030), "no fade");
		float previous = UiFade.enter(0.5F);
		try {
			assertEquals(0x80102030, UiFade.apply(0xFF102030), "half");
			assertEquals(0x40102030, UiFade.apply(0x80102030), "of a half-transparent colour");
			float inner = UiFade.enter(0.5F);
			try {
				assertEquals(0x40102030, UiFade.apply(0xFF102030), "nested fades multiply");
			} finally {
				UiFade.leave(inner);
			}
		} finally {
			UiFade.leave(previous);
		}
		assertEquals(0xFF102030, UiFade.apply(0xFF102030), "and the fade before is back");
	}
}
