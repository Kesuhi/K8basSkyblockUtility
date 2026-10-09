package com.k8bas.skyblockutility.ui.widget

import com.k8bas.skyblockutility.ui.option.Keybind

/**
 * Which other key mappings share a binding (EC-UI-11), by vanilla's rule: the same key, not unbound,
 * not the mapping itself, and not both sitting on their own default keys.
 */
object KeyConflicts {
	/**
	 * A mapping's name, its bound key and its default key, as options.txt names them. Nullable as the Java
	 * record's were: [of] fails on a missing name or key only where it reads one, as before.
	 */
	@JvmRecord
	data class KeyState(val mapping: String?, val bound: String?, val defaultKey: String?) {
		override fun toString(): String = "KeyState[mapping=$mapping, bound=$bound, defaultKey=$defaultKey]"
	}

	@JvmStatic
	fun of(self: KeyState, all: List<KeyState>): List<String> {
		val clashes = ArrayList<String>()
		val bound = self.bound!!
		if (bound == Keybind.UNBOUND) {
			return clashes
		}
		for (other in all) {
			val mapping = other.mapping!!
			if (mapping == self.mapping || other.bound!! != bound) {
				continue
			}
			val bothOnDefaults = bound == self.defaultKey && other.bound == other.defaultKey
			if (!bothOnDefaults) {
				clashes.add(mapping)
			}
		}
		return clashes
	}
}
