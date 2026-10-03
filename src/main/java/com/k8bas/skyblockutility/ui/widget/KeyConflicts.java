package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Keybind;

import java.util.ArrayList;
import java.util.List;

/**
 * Which other key mappings share a binding (EC-UI-11), by vanilla's rule: the same key, not unbound,
 * not the mapping itself, and not both sitting on their own default keys.
 */
public final class KeyConflicts {
	/** A mapping's name, its bound key and its default key, as options.txt names them. */
	public record KeyState(String mapping, String bound, String defaultKey) {
	}

	private KeyConflicts() {
	}

	public static List<String> of(KeyState self, List<KeyState> all) {
		List<String> clashes = new ArrayList<>();
		if (self.bound().equals(Keybind.UNBOUND)) {
			return clashes;
		}
		for (KeyState other : all) {
			if (other.mapping().equals(self.mapping()) || !other.bound().equals(self.bound())) {
				continue;
			}
			boolean bothOnDefaults = self.bound().equals(self.defaultKey()) && other.bound().equals(other.defaultKey());
			if (!bothOnDefaults) {
				clashes.add(other.mapping());
			}
		}
		return clashes;
	}
}
