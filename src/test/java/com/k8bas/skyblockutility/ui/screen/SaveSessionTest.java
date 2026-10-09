package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.widget.Slider;
import com.k8bas.skyblockutility.ui.widget.SliderModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-15, AC-UI-14 [A] (T2.4c): writes counted on the save path. */
class SaveSessionTest {
	private int saves;
	private int rebuilds;
	private final SaveSession session = new SaveSession(() -> rebuilds++, () -> saves++);

	/** AC-UI-14: a drag across 50 values writes nothing while dragging and once on release. */
	@Test
	void aSliderDragWritesOnceOnRelease() {
		long[] value = {0};
		java.util.Set<Long> seen = new java.util.HashSet<>();
		Slider slider = new Slider(SliderModel.ofInt(0, 100, 1), () -> value[0], v -> {
			value[0] = v;
			seen.add(v);
		}, session::commit, Long::toString, () -> { });
		slider.setBounds(0, 0, 200, 10);
		slider.press(0, 5, 0);
		for (int x = 1; x < 200; x++) {
			slider.drag(x, 5);
		}
		assertTrue(seen.size() >= 50, "the value followed the drag live across " + seen.size() + " values");
		assertEquals(0, saves, "nothing written while dragging");
		slider.release(199, 5);
		assertEquals(1, saves, "written once on release");
		assertEquals(0, rebuilds, "a commit does not rebuild the modules");
	}

	/** AC-UI-14: closing writes exactly once and rebuilds each module exactly once, whatever the route. */
	@Test
	void closingWritesAndRebuildsOnce() {
		session.close();
		// Esc closes through onClose, and the game then calls removed(): the second call does nothing.
		session.close();
		assertEquals(1, saves);
		assertEquals(1, rebuilds);
	}

	/** Covered by a screen it opened and shown again, the screen's next close saves and rebuilds once more. */
	@Test
	void aScreenShownAgainSavesAgainOnItsNextClose() {
		session.close();
		session.open();
		session.close();
		session.close();
		assertEquals(2, saves);
		assertEquals(2, rebuilds);
	}

	/** A commit writes at once; the close rebuilds the modules first, then writes. */
	@Test
	void theRebuildRunsBeforeTheWrite() {
		StringBuilder order = new StringBuilder();
		SaveSession ordered = new SaveSession(() -> order.append("rebuild "), () -> order.append("save "));
		ordered.commit();
		ordered.close();
		assertEquals("save rebuild save ", order.toString(), "a commit writes at once; the close rebuilds, then writes");
	}
}
