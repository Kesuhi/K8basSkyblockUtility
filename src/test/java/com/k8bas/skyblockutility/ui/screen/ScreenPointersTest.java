package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.widget.Overlay;
import com.k8bas.skyblockutility.ui.widget.OverlayHost;
import com.k8bas.skyblockutility.ui.widget.PointerShape;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.VirtualList;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UI-44 [A] (REQ-UI-43, T3.33): the pointer shapes of the settings screen's own widgets. */
class ScreenPointersTest {
	@Test
	void aToggleCardShowsTheHandAndOtherCardsTheDefault() {
		List<Option> options = OptionCatalog.build(new GeneralConfig(), new MobHighlighterConfig(), new NpcSearchConfig(), enabled -> { }, enabled -> { })
				.stream().flatMap(card -> card.options().stream()).toList();
		Toggle toggle = options.stream().filter(Toggle.class::isInstance).map(Toggle.class::cast).findFirst().orElseThrow();
		ToggleSwitch toggleSwitch = new ToggleSwitch(toggle.binding(), () -> { }, () -> 0L);
		OptionCard toggleCard = new OptionCard(toggle, toggleSwitch);
		toggleCard.setBounds(0, 0, 300, 40);
		assertEquals(PointerShape.HAND, toggleCard.cursorAt(50, 20), "a click anywhere on it flips its switch");
		Option slider = options.stream().filter(IntSlider.class::isInstance).findFirst().orElseThrow();
		OptionCard card = new OptionCard(slider, null);
		card.setBounds(0, 0, 300, 40);
		assertEquals(PointerShape.DEFAULT, card.cursorAt(50, 20), "a card a click does nothing on");
	}

	@Test
	void theSearchBoxsClearButtonShowsTheHandOnlyWhileItShows() {
		boolean[] text = {false};
		ClearButton clear = new ClearButton(() -> text[0], () -> { });
		clear.setBounds(0, 0, ClearButton.SIZE, ClearButton.SIZE);
		assertFalse(clear.contains(6, 6), "hidden with no text: the field under it decides");
		text[0] = true;
		assertTrue(clear.contains(6, 6));
		assertEquals(PointerShape.HAND, clear.cursorAt(6, 6));
	}

	@Test
	void thePickersFoldersAndAddButtonsShowTheHand() {
		List<RuleDatabase.Entry> entries = List.of(
				new RuleDatabase.Entry("zealot", "Zealot", "The End", null, "Zealot"),
				new RuleDatabase.Entry("watcher", "Watcher", "The End", null, "Watcher", RuleDatabase.NEVER_HIGHLIGHTED));
		DatabasePickerOverlay picker = new DatabasePickerOverlay(new FakeDatabase(entries), new FakeHost(), () -> { }, () -> { });
		picker.layout(640, 480);
		VirtualList list = picker.list();
		int rowHeight = DatabasePickerOverlay.ROW_HEIGHT;
		assertEquals(1, picker.rows().size(), "a closed folder: " + picker.rows());
		double rowX = list.x() + 20;
		double folderY = list.y() + rowHeight / 2.0;
		assertEquals(PointerShape.HAND, list.cursorAt(rowX, folderY), "a folder opens or closes on a click");
		assertTrue(list.press(rowX, folderY, 0));
		list.release(rowX, folderY);
		picker.layout(640, 480);
		assertEquals(3, picker.rows().size(), "the folder opened: " + picker.rows());
		int checked = 0;
		for (int index = 1; index < picker.rows().size(); index++) {
			RuleDatabase.Entry entry = picker.rows().get(index).entry();
			int rowWidth = list.width();
			int[] add = DatabasePickerOverlay.addButton(rowWidth, rowHeight);
			double top = list.y() + index * rowHeight;
			PointerShape onAdd = list.cursorAt(list.x() + add[0] + add[2] / 2.0, top + add[1] + add[3] / 2.0);
			assertEquals(entry.addable() ? PointerShape.HAND : PointerShape.DEFAULT, onAdd, entry.name() + "'s Add button");
			assertEquals(PointerShape.DEFAULT, list.cursorAt(rowX, top + rowHeight / 2.0), entry.name() + "'s name: a click there does nothing");
			checked++;
		}
		assertEquals(2, checked);
	}

	private record FakeDatabase(List<RuleDatabase.Entry> entries) implements RuleDatabase {
		@Override
		public String title() {
			return "Mobs";
		}

		@Override
		public State state() {
			return State.READY;
		}

		@Override
		public Set<String> used() {
			return Set.of();
		}

		@Override
		public void add(RuleDatabase.Entry entry) {
		}
	}

	private static final class FakeHost implements OverlayHost {
		@Override
		public void open(Overlay opened) {
		}

		@Override
		public void close(Overlay closed) {
		}
	}
}
