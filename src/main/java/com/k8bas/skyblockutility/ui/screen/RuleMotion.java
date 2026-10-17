package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.Animated;
import com.k8bas.skyblockutility.ui.render.UiTime;

import java.util.HashMap;
import java.util.Map;

/**
 * How far each rule card is open (REQ-UI-52, T3.45): a click eases it open or closed over {@link #DURATION_MS} on the UI
 * clock, and a click during the motion reverses it from where it is. A card that opens or closes without a click (a search
 * opening the rules it finds, and closing them again when it ends, EC-UI-32) is there at once, as is every change with
 * Animations OFF. The page lays out at the eased height each frame (ConfigLayout).
 */
final class RuleMotion {
	static final long DURATION_MS = 150;

	private final UiTime time;
	/** By rule id; a rule not seen yet starts where its state is. */
	private final Map<String, Animated> reveals = new HashMap<>();

	RuleMotion(UiTime time) {
		this.time = time;
	}

	/**
	 * A click opened ({@code open}) or closed the rule: its card eases there from where it is, which is its state before the
	 * click (reached at once, if that state came without a click and no frame has shown it yet).
	 */
	void toggled(String id, boolean open) {
		reveal(id, !open);
		reveals.get(id).animateTo(open ? 1F : 0F);
	}

	/** Every card at the end of its motion: an overlay opening over the page keeps its anchor where it is (REQ-UI-38). */
	void settle() {
		for (Animated reveal : reveals.values()) {
			reveal.snapTo(reveal.target());
		}
	}

	/** How far the rule's card is open now, 0 to 1, for its state {@code expanded}; a state no click set is reached at once. */
	float reveal(String id, boolean expanded) {
		float end = expanded ? 1F : 0F;
		Animated reveal = reveals.computeIfAbsent(id, unused -> new Animated(end, DURATION_MS, time));
		if (reveal.target() != end) {
			reveal.snapTo(end);
		}
		return reveal.value();
	}
}
