package com.k8bas.skyblockutility.ui.option;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** A single-choice dropdown (REQ-UI-06); its entry labels are searchable (REQ-UI-08). */
public record Choice<E>(String id, String storageKey, OptionText text, E defaultValue, List<E> values, Function<E, String> label,
		Binding<E> binding, boolean amber, Supplier<OptionStatus> statusSource) implements Option {
	public Choice {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(storageKey, "storageKey");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(defaultValue, "defaultValue");
		values = List.copyOf(values);
		Objects.requireNonNull(label, "label");
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(statusSource, "statusSource");
		if (!values.contains(defaultValue)) {
			throw new IllegalArgumentException(id + ": the default must be one of the choices");
		}
	}

	public static <E> Choice<E> of(String id, String storageKey, OptionText text, E defaultValue, List<E> values, Function<E, String> label,
			Binding<E> binding) {
		return new Choice<>(id, storageKey, text, defaultValue, values, label, binding, false, () -> OptionStatus.OK);
	}

	@Override
	public List<String> searchLabels() {
		return values.stream().map(label).toList();
	}

	@Override
	public OptionStatus status() {
		return statusSource.get();
	}
}
