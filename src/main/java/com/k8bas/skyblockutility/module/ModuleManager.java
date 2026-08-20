package com.k8bas.skyblockutility.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {
	}

	public static void register(Module module) {
		MODULES.add(module);
		module.onRegister();
	}

	public static List<Module> modules() {
		return List.copyOf(MODULES);
	}

	public static Optional<Module> get(String id) {
		return MODULES.stream().filter(m -> m.id().equals(id)).findFirst();
	}
}
