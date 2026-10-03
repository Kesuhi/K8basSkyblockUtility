package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A colour, picked with a swatch and the colour picker (REQ-UI-13): 0xRRGGBB, or 0xAARRGGBB when
 * {@code storesAlpha}.
 */
public record ColorOption(String id, String storageKey, OptionText text, Integer defaultValue, boolean storesAlpha, Binding<Integer> binding,
		boolean amber, Supplier<OptionStatus> statusSource) implements Option {
	public ColorOption {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(storageKey, "storageKey");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(defaultValue, "defaultValue");
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(statusSource, "statusSource");
	}

	public static ColorOption of(String id, String storageKey, OptionText text, int defaultValue, boolean storesAlpha, Binding<Integer> binding) {
		return new ColorOption(id, storageKey, text, defaultValue, storesAlpha, binding, false, () -> OptionStatus.OK);
	}

	@Override
	public OptionStatus status() {
		return statusSource.get();
	}
}
