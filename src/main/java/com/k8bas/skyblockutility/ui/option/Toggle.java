package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;
import java.util.function.Supplier;

/** An on/off switch; a feature card's own toggle is one (REQ-UI-05, REQ-XC-TOGGLE-01). */
public record Toggle(String id, String storageKey, OptionText text, Boolean defaultValue, Binding<Boolean> binding, boolean amber,
		Supplier<OptionStatus> statusSource) implements Option {
	public Toggle {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(storageKey, "storageKey");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(defaultValue, "defaultValue");
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(statusSource, "statusSource");
	}

	public static Toggle of(String id, String storageKey, OptionText text, boolean defaultValue, Binding<Boolean> binding) {
		return new Toggle(id, storageKey, text, defaultValue, binding, false, () -> OptionStatus.OK);
	}

	public Toggle asAmber() {
		return new Toggle(id, storageKey, text, defaultValue, binding, true, statusSource);
	}

	public Toggle withStatus(Supplier<OptionStatus> source) {
		return new Toggle(id, storageKey, text, defaultValue, binding, amber, source);
	}

	@Override
	public OptionStatus status() {
		return statusSource.get();
	}
}
