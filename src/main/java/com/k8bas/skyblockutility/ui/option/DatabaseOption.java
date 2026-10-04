package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;

/** The "Add from database" button of a rule list: it opens the picker over {@code database} (REQ-UI-11). */
public record DatabaseOption(String id, OptionText text, String buttonLabel, RuleDatabase database) implements Option {
	public DatabaseOption {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(buttonLabel, "buttonLabel");
		Objects.requireNonNull(database, "database");
	}

	@Override
	public String storageKey() {
		return "database:" + id;
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
