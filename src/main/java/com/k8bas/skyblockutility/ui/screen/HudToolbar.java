package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.Animated;
import com.k8bas.skyblockutility.ui.render.Easing;
import com.k8bas.skyblockutility.ui.render.UiTime;

/**
 * The HUD editor toolbar's timing (REQ-HUD-08), apart from its drawing so tests can step it on a clock: the hint panel shown
 * for 4 s from the first frame, or until the first drag, nudge or scale, then fading out over 300 ms and back in while the
 * pointer rests on the card; the card fading to 30 % while an element is dragged over it; Reset All's second click. Every
 * fade is instant with Animations OFF (REQ-UI-39).
 */
final class HudToolbar {
	static final long HINT_SHOWN_MS = 4_000;
	static final long HINT_FADE_MS = 300;
	static final long CARD_FADE_MS = 150;
	/** The card's opacity while an element is dragged over it. */
	static final float CARD_DIMMED = 0.3F;
	/** How long Reset All waits for its second click. */
	static final long CONFIRM_MS = 3_000;

	private final UiTime time;
	private final Animated onCard;
	private final Animated card;
	/** When the hint starts to fade: 4 s after the first frame, or the first drag, nudge or scale; MAX_VALUE before the first frame. */
	private long hideAt = Long.MAX_VALUE;
	private boolean shown;
	/** Until when Reset All asks for its second click; MIN_VALUE when it does not. */
	private long confirmUntil = Long.MIN_VALUE;

	HudToolbar(UiTime time) {
		this.time = time;
		this.onCard = new Animated(0F, HINT_FADE_MS, time);
		this.card = new Animated(1F, CARD_FADE_MS, time);
	}

	/** The hint panel's opacity this frame, with the pointer on the card or not. */
	float hintAlpha(boolean pointerOnCard) {
		long now = time.millis();
		if (!shown) {
			shown = true;
			hideAt = Math.min(hideAt, now + HINT_SHOWN_MS);
		}
		onCard.animateTo(pointerOnCard ? 1F : 0F);
		float own;
		if (now < hideAt) {
			own = 1F;
		} else if (!time.motion()) {
			own = 0F;
		} else {
			own = 1F - Easing.outCubic((now - hideAt) / (float) HINT_FADE_MS);
		}
		return Math.max(own, onCard.value());
	}

	/** A drag, nudge or scale: the hint starts to fade now, unless it already has. */
	void acted() {
		hideAt = Math.min(hideAt, time.millis());
	}

	/** The card's opacity this frame, with an element dragged over it or not. */
	float cardAlpha(boolean dragOverCard) {
		card.animateTo(dragOverCard ? CARD_DIMMED : 1F);
		return card.value();
	}

	/** Whether Reset All is asking for its second click now. */
	boolean confirming() {
		if (confirmUntil != Long.MIN_VALUE && time.millis() >= confirmUntil) {
			confirmUntil = Long.MIN_VALUE;
		}
		return confirmUntil != Long.MIN_VALUE;
	}

	/** A click on Reset All: true when it is the second one within 3 s and every element is to be reset; else it starts asking. */
	boolean resetAllClicked() {
		if (confirming()) {
			confirmUntil = Long.MIN_VALUE;
			return true;
		}
		confirmUntil = time.millis() + CONFIRM_MS;
		return false;
	}
}
