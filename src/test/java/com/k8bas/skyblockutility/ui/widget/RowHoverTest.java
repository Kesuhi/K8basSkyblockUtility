package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiTime;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UI-46 [A] (REQ-UI-45, T3.35): a list row's hover highlight eases in and out as a card's does. */
class RowHoverTest {
	private final long[] now = {1_000};
	private final boolean[] motion = {true};
	private UiTime previous;

	@BeforeEach
	void testClock() {
		previous = UiClock.enter(UiTime.of(() -> now[0], () -> motion[0]));
	}

	@AfterEach
	void restore() {
		UiClock.leave(previous);
	}

	@Test
	void aRowsHoverFollowsTheCardsEasing() {
		RowHover rows = new RowHover(UiClock.SOURCE);
		Widget card = new Widget() {
			@Override
			public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			}
		};
		rows.hover(-1);
		card.hoverAmount(false);
		rows.hover(3);
		card.hoverAmount(true);
		for (long step : new long[] {0, 10, 30, 75, 120, 149, 150, 300}) {
			now[0] = 1_000 + step;
			rows.hover(3);
			assertEquals(card.hoverAmount(true), rows.amount(3), 1e-6, "in, at " + step + " ms");
		}
		long out = now[0];
		rows.hover(-1);
		card.hoverAmount(false);
		for (long step : new long[] {0, 20, 75, 150, 200}) {
			now[0] = out + step;
			rows.hover(-1);
			assertEquals(card.hoverAmount(false), rows.amount(3), 1e-6, "out, at " + step + " ms");
		}
		assertEquals(0F, rows.amount(3));
	}

	@Test
	void theRowLeftFadesOutWhileTheNextFadesIn() {
		RowHover rows = new RowHover(UiClock.SOURCE);
		rows.hover(1);
		now[0] += 150;
		rows.hover(1);
		assertEquals(1F, rows.amount(1), 1e-6);
		rows.hover(2);
		now[0] += 75;
		rows.hover(2);
		float leaving = rows.amount(1);
		float coming = rows.amount(2);
		assertTrue(leaving > 0F && leaving < 1F && coming > 0F && coming < 1F, "both on their way: " + leaving + ", " + coming);
		assertEquals(1F - coming, leaving, 1e-6, "the same easing both ways");
		assertEquals(0F, rows.amount(0), "a row never hovered");
		// Back on the row being left: it eases back up from where it is, not from 0.
		rows.hover(1);
		assertEquals(leaving, rows.amount(1), 1e-6);
		now[0] += 150;
		rows.hover(1);
		assertEquals(1F, rows.amount(1), 1e-6);
		assertEquals(0F, rows.amount(2), 1e-6);
	}

	@Test
	void withAnimationsOffItIsThereAtOnce() {
		motion[0] = false;
		RowHover rows = new RowHover(UiClock.SOURCE);
		rows.hover(4);
		assertEquals(1F, rows.amount(4));
		rows.hover(5);
		assertEquals(0F, rows.amount(4));
		assertEquals(1F, rows.amount(5));
	}
}
