package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;
import java.util.function.Supplier;

/** A value shown but not edited, e.g. a fixed NPC's coordinates (REQ-UI-11). */
public record InfoOption(String id, OptionText text, Supplier<String> value) implements Option {
	public InfoOption {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(value, "value");
	}

	@Override
	public String storageKey() {
		return "info:" + id;
	}

	@Override
	public boolean amber() {
		return false;
	}

	@Override
	public OptionStatus status() {
		return OptionStatus.OK;
	}

	@Override
	public Object defaultValue() {
		return "";
	}
}
