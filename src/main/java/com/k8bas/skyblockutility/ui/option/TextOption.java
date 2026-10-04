package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A line of text, e.g. a rule's label or name pattern (REQ-UI-11). Typing applies at once; {@code invalid}
 * marks the field in the error colour while it holds (an invalid regular expression, REQ-UI-12).
 */
public record TextOption(String id, String storageKey, OptionText text, String defaultValue, int maxLength, String placeholder,
		Binding<String> binding, BooleanSupplier invalid, boolean amber, Supplier<OptionStatus> statusSource) implements Option {
	public TextOption {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(storageKey, "storageKey");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(defaultValue, "defaultValue");
		Objects.requireNonNull(placeholder, "placeholder");
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(invalid, "invalid");
		Objects.requireNonNull(statusSource, "statusSource");
		if (maxLength <= 0) {
			throw new IllegalArgumentException(id + ": maxLength " + maxLength);
		}
	}

	public static TextOption of(String id, String storageKey, OptionText text, String defaultValue, int maxLength, String placeholder,
			Binding<String> binding) {
		return new TextOption(id, storageKey, text, defaultValue, maxLength, placeholder, binding, () -> false, false, () -> OptionStatus.OK);
	}

	public TextOption invalidWhen(BooleanSupplier condition) {
		return new TextOption(id, storageKey, text, defaultValue, maxLength, placeholder, binding, condition, amber, statusSource);
	}

	@Override
	public OptionStatus status() {
		return statusSource.get();
	}
}
