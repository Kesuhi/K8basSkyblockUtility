package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Animated;
import com.k8bas.skyblockutility.ui.render.UiTime;

/**
 * How hovered each row of a list looks (REQ-UI-45, T3.35): the hovered row eases in over {@link Animated#DEFAULT_DURATION_MS}
 * as a card does (REQ-UI-17), the row left eases out meanwhile, and a row the pointer comes back to goes on from where it
 * is. Two values serve a list of thousands of rows: a third row hovered within the fade cuts the oldest fade short.
 * Nothing is allocated after it is made.
 */
public final class RowHover {
	private int current = -1;
	private int previous = -1;
	private Animated currentAmount;
	private Animated previousAmount;

	public RowHover(UiTime time) {
		currentAmount = new Animated(0F, Animated.DEFAULT_DURATION_MS, time);
		previousAmount = new Animated(0F, Animated.DEFAULT_DURATION_MS, time);
	}

	/** The row under the pointer this frame, or -1 for none. */
	public void hover(int row) {
		if (row != current) {
			Animated leaving = currentAmount;
			if (row >= 0 && row == previous) {
				// Back on the row being left: it goes on from where it is.
				currentAmount = previousAmount;
			} else {
				currentAmount = previousAmount;
				currentAmount.snapTo(0F);
			}
			previousAmount = leaving;
			previous = current;
			current = row;
		}
		currentAmount.animateTo(current >= 0 ? 1F : 0F);
		previousAmount.animateTo(0F);
	}

	/** No row looks hovered, at once: the rows were rebuilt, so an index may be another row now. */
	public void reset() {
		current = -1;
		previous = -1;
		currentAmount.snapTo(0F);
		previousAmount.snapTo(0F);
	}

	/** How hovered row {@code row} looks now, 0 to 1. */
	public float amount(int row) {
		if (row < 0) {
			return 0F;
		}
		if (row == current) {
			return currentAmount.value();
		}
		if (row == previous) {
			return previousAmount.value();
		}
		return 0F;
	}
}
