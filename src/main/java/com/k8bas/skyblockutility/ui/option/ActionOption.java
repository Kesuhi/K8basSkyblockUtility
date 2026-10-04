package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;

/**
 * A button that does something at once, e.g. a rule's Delete (REQ-UI-11). It stores no value of its
 * own; the screen writes the config after it ran (a discrete commit, REQ-UI-15).
 *
 * @param destructive drawn in the fixed destructive red, which never follows the accent (REQ-UI-17)
 */
public record ActionOption(String id, OptionText text, String buttonLabel, boolean destructive, Runnable action) implements Option {
	public ActionOption {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(buttonLabel, "buttonLabel");
		Objects.requireNonNull(action, "action");
	}

	@Override
	public String storageKey() {
		return "action:" + id;
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
