package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.InfoOption;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.TextOption;
import com.k8bas.skyblockutility.ui.option.Toggle;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Until T2.5b replaces the Cloth screen: turns the option declarations (T2.1) into Cloth entries, so
 * the texts and defaults exist once.
 */
public final class ClothOptions {
	private ClothOptions() {
	}

	/**
	 * Cloth calls every entry's save consumer on Save, edited or not; this one writes only a value that
	 * differs from the one the entry was built with, so a setting absent until picked (the accent, R28)
	 * stays absent (REQ-UI-16).
	 */
	static <T> Consumer<T> ifChanged(Binding<T> binding) {
		T shown = binding.get();
		return value -> {
			if (!Objects.equals(value, shown)) {
				binding.set(value);
			}
		};
	}

	public static void addCards(ConfigCategory category, ConfigEntryBuilder entryBuilder, List<Card> cards) {
		for (Card card : cards) {
			for (Option option : card.all()) {
				category.addEntry(entry(entryBuilder, option));
			}
		}
	}

	private static AbstractConfigListEntry<?> entry(ConfigEntryBuilder entryBuilder, Option option) {
		Component title = Component.literal(option.text().title());
		Component[] tooltip = tooltip(option.text());
		return switch (option) {
			case Toggle toggle -> entryBuilder.startBooleanToggle(title, toggle.binding().get())
					.setDefaultValue(toggle.defaultValue())
					.setTooltip(tooltip)
					.setSaveConsumer(ifChanged(toggle.binding()))
					.build();
			case IntSlider slider -> entryBuilder.startIntSlider(title, slider.binding().get(), slider.min(), slider.max())
					.setDefaultValue(slider.defaultValue())
					.setTextGetter(value -> Component.literal(slider.format(value)))
					.setTooltip(tooltip)
					.setSaveConsumer(ifChanged(slider.binding()))
					.build();
			case Choice<?> choice -> choiceEntry(entryBuilder, title, tooltip, choice);
			case ColorOption colour -> entryBuilder.startColorField(title, colour.binding().get())
					.setDefaultValue(colour.defaultValue())
					.setTooltip(tooltip)
					.setSaveConsumer(ifChanged(colour.binding()))
					.build();
			// Only rule cards have these, and the Cloth screen builds its rules itself until T2.5b.
			case TextOption text -> throw new IllegalArgumentException("not a Cloth setting: " + text.id());
			case ActionOption action -> throw new IllegalArgumentException("not a Cloth setting: " + action.id());
			case InfoOption info -> throw new IllegalArgumentException("not a Cloth setting: " + info.id());
			case Keybind keybind -> entryBuilder.fillKeybindingField(title, keyMapping(keybind.keyMappingName()))
					.setTooltip(tooltip)
					.build();
		};
	}

	@SuppressWarnings("unchecked")
	private static <E> AbstractConfigListEntry<?> choiceEntry(ConfigEntryBuilder entryBuilder, Component title, Component[] tooltip, Choice<E> choice) {
		E[] values = (E[]) choice.values().toArray();
		return entryBuilder.startSelector(title, values, choice.binding().get())
				.setDefaultValue(choice.defaultValue())
				.setNameProvider(value -> Component.literal(choice.label().apply(value)))
				.setTooltip(tooltip)
				.setSaveConsumer(ifChanged(choice.binding()))
				.build();
	}

	/** The full text (REQ-UI-07); the description, written for a card, only where there is no tooltip. */
	private static Component[] tooltip(OptionText text) {
		String full = text.tooltip().isBlank() ? text.description() : text.tooltip();
		List<Component> lines = new ArrayList<>();
		for (String line : full.split("\n")) {
			lines.add(Component.literal(line));
		}
		return lines.toArray(Component[]::new);
	}

	private static KeyMapping keyMapping(String name) {
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (mapping.getName().equals(name)) {
				return mapping;
			}
		}
		throw new IllegalStateException("no key mapping " + name);
	}
}
