package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A whole-number slider (REQ-UI-06).
 *
 * @param unit      shown after the value, e.g. "blocks"
 * @param zeroLabel shown instead of 0 when 0 has a meaning of its own (e.g. "Unlimited"), empty if not
 */
public record IntSlider(String id, String storageKey, OptionText text, Integer defaultValue, int min, int max, int step, String unit,
		String zeroLabel, Binding<Integer> binding, boolean amber, Supplier<OptionStatus> statusSource) implements Option {
	public IntSlider {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(storageKey, "storageKey");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(defaultValue, "defaultValue");
		Objects.requireNonNull(unit, "unit");
		Objects.requireNonNull(zeroLabel, "zeroLabel");
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(statusSource, "statusSource");
		if (min > max || defaultValue < min || defaultValue > max || step <= 0) {
			throw new IllegalArgumentException(id + ": default " + defaultValue + " must lie in " + min + ".." + max + " with a positive step");
		}
	}

	public static IntSlider of(String id, String storageKey, OptionText text, int defaultValue, int min, int max, int step, String unit,
			String zeroLabel, Binding<Integer> binding) {
		return new IntSlider(id, storageKey, text, defaultValue, min, max, step, unit, zeroLabel, binding, false, () -> OptionStatus.OK);
	}

	/** How a value reads on the slider. */
	public String format(int value) {
		if (value == 0 && !zeroLabel.isEmpty()) {
			return zeroLabel;
		}
		return unit.isEmpty() ? Integer.toString(value) : value + " " + unit;
	}

	@Override
	public OptionStatus status() {
		return statusSource.get();
	}
}
