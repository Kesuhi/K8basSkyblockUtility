package com.k8bas.skyblockutility.module

object ModuleManager {
	private val MODULES = ArrayList<Module>()

	@JvmStatic
	fun register(module: Module) {
		MODULES.add(module)
		module.onRegister()
	}

	@JvmStatic
	fun modules(): List<Module> = java.util.List.copyOf(MODULES)
}
