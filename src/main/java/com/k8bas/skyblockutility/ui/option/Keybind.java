package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A key the player can bind (REQ-UI-14). The game stores it in options.txt under its key mapping's
 * name; every keybind of this mod is unbound by default (REQ-UI-10).
 */
public record Keybind(String id, String keyMappingName, OptionText text, Supplier<OptionStatus> statusSource) implements Option {
	/** The game's name for "no key". */
	public static final String UNBOUND = "key.keyboard.unknown";

	public Keybind {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(keyMappingName, "keyMappingName");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(statusSource, "statusSource");
	}

	public static Keybind of(String id, String keyMappingName, OptionText text) {
		return new Keybind(id, keyMappingName, text, () -> OptionStatus.OK);
	}

	@Override
	public String storageKey() {
		return "options.txt:" + keyMappingName;
	}

	@Override
	public boolean amber() {
		return false;
	}

	@Override
	public Object defaultValue() {
		return UNBOUND;
	}

	@Override
	public OptionStatus status() {
		return statusSource.get();
	}
}
