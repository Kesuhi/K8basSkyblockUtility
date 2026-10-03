package com.k8bas.skyblockutility.settings;

import com.google.gson.Gson;
import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.Toggle;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** REQ-UI-16, R28 (T2.4d): the old Cloth screen's Save writes only what was changed. */
class ClothOptionsTest {
	/** Cloth hands every entry's value back on Save; unchanged ones must leave absent-until-picked settings absent. */
	@Test
	void savingUnchangedEntriesWritesNothing() {
		GeneralConfig config = new GeneralConfig();
		String before = new Gson().toJson(config);
		for (var card : GeneralOptions.cards(config)) {
			for (Option option : card.all()) {
				saveAsShown(option);
			}
		}
		assertEquals(before, new Gson().toJson(config), "no key written for an untouched entry");
		assertFalse(new Gson().toJson(config).contains("accentColor"));
	}

	@Test
	void aChangedEntryIsWritten() {
		GeneralConfig config = new GeneralConfig();
		ColorOption accent = (ColorOption) GeneralOptions.cards(config).get(0).options().get(0);
		ClothOptions.ifChanged(accent.binding()).accept(0xFF5252);
		assertEquals(0xFF5252, config.accentColor);
	}

	@SuppressWarnings("unchecked")
	private static void saveAsShown(Option option) {
		switch (option) {
			case Toggle toggle -> ClothOptions.ifChanged(toggle.binding()).accept(toggle.binding().get());
			case IntSlider slider -> ClothOptions.ifChanged(slider.binding()).accept(slider.binding().get());
			case ColorOption colour -> ClothOptions.ifChanged(colour.binding()).accept(colour.binding().get());
			case Choice<?> choice -> {
				Choice<Object> any = (Choice<Object>) choice;
				Consumer<Object> save = ClothOptions.ifChanged(any.binding());
				save.accept(any.binding().get());
			}
			case Keybind keybind -> { }
		}
	}
}
