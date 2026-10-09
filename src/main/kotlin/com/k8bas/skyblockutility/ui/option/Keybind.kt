package com.k8bas.skyblockutility.ui.option

import java.util.function.Supplier

/**
 * A key the player can bind (REQ-UI-14). The game stores it in options.txt under its key mapping's
 * name; every keybind of this mod is unbound by default (REQ-UI-10).
 */
data class Keybind(
	private val id: String,
	@get:JvmName("keyMappingName") val keyMappingName: String,
	private val text: OptionText,
	@get:JvmName("statusSource") val statusSource: Supplier<OptionStatus>,
) : Option {
	override fun id(): String = id

	override fun storageKey(): String = "options.txt:$keyMappingName"

	override fun text(): OptionText = text

	override fun amber(): Boolean = false

	override fun defaultValue(): Any = UNBOUND

	override fun status(): OptionStatus = statusSource.get()

	override fun toString(): String = "Keybind[id=$id, keyMappingName=$keyMappingName, text=$text, statusSource=$statusSource]"

	companion object {
		/** The game's name for "no key". */
		const val UNBOUND: String = "key.keyboard.unknown"

		@JvmStatic
		fun of(id: String, keyMappingName: String, text: OptionText): Keybind = Keybind(id, keyMappingName, text, Supplier { OptionStatus.OK })
	}
}
