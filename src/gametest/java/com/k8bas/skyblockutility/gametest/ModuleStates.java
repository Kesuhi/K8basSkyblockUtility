package com.k8bas.skyblockutility.gametest;

import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Each module's on/off state, taken before a gametest switches modules and put back after it, so a later gametest
 * finds the defaults: not every feature need be ON by default (D-6). Client thread only.
 */
public final class ModuleStates {
	private final Map<String, Boolean> states = new LinkedHashMap<>();

	private ModuleStates() {
	}

	public static ModuleStates capture() {
		ModuleStates captured = new ModuleStates();
		for (Module module : ModuleManager.modules()) {
			captured.states.put(module.id(), module.isEnabled());
		}
		return captured;
	}

	/** Puts every captured module back; a module registered since is left as it is. */
	public void restore() {
		for (Module module : ModuleManager.modules()) {
			Boolean enabled = states.get(module.id());
			if (enabled != null && module.isEnabled() != enabled) {
				module.setEnabled(enabled);
			}
		}
	}
}
