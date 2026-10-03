package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.Toggle;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Until T2.5b replaces the Cloth screen: turns the option declarations (T2.1) into Cloth entries, so
 * the texts and defaults exist once.
 */
public final class ClothOptions {
	private ClothOptions() {
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
					.setSaveConsumer(toggle.binding()::set)
					.build();
			case IntSlider slider -> entryBuilder.startIntSlider(title, slider.binding().get(), slider.min(), slider.max())
					.setDefaultValue(slider.defaultValue())
					.setTextGetter(value -> Component.literal(slider.format(value)))
					.setTooltip(tooltip)
					.setSaveConsumer(slider.binding()::set)
					.build();
			case Choice<?> choice -> choiceEntry(entryBuilder, title, tooltip, choice);
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
				.setSaveConsumer(choice.binding()::set)
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
